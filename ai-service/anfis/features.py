import numpy as np
from typing import List, Dict

import yolo_core


def _normalize_fractal_dim(fd: float, min_fd: float = 1.0, max_fd: float = 2.0) -> float:
    return float(np.clip((fd - min_fd) / (max_fd - min_fd), 0.0, 1.0))


def _safe_bbox(region: yolo_core.FractureRegion) -> tuple:
    try:
        x, y, w, h = region.bbox
    except (TypeError, ValueError):
        return 0, 0, 0, 0
    return int(x), int(y), max(int(w), 0), max(int(h), 0)


def compute_bone_contrast(img: np.ndarray) -> float:
    if img is None or img.size == 0:
        return 0.0
    std = float(np.std(img))
    return float(np.clip((std - 20.0) / 80.0, 0.0, 1.0))


def compute_yolo_confidence(regions: List[yolo_core.FractureRegion]) -> float:
    yolo_scores = [r.confidence for r in regions if r.source == "yolo"]
    if not yolo_scores:
        return 0.0
    return float(np.clip(max(yolo_scores), 0.0, 1.0))


def compute_edge_irregularity(regions: List[yolo_core.FractureRegion]) -> float:
    if not regions:
        return 0.0
    weighted_scores = []
    for region in regions:
        weight = 0.6 + (0.4 * float(np.clip(region.confidence, 0.0, 1.0)))
        weighted_scores.append(_normalize_fractal_dim(region.fractal_dim) * weight)
    return float(np.clip(max(weighted_scores), 0.0, 1.0))


def compute_region_burden(img: np.ndarray, regions: List[yolo_core.FractureRegion]) -> float:
    if img is None or img.size == 0 or not regions:
        return 0.0

    image_area = float(img.shape[0] * img.shape[1])
    suspicious_area = 0.0
    for region in regions:
        _, _, w, h = _safe_bbox(region)
        suspicious_area += w * h

    area_ratio = suspicious_area / max(image_area, 1.0)
    count_factor = min(len(regions) / 4.0, 1.0)
    area_factor = np.clip(area_ratio / 0.12, 0.0, 1.0)
    return float(np.clip((0.55 * area_factor) + (0.45 * count_factor), 0.0, 1.0))


def compute_morphology_strength(img: np.ndarray, regions: List[yolo_core.FractureRegion]) -> float:
    if img is None or img.size == 0 or not regions:
        return 0.0

    image_area = float(img.shape[0] * img.shape[1])
    scores = []
    for region in regions:
        _, _, w, h = _safe_bbox(region)
        if w == 0 or h == 0:
            continue

        aspect_ratio = max(w, h) / max(min(w, h), 1)
        elongatedness = np.clip((aspect_ratio - 1.2) / 4.8, 0.0, 1.0)
        area_ratio = np.clip(((w * h) / max(image_area, 1.0)) / 0.08, 0.0, 1.0)
        fractal_score = _normalize_fractal_dim(region.fractal_dim, 1.45, 2.0)
        confidence = float(np.clip(region.confidence, 0.0, 1.0))

        scores.append(
            (0.4 * fractal_score)
            + (0.25 * elongatedness)
            + (0.2 * confidence)
            + (0.15 * area_ratio)
        )

    if not scores:
        return 0.0
    return float(np.clip(max(scores), 0.0, 1.0))


def compute_source_agreement(regions: List[yolo_core.FractureRegion]) -> float:
    if not regions:
        return 0.0
    sources = {r.source for r in regions}
    if "yolo" in sources and "fractal" in sources:
        return 1.0
    if "yolo" in sources:
        return 0.7
    return 0.55


def compute_max_confidence(regions: List[yolo_core.FractureRegion]) -> float:
    if not regions:
        return 0.0
    return float(np.clip(max(r.confidence for r in regions), 0.0, 1.0))


def compute_max_fractal_dim(regions: List[yolo_core.FractureRegion]) -> float:
    if not regions:
        return 0.0
    return float(max(r.fractal_dim for r in regions))


def build_diagnosis_features(img: np.ndarray,
                             enhanced: np.ndarray,
                             regions: List[yolo_core.FractureRegion]) -> Dict[str, float]:
    region_count = len(regions)
    return {
        "bone_contrast": compute_bone_contrast(enhanced),
        "edge_irregularity": compute_edge_irregularity(regions),
        "yolo_confidence": compute_yolo_confidence(regions),
        "region_burden": compute_region_burden(img, regions),
        "morphology_strength": compute_morphology_strength(img, regions),
        "source_agreement": compute_source_agreement(regions),
        "max_confidence": compute_max_confidence(regions),
        "max_fractal_dim": compute_max_fractal_dim(regions),
        "region_count": float(region_count),
    }
