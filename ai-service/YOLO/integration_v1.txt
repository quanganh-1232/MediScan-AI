"""
Bone Fracture Detection — Multi-Format Support
===============================================
Supports input formats: .dcm (DICOM), .png, .jpg, .jpeg
University Project — AI Bone Fracture Detection

Pipeline:
  1. Load image  (auto-detect format: DICOM or raster)
  2. Preprocess  (CLAHE + bilateral denoising)
  3. Extract fracture candidates  (adaptive Canny + connected components)
  4. Score with Fractal Box-Counting Dimension
  5. Non-Maximum Suppression
  6. Annotate bounding boxes
  7. Export result as PNG

Dependencies:
  pip install pydicom numpy opencv-python scipy Pillow matplotlib
"""

import os
import sys
import argparse
import numpy as np
import cv2
import pydicom
from PIL import Image
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from dataclasses import dataclass
from typing import List, Tuple, Optional

try:
    from ultralytics import YOLO
    _ULTRALYTICS_AVAILABLE = True
except ImportError:
    _ULTRALYTICS_AVAILABLE = False


# ══════════════════════════════════════════════════════════════════════════════
# SECTION 1 — Data Structures
# ══════════════════════════════════════════════════════════════════════════════
# We use a simple dataclass to bundle everything we know about one detected
# fracture region: where it is (bbox), how irregular its edges are (fractal_dim),
# and how confident the algorithm is (confidence).

@dataclass
class FractureRegion:
    """
    Represents a single detected fracture candidate.

    Attributes
    ----------
    bbox         : (x, y, w, h) bounding box in pixel coordinates.
                   x, y = top-left corner; w, h = width and height.
    fractal_dim  : Box-counting fractal dimension of the region's edge mask.
                   Higher = more geometrically irregular = more fracture-like.
    confidence   : Float in [0, 1] derived from fractal_dim.
                   Displayed as a percentage in the output image.
    label        : Human-readable summary string (auto-built in __post_init__).
    """
    bbox:        Tuple[int, int, int, int]
    fractal_dim: float
    confidence:  float
    label:       str = ""
    source:      str = "fractal"   # "fractal" or "yolo"

    def __post_init__(self):
        prefix = "[YOLO] " if self.source == "yolo" else "[Fractal] "
        self.label = f"{prefix}FD={self.fractal_dim:.3f}  conf={self.confidence:.0%}"


# ══════════════════════════════════════════════════════════════════════════════
# SECTION 2 — Image Loading  (DICOM + Raster)
# ══════════════════════════════════════════════════════════════════════════════

# Supported raster extensions.  DICOM is handled separately by extension check.
RASTER_EXTENSIONS = {".png", ".jpg", ".jpeg"}
DICOM_EXTENSIONS  = {".dcm", ".dicom"}


def _extension(path: str) -> str:
    """Return the lowercase file extension including the dot, e.g. '.png'."""
    return os.path.splitext(path)[1].lower()


# ── DICOM loader ──────────────────────────────────────────────────────────────

def _load_dicom(path: str) -> Tuple[np.ndarray, dict]:
    """
    Read a DICOM file with pydicom and return a uint8 grayscale array + metadata.

    Key steps
    ---------
    1. Read raw pixel data as float32 so arithmetic doesn't overflow.
    2. Apply RescaleSlope / RescaleIntercept (converts raw sensor values to
       Hounsfield Units for CT, or exposure values for X-ray).
    3. Apply window/level normalisation so the clinically relevant intensity
       range maps to the full [0, 255] range.  If the DICOM tags are absent
       we fall back to mean±2σ windowing.
    4. Clip and cast to uint8.
    """
    ds     = pydicom.dcmread(path, force=True)
    pixels = ds.pixel_array.astype(np.float32)

    # --- Rescale (Hounsfield / linear sensor units) --------------------------
    slope     = float(getattr(ds, "RescaleSlope",     1))
    intercept = float(getattr(ds, "RescaleIntercept", 0))
    pixels    = pixels * slope + intercept

    # --- Window / Level -------------------------------------------------------
    # DICOM can store multiple window presets as a MultiValue sequence.
    # We always use the first preset; if the tag is absent we auto-compute.
    def _scalar(tag_val, fallback):
        if hasattr(tag_val, "__iter__") and not isinstance(tag_val, str):
            return float(tag_val[0])
        return float(tag_val) if tag_val is not None else fallback

    wc = _scalar(getattr(ds, "WindowCenter", None), float(np.mean(pixels)))
    ww = _scalar(getattr(ds, "WindowWidth",  None), float(np.std(pixels) * 4 + 1))

    lo, hi = wc - ww / 2.0, wc + ww / 2.0
    pixels  = np.clip(pixels, lo, hi)

    # --- Normalise to [0, 255] ------------------------------------------------
    pmin, pmax = pixels.min(), pixels.max()
    if pmax > pmin:
        pixels = (pixels - pmin) / (pmax - pmin) * 255.0
    img_u8 = pixels.astype(np.uint8)

    # --- Metadata dict --------------------------------------------------------
    metadata = {
        "PatientID":        str(getattr(ds, "PatientID",        "N/A")),
        "PatientName":      str(getattr(ds, "PatientName",      "N/A")),
        "StudyDate":        str(getattr(ds, "StudyDate",        "N/A")),
        "Modality":         str(getattr(ds, "Modality",         "DX")),
        "BodyPartExamined": str(getattr(ds, "BodyPartExamined", "N/A")),
        "InstitutionName":  str(getattr(ds, "InstitutionName",  "N/A")),
        "rows":             int(getattr(ds, "Rows",    img_u8.shape[0])),
        "cols":             int(getattr(ds, "Columns", img_u8.shape[1])),
        "source_format":    "DICOM",
    }
    return img_u8, metadata


# ── Raster loader (PNG / JPEG / JPG) ─────────────────────────────────────────

def _load_raster(path: str) -> Tuple[np.ndarray, dict]:
    """
    Load a PNG, JPEG, or JPG file and return a uint8 grayscale array + metadata.

    Key steps
    ---------
    1. Open with Pillow (handles palette, RGBA, 16-bit, etc. transparently).
    2. Convert to grayscale ('L' mode = single 8-bit channel).
       •  RGB / RGBA → weighted luminance conversion (Rec. 601).
       •  Already-grayscale images pass through unchanged.
    3. Convert to numpy uint8.
    4. Build a synthetic metadata dict that mirrors what the DICOM loader
       returns so the rest of the pipeline never needs to know the source.

    Why Pillow instead of cv2.imread?
    ----------------------------------
    Pillow handles a wider variety of sub-formats (16-bit PNG, CMYK JPEG,
    animated GIF first frame, etc.) without silently returning None.
    We convert to OpenCV (numpy) array immediately after loading.
    """
    pil_img = Image.open(path)

    # Convert any mode (RGBA, P/palette, L, RGB, CMYK …) to plain grayscale
    if pil_img.mode != "L":
        pil_img = pil_img.convert("L")   # Pillow uses Rec. 601 weights internally

    img_u8 = np.array(pil_img, dtype=np.uint8)

    h, w = img_u8.shape
    ext  = _extension(path).lstrip(".")

    metadata = {
        "PatientID":        "N/A",
        "PatientName":      "N/A",
        "StudyDate":        "N/A",
        "Modality":         ext.upper(),      # e.g. "PNG", "JPEG"
        "BodyPartExamined": "N/A",
        "InstitutionName":  "N/A",
        "rows":             h,
        "cols":             w,
        "source_format":    ext.upper(),
    }
    return img_u8, metadata


# ── Unified loader ────────────────────────────────────────────────────────────

def load_image(path: str) -> Tuple[np.ndarray, dict]:
    """
    Auto-detect the file format by extension and call the appropriate loader.

    Returns
    -------
    img_u8   : 2-D uint8 numpy array (grayscale), shape (H, W).
    metadata : dict with keys PatientID, Modality, rows, cols, source_format, …

    Raises
    ------
    ValueError  if the extension is not in DICOM_EXTENSIONS ∪ RASTER_EXTENSIONS.
    FileNotFoundError  if the file does not exist.
    """
    if not os.path.isfile(path):
        raise FileNotFoundError(f"Input file not found: {path}")

    ext = _extension(path)

    if ext in DICOM_EXTENSIONS:
        return _load_dicom(path)
    elif ext in RASTER_EXTENSIONS:
        return _load_raster(path)
    else:
        supported = sorted(DICOM_EXTENSIONS | RASTER_EXTENSIONS)
        raise ValueError(
            f"Unsupported file extension '{ext}'. "
            f"Supported: {', '.join(supported)}"
        )


# ══════════════════════════════════════════════════════════════════════════════
# SECTION 3 — Preprocessing
# ══════════════════════════════════════════════════════════════════════════════

def preprocess(img: np.ndarray) -> np.ndarray:
    """
    Enhance contrast and reduce noise while preserving edges.

    Steps
    -----
    1. Ensure the array is 2-D and uint8 (handles edge cases from loaders).
    2. CLAHE  (Contrast Limited Adaptive Histogram Equalization):
       •  Divides the image into small tiles (8×8 by default).
       •  Equalises the histogram within each tile independently.
       •  'clipLimit' prevents over-amplification of noise in uniform regions.
       •  Result: local contrast is boosted even in low-contrast bone regions,
          making subtle fracture lines visible.
    3. Bilateral filter:
       •  Gaussian in space (sigmaSpace) but Gaussian in intensity (sigmaColor).
       •  Smooths uniform areas (reduces sensor noise) while leaving sharp
          intensity discontinuities (edges / fracture boundaries) intact.
       •  Much better than a plain Gaussian blur for medical images.

    Returns
    -------
    denoised : uint8 array, same shape as input.
    """
    # Guard: collapse extra dimensions that some loaders may produce
    if img.ndim == 3 and img.shape[2] == 1:
        img = img.squeeze(axis=2)
    elif img.ndim == 3:
        img = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

    if img.dtype != np.uint8:
        img = np.clip(img, 0, 255).astype(np.uint8)

    # Step 2: CLAHE
    clahe    = cv2.createCLAHE(clipLimit=3.0, tileGridSize=(8, 8))
    enhanced = clahe.apply(img)

    # Step 3: Bilateral filter
    denoised = cv2.bilateralFilter(enhanced, d=9, sigmaColor=75, sigmaSpace=75)
    return denoised


# ══════════════════════════════════════════════════════════════════════════════
# SECTION 4 — Fractal Box-Counting Dimension
# ══════════════════════════════════════════════════════════════════════════════

def box_counting_dimension(binary_img: np.ndarray,
                            min_box: int = 2,
                            max_box: int = 64) -> float:
    """
    Estimate the fractal (Hausdorff) dimension of a binary pattern using the
    box-counting method.

    Theory
    ------
    For a fractal set S, cover it with boxes of side length r.  Let N(r) be
    the number of boxes that contain at least one point of S.  The fractal
    dimension D is defined by:

        N(r) ∝ r^(-D)   ⟹   D = -lim_{r→0}  log N(r) / log r

    In practice we measure N(r) at several discrete box sizes, take logs,
    and fit a line with np.polyfit.  The slope of log N vs log(1/r) is D.

    Why does this detect fractures?
    --------------------------------
    •  Healthy cortical bone edge:  smooth, regular curve → D ≈ 1.0 – 1.5
    •  Fracture line:               jagged, branching, self-similar crack
                                    → D ≈ 1.55 – 1.9
    The fracture's self-similar irregularity at multiple scales is the
    physical reason its fractal dimension is elevated.

    Parameters
    ----------
    binary_img : 2-D uint8 array with foreground pixels = 1 (or > 0).
    min_box    : Smallest box side length (pixels).  Smaller = finer detail.
    max_box    : Largest box side length (pixels).   Larger = coarser structure.

    Returns
    -------
    D : float  — fractal dimension, typically in [1.0, 2.0] for 2-D images.
                 Returns 0.0 if the image is empty or has too few data points.
    """
    if binary_img.sum() == 0:
        return 0.0

    sizes, counts = [], []
    size = max_box

    while size >= min_box:
        count = 0
        for i in range(0, binary_img.shape[0], size):
            for j in range(0, binary_img.shape[1], size):
                if binary_img[i:i + size, j:j + size].any():
                    count += 1
        sizes.append(size)
        counts.append(count)
        size //= 2   # halve the box size at each step

    sizes  = np.array(sizes,  dtype=np.float64)
    counts = np.array(counts, dtype=np.float64)

    valid = counts > 0
    if valid.sum() < 2:
        return 0.0

    # Linear fit in log-log space
    log_s = np.log(1.0 / sizes[valid])   # log(1/r) = log of inverse scale
    log_n = np.log(counts[valid])         # log N(r)
    coeffs = np.polyfit(log_s, log_n, 1)
    return float(coeffs[0])              # slope = D


# ══════════════════════════════════════════════════════════════════════════════
# SECTION 5 — Candidate Region Extraction
# ══════════════════════════════════════════════════════════════════════════════

def extract_candidates(enhanced: np.ndarray,
                       min_area: int = 200,
                       max_area_frac: float = 0.25
                       ) -> List[Tuple[np.ndarray, Tuple[int, int, int, int]]]:
    """
    Find connected edge regions that could be fracture lines.

    Algorithm
    ---------
    1. Adaptive Canny edge detection:
       •  Thresholds are derived from the image median so they adapt to
          the brightness/contrast of each individual scan.
       •  sigma=0.33 gives tight thresholds (good for preserving fine cracks).

    2. Morphological dilation:
       •  A fracture crack produces thin, sometimes broken edge segments.
       •  Dilating with a 5×5 elliptical kernel bridges small gaps so the
          whole fracture line becomes one connected component.

    3. Connected component analysis (8-connectivity):
       •  Labels every connected island of edge pixels.
       •  Returns bounding box (x, y, w, h) and area for each island.

    4. Area filter:
       •  Discard tiny blobs (noise) below min_area pixels.
       •  Discard huge blobs above max_area_frac of the whole image
          (those are bone boundaries, not fractures).

    Returns
    -------
    List of (roi_binary_mask, (x, y, w, h)) for each surviving candidate.
    roi_binary_mask is a 2-D uint8 array cropped to the bounding box.
    """
    h, w    = enhanced.shape
    max_area = int(h * w * max_area_frac)

    # Adaptive Canny thresholds
    med   = np.median(enhanced)
    sigma = 0.33
    lo    = int(max(0,   (1.0 - sigma) * med))
    hi    = int(min(255, (1.0 + sigma) * med))
    edges = cv2.Canny(enhanced, lo, hi)

    # Bridge gaps in the fracture line
    kernel  = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5))
    dilated = cv2.dilate(edges, kernel, iterations=2)

    # Label connected components
    n_labels, labels, stats, _ = cv2.connectedComponentsWithStats(dilated, connectivity=8)

    candidates = []
    for lid in range(1, n_labels):   # label 0 = background
        area = stats[lid, cv2.CC_STAT_AREA]
        if not (min_area <= area <= max_area):
            continue

        x  = stats[lid, cv2.CC_STAT_LEFT]
        y  = stats[lid, cv2.CC_STAT_TOP]
        bw = stats[lid, cv2.CC_STAT_WIDTH]
        bh = stats[lid, cv2.CC_STAT_HEIGHT]

        roi_mask = (labels[y:y + bh, x:x + bw] == lid).astype(np.uint8)
        candidates.append((roi_mask, (x, y, bw, bh)))

    return candidates


# ══════════════════════════════════════════════════════════════════════════════
# SECTION 6 — Fracture Scoring
# ══════════════════════════════════════════════════════════════════════════════

# ── Tuning parameters — adjust these if you're getting too many / too few hits ──
FD_FRACTURE_THRESHOLD = 1.7   # minimum fractal dimension to be called a fracture
FD_UPPER_BOUND        = 2.0    # physical maximum for a 2-D binary image
ASPECT_RATIO_MIN      = 1.2    # fractures are elongated, not square
ASPECT_RATIO_MAX      = 15.0   # very thin slivers are likely noise


def score_region(roi_mask: np.ndarray,
                 bbox: Tuple[int, int, int, int]) -> Optional[FractureRegion]:
    """
    Decide whether a candidate region is a fracture and return its score.

    Two-gate filter
    ---------------
    Gate 1 — Shape heuristic:
        Fracture lines are geometrically elongated.  We compute the aspect
        ratio (long side / short side) of the bounding box.  Near-square
        blobs fail this test and are discarded.

    Gate 2 — Fractal dimension:
        We call box_counting_dimension() on the binary ROI mask.
        Only regions with FD ≥ FD_FRACTURE_THRESHOLD pass.

    Confidence mapping:
        We linearly interpolate FD from [threshold, 2.0] → [0%, 100%].
        This gives a simple, interpretable confidence score for the report.

    Returns
    -------
    FractureRegion if both gates pass, None otherwise.
    """
    _, _, bw, bh = bbox
    if bw == 0 or bh == 0:
        return None

    # Gate 1: aspect ratio
    aspect = max(bw, bh) / max(min(bw, bh), 1)
    if not (ASPECT_RATIO_MIN <= aspect <= ASPECT_RATIO_MAX):
        return None

    # Gate 2: fractal dimension
    fd = box_counting_dimension(roi_mask)
    if not (FD_FRACTURE_THRESHOLD <= fd <= FD_UPPER_BOUND):
        return None

    # Confidence: linear interpolation in [threshold, 2.0]
    conf = (fd - FD_FRACTURE_THRESHOLD) / (FD_UPPER_BOUND - FD_FRACTURE_THRESHOLD)
    conf = float(np.clip(conf, 0.0, 1.0))

    return FractureRegion(bbox=bbox, fractal_dim=fd, confidence=conf, source="fractal")


# ══════════════════════════════════════════════════════════════════════════════
# SECTION 6b — YOLOv11 Detection
# ══════════════════════════════════════════════════════════════════════════════

def run_yolo_detection(img_u8: np.ndarray,
                       model_path: str,
                       conf_thresh: float = 0.25,
                       compute_fd: bool = True) -> List[FractureRegion]:
    """
    Run a YOLOv8 model on the image and convert its detections into
    FractureRegion objects so they share the same data structure as the
    fractal-based detections.

    Parameters
    ----------
    img_u8      : 2-D uint8 grayscale image (output of load_image()).
    model_path  : Path to your trained YOLOv8 weights, e.g. "best.pt".
    conf_thresh : Minimum YOLO confidence to keep a detection.
    compute_fd  : If True, also compute the fractal dimension of each YOLO
                  box's edge content. This is purely for the report panel —
                  it does NOT affect whether the box is kept. Set False to
                  skip this (faster, but FD will show as 0.000 in the report).

    Returns
    -------
    List of FractureRegion with source="yolo". confidence = YOLO's own
    objectness/class confidence score (not derived from FD).

    Notes
    -----
    - YOLOv8 expects a 3-channel image, so the grayscale array is stacked
      into BGR before inference.
    - If `ultralytics` is not installed, raises ImportError with an
      install hint.
    - If you have multiple classes (e.g. "hairline", "displaced",
      "comminuted"), the class name is appended to the label.
    """
    if not _ULTRALYTICS_AVAILABLE:
        raise ImportError(
            "ultralytics is not installed. Run: pip install ultralytics"
        )

    model = YOLO(model_path)

    # YOLO expects 3-channel input
    img_bgr = cv2.cvtColor(img_u8, cv2.COLOR_GRAY2BGR)

    results = model.predict(img_bgr, conf=conf_thresh, verbose=False)

    regions: List[FractureRegion] = []
    for r in results:
        if r.boxes is None:
            continue
        for box in r.boxes:
            x1, y1, x2, y2 = box.xyxy[0].cpu().numpy()
            x, y   = int(x1), int(y1)
            bw, bh = int(x2 - x1), int(y2 - y1)
            yolo_conf = float(box.conf[0].cpu().numpy())
            cls_id    = int(box.cls[0].cpu().numpy())
            cls_name  = model.names.get(cls_id, str(cls_id)) if hasattr(model, "names") else str(cls_id)

            fd = 0.0
            if compute_fd and bw > 0 and bh > 0:
                # Recompute edges inside this ROI to estimate FD for the report
                roi = img_u8[y:y+bh, x:x+bw]
                if roi.size > 0:
                    roi_enh   = preprocess(roi)
                    roi_edges = cv2.Canny(roi_enh, 50, 150)
                    fd = box_counting_dimension(roi_edges)

            region = FractureRegion(
                bbox=(x, y, bw, bh),
                fractal_dim=fd,
                confidence=yolo_conf,
                source="yolo",
            )
            region.label = f"[YOLO:{cls_name}] FD={fd:.3f}  conf={yolo_conf:.0%}"
            regions.append(region)

    return regions


# ══════════════════════════════════════════════════════════════════════════════
# SECTION 7 — Non-Maximum Suppression
# ══════════════════════════════════════════════════════════════════════════════

def _iou(a: Tuple[int,int,int,int], b: Tuple[int,int,int,int]) -> float:
    """
    Intersection-over-Union (IoU) between two bounding boxes.

    IoU = |A ∩ B| / |A ∪ B|

    IoU = 0  → boxes do not overlap at all.
    IoU = 1  → boxes are identical.
    We use it to detect duplicate detections of the same fracture.
    """
    ax1, ay1, aw, ah = a;  ax2, ay2 = ax1 + aw, ay1 + ah
    bx1, by1, bw, bh = b;  bx2, by2 = bx1 + bw, by1 + bh
    ix = max(0, min(ax2, bx2) - max(ax1, bx1))
    iy = max(0, min(ay2, by2) - max(ay1, by1))
    inter = ix * iy
    if inter == 0:
        return 0.0
    union = aw * ah + bw * bh - inter
    return inter / union if union > 0 else 0.0


def nms(regions: List[FractureRegion], iou_thresh: float = 0.4) -> List[FractureRegion]:
    """
    Greedy Non-Maximum Suppression.

    Algorithm
    ---------
    1. Sort all detections by confidence (highest first).
    2. Iterate: keep the current box; discard any subsequent box whose
       IoU with any kept box exceeds iou_thresh.

    This eliminates duplicate bounding boxes that fire on the same fracture
    from slightly different edge blobs.  iou_thresh=0.4 is a reasonable
    default: boxes that overlap by more than 40% of their union are
    considered duplicates.
    """
    regions = sorted(regions, key=lambda r: r.confidence, reverse=True)
    kept    = []
    for r in regions:
        if all(_iou(r.bbox, k.bbox) < iou_thresh for k in kept):
            kept.append(r)
    return kept


# ══════════════════════════════════════════════════════════════════════════════
# SECTION 8 — Annotation & PNG Export
# ══════════════════════════════════════════════════════════════════════════════

# BGR colour palette for bounding boxes (OpenCV uses BGR, not RGB)
BOX_COLORS = [
    (0, 0, 255),     # red
    (0, 165, 255),   # orange
    (0, 255, 255),   # yellow
    (255, 0, 0),     # blue
]


def draw_bounding_boxes(img_u8: np.ndarray,
                        regions: List[FractureRegion],
                        metadata: dict) -> np.ndarray:
    """
    Draw colour-coded bounding boxes + confidence labels on the grayscale image.

    Each box has:
    •  A coloured rectangle border (2 px thick).
    •  A filled colour badge above the box containing the fracture index,
       fractal dimension, and confidence percentage.
    •  A semi-transparent metadata overlay in the top-left corner.

    Returns
    -------
    annotated : BGR uint8 array ready for matplotlib or cv2 display.
    """
    # Grayscale → BGR so we can draw coloured overlays
    annotated = cv2.cvtColor(img_u8, cv2.COLOR_GRAY2BGR)

    for idx, reg in enumerate(regions):
        if reg.source == "yolo":
            color = (0, 255, 0)   # green = YOLO
        else:
            color = BOX_COLORS[idx % len(BOX_COLORS)]   # red/orange/yellow/blue = fractal
        x, y, bw, bh = reg.bbox

        # Bounding box
        cv2.rectangle(annotated, (x, y), (x + bw, y + bh), color, 2)

        # Label badge
        tag   = "Y" if reg.source == "yolo" else "F"
        label = f"#{idx+1}[{tag}] FD={reg.fractal_dim:.2f} {reg.confidence:.0%}"
        (tw, th), baseline = cv2.getTextSize(label, cv2.FONT_HERSHEY_SIMPLEX, 0.5, 1)
        cv2.rectangle(annotated, (x, y - th - baseline - 4), (x + tw + 4, y), color, -1)
        cv2.putText(annotated, label, (x + 2, y - baseline - 2),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 1, cv2.LINE_AA)

    # Metadata overlay (top-left)
    for i, line in enumerate([
        f"Patient: {metadata['PatientID']}",
        f"Date:    {metadata['StudyDate']}",
        f"Format:  {metadata['source_format']}",
        f"Modality:{metadata['Modality']}",
        f"Fractures detected: {len(regions)}",
    ]):
        cv2.putText(annotated, line, (10, 20 + i * 18),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.45, (200, 200, 200), 1, cv2.LINE_AA)

    return annotated


def export_png(annotated_bgr: np.ndarray,
               output_path: str,
               regions: List[FractureRegion],
               metadata: dict) -> None:
    """
    Compose a two-panel report image and save it as PNG.

    Left panel  : Annotated X-ray / bone image with bounding boxes.
    Right panel : Findings report listing FD, confidence, and bounding box
                  coordinates for each detected fracture.
    Footer      : Patient / acquisition metadata.

    Uses matplotlib so fonts are anti-aliased and the layout is consistent
    regardless of image size.
    """
    rgb = cv2.cvtColor(annotated_bgr, cv2.COLOR_BGR2RGB)

    fig, axes = plt.subplots(1, 2, figsize=(14, 7),
                             gridspec_kw={"width_ratios": [3, 1]},
                             facecolor="#0d0d0d")

    # ── Left: image ───────────────────────────────────────────────────────────
    axes[0].imshow(rgb)
    axes[0].set_title("Fracture Detection Result", color="white",
                      fontsize=13, fontweight="bold", pad=10)
    axes[0].axis("off")

    # ── Right: findings ───────────────────────────────────────────────────────
    axes[1].set_facecolor("#161616")
    axes[1].axis("off")
    axes[1].text(0.5, 0.97, "── FINDINGS ──", transform=axes[1].transAxes,
                 ha="center", va="top", fontsize=10, color="#aaaaaa",
                 fontfamily="monospace")

    if regions:
        lines = []
        for i, r in enumerate(regions):
            x, y, bw, bh = r.bbox
            lines += [f"\n#{i+1}  Fracture",
                      f"  FD   = {r.fractal_dim:.4f}",
                      f"  Conf = {r.confidence:.1%}",
                      f"  Box  ({x},{y},{bw},{bh})"]
        axes[1].text(0.05, 0.90, "\n".join(lines), transform=axes[1].transAxes,
                     ha="left", va="top", fontsize=8.5, color="#e0e0e0",
                     fontfamily="monospace",
                     bbox=dict(boxstyle="round,pad=0.5", fc="#1e1e2e", ec="#444"))
    else:
        axes[1].text(0.5, 0.5, "No fracture\ndetected",
                     transform=axes[1].transAxes, ha="center", va="center",
                     fontsize=11, color="#66ff99", fontfamily="monospace")

    # Footer
    fig.text(0.5, 0.01,
             f"Patient: {metadata['PatientID']}  |  "
             f"{metadata['Modality']}  |  {metadata['StudyDate']}\n"
             f"Body: {metadata['BodyPartExamined']}  |  "
             f"{metadata['rows']}×{metadata['cols']} px  |  "
             f"Source: {metadata['source_format']}",
             ha="center", va="bottom", fontsize=7.5,
             color="#666666", fontfamily="monospace")

    plt.tight_layout(pad=1.5)
    plt.savefig(output_path, dpi=150, bbox_inches="tight",
                facecolor=fig.get_facecolor())
    plt.close(fig)
    print(f"[✓] Exported → {output_path}")


# ══════════════════════════════════════════════════════════════════════════════
# SECTION 9 — Main Detection Pipeline
# ══════════════════════════════════════════════════════════════════════════════

def detect_fractures(input_path: str,
                     output_path: Optional[str] = None,
                     yolo_model_path: Optional[str] = None,
                     yolo_conf_thresh: float = 0.25,
                     verbose: bool = True) -> List[FractureRegion]:
    """
    End-to-end fracture detection pipeline.

    Accepts .dcm, .png, .jpg, or .jpeg files.

    Parameters
    ----------
    input_path       : Path to the input image (any supported format).
    output_path      : Destination PNG path.  Defaults to <input>_result.png.
    yolo_model_path  : Optional path to a trained YOLOv8 .pt weights file.
                       If provided, YOLO detections are run alongside the
                       fractal pipeline and merged via NMS.
    yolo_conf_thresh : Minimum YOLO confidence to keep a detection.
    verbose          : If True, print step-by-step progress to stdout.

    Returns
    -------
    List of FractureRegion objects (empty list if no fractures found).
    Each region's `.source` is "fractal" or "yolo".
    """
    # ── Step 1: Load ──────────────────────────────────────────────────────────
    if verbose:
        print(f"[1/5] Loading ({_extension(input_path).upper()}): {input_path}")
    img, metadata = load_image(input_path)
    if verbose:
        print(f"      {metadata['rows']}×{metadata['cols']} px  |  "
              f"format={metadata['source_format']}  |  "
              f"modality={metadata['Modality']}")

    # ── Step 2: Preprocess ────────────────────────────────────────────────────
    if verbose:
        print("[2/5] Preprocessing (CLAHE + bilateral filter)…")
    enhanced = preprocess(img)

    # ── Step 3: Extract candidates ────────────────────────────────────────────
    if verbose:
        print("[3/5] Extracting edge-based candidate regions…")
    candidates = extract_candidates(enhanced)
    if verbose:
        print(f"      {len(candidates)} candidate(s) found.")

    # ── Step 4: Score with fractal dimension ──────────────────────────────────
    if verbose:
        print("[4/5] Computing fractal dimension per candidate…")
    regions: List[FractureRegion] = []
    for roi_mask, bbox in candidates:
        result = score_region(roi_mask, bbox)
        if result is not None:
            regions.append(result)
    if verbose:
        print(f"      {len(regions)} candidate(s) passed fractal filter.")

    # ── Step 5: NMS + sort ────────────────────────────────────────────────────
    fractal_regions = nms(regions, iou_thresh=0.4)

    # ── Step 5b: YOLO detection (optional) ────────────────────────────────────
    yolo_regions: List[FractureRegion] = []
    if yolo_model_path is not None:
        if verbose:
            print(f"[5b/5] Running YOLOv8 ({yolo_model_path})…")
        yolo_regions = run_yolo_detection(img, yolo_model_path, conf_thresh=yolo_conf_thresh)
        if verbose:
            print(f"       YOLO found {len(yolo_regions)} detection(s).")

    # ── Step 5c: Merge both detectors via NMS ─────────────────────────────────
    # Combine, then re-run NMS across the merged set. Because nms() sorts by
    # confidence first, and YOLO confidence (objectness) and fractal
    # confidence (FD-derived) are on different scales, overlapping boxes
    # will generally be resolved in favor of whichever score is higher —
    # in practice YOLO boxes (trained, calibrated) tend to win ties against
    # fractal boxes when both detect the same region.
    combined = nms(fractal_regions + yolo_regions, iou_thresh=0.4)
    combined.sort(key=lambda r: r.confidence, reverse=True)
    regions = combined

    if verbose:
        n_fractal = sum(1 for r in regions if r.source == "fractal")
        n_yolo    = sum(1 for r in regions if r.source == "yolo")
        print(f"[5/5] After merge+NMS: {len(regions)} fracture(s) "
              f"({n_fractal} fractal, {n_yolo} YOLO).")
        for i, r in enumerate(regions):
            print(f"      #{i+1} [{r.source}]  FD={r.fractal_dim:.4f}  "
                  f"conf={r.confidence:.1%}  bbox={r.bbox}")

    # ── Annotate & export ─────────────────────────────────────────────────────
    if output_path is None:
        base        = os.path.splitext(input_path)[0]
        output_path = base + "_fracture_detection.png"

    annotated = draw_bounding_boxes(img, regions, metadata)
    export_png(annotated, output_path, regions, metadata)

    if not regions:
        print("[✓] No fractures detected in this image.")

    return regions


# ══════════════════════════════════════════════════════════════════════════════
# SECTION 10 — CLI Entry Point
# ══════════════════════════════════════════════════════════════════════════════

def _build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        description="Bone Fracture Detection — DICOM / PNG / JPEG",
        formatter_class=argparse.RawTextHelpFormatter,
        epilog="""
Supported input formats:
  .dcm / .dicom   — DICOM medical imaging
  .png            — PNG raster image
  .jpg / .jpeg    — JPEG raster image

Examples:
  python bone_fracture_detector_v2.py scan.dcm
  python bone_fracture_detector_v2.py xray.png -o output/result.png
  python bone_fracture_detector_v2.py photo.jpg --threshold 1.6 --quiet
        """,
    )
    p.add_argument("input_path",
                   help="Path to input image (.dcm, .png, .jpg, .jpeg)")
    p.add_argument("-o", "--output", default=None,
                   help="Output PNG path  (default: <input>_fracture_detection.png)")
    p.add_argument("--threshold", type=float, default=FD_FRACTURE_THRESHOLD,
                   help=f"Fractal dimension threshold  (default: {FD_FRACTURE_THRESHOLD})")
    p.add_argument("--min-area", type=int, default=200,
                   help="Minimum candidate region area in pixels  (default: 200)")
    p.add_argument("--iou-thresh", type=float, default=0.4,
                   help="IoU threshold for NMS  (default: 0.4)")
    p.add_argument("--yolo-model", default=None,
                   help="Path to trained YOLOv8 .pt weights. If given, "
                        "YOLO detections are merged with fractal detections.")
    p.add_argument("--yolo-conf", type=float, default=0.25,
                   help="Minimum YOLO confidence threshold  (default: 0.25)")
    p.add_argument("--quiet", action="store_true",
                   help="Suppress progress output")
    return p


def _filter_colab_args(raw: list) -> list:
    """
    Google Colab / Jupyter kernels inject '-f /path/to/kernel.json' into
    sys.argv.  This helper strips those spurious arguments so argparse
    doesn't choke on them.
    """
    filtered, skip = [], False
    for arg in raw:
        if skip:
            skip = False
            continue
        if arg == "-f":
            skip = True
            continue
        filtered.append(arg)
    return filtered


def main():
    parser = _build_parser()
    args   = parser.parse_args(_filter_colab_args(sys.argv[1:]))

    global FD_FRACTURE_THRESHOLD
    FD_FRACTURE_THRESHOLD = args.threshold

    if not os.path.isfile(args.input_path):
        print(f"[ERROR] File not found: {args.input_path}", file=sys.stderr)
        sys.exit(1)

    detect_fractures(
        input_path       = args.input_path,
        output_path      = args.output,
        yolo_model_path  = args.yolo_model,
        yolo_conf_thresh = args.yolo_conf,
        verbose          = not args.quiet,
    )


if __name__ == "__main__":
    main()