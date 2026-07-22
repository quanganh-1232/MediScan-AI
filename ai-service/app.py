from fastapi import FastAPI, UploadFile, File, Header, HTTPException, Depends
import os
import tempfile
import base64
from pydantic import BaseModel, Field
from typing import Dict, List, Optional

# Import the YOLO / Fractal logic
import yolo_core
from anfis.features import build_diagnosis_features
from anfis.service import AnfisDiagnosisService

app = FastAPI(title="MediScan AI Service")

anfis_service = AnfisDiagnosisService()

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
YOLO_MODEL_PATH = os.path.join(BASE_DIR, "YOLO", "FDetectModel_YOLO11_080626.pt")

# Shared secret the Spring Boot app must send so this service isn't reachable
# by anyone who can hit the host directly, bypassing all app-level auth.
API_KEY = os.environ.get("AI_SERVICE_API_KEY", "dev-ai-key-change-me")

ALLOWED_EXTENSIONS = {".png", ".jpg", ".jpeg", ".bmp", ".tif", ".tiff", ".dcm", ".dicom"}
MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024  # 20 MB


class BoundingBox(BaseModel):
    x: int
    y: int
    w: int
    h: int

class FractureRegionInfo(BaseModel):
    source: str
    fractal_dim: float
    confidence: float
    bbox: BoundingBox

class PredictionSummary(BaseModel):
    fracture_score: float
    risk_level: str
    clinical_confidence: Optional[float] = None
    impression: Optional[str] = None
    summary: str
    evidence: List[str] = Field(default_factory=list)
    recommendations: List[str] = Field(default_factory=list)
    limitations: List[str] = Field(default_factory=list)
    inputs: Dict[str, float]

class PredictResponse(BaseModel):
    status: str
    fractures: List[FractureRegionInfo]
    diagnosis: PredictionSummary
    annotated_image_base64: Optional[str] = None
    fracture_detected: bool
    highest_confidence: float


def verify_api_key(x_internal_api_key: Optional[str] = Header(default=None)):
    if x_internal_api_key != API_KEY:
        raise HTTPException(status_code=401, detail="Thiếu hoặc sai khoá xác thực nội bộ.")


# Plain `def` (not `async def`): FastAPI runs sync path functions in a
# worker threadpool automatically, so one slow/CPU-heavy analysis (OpenCV +
# YOLO + ANFIS) doesn't block the event loop and serialize every other
# concurrent upload behind it.
@app.post("/predict", response_model=PredictResponse)
def predict_fracture(file: UploadFile = File(...), _auth: None = Depends(verify_api_key)):
    ext = os.path.splitext(file.filename or "")[1].lower()
    if not ext:
        ext = ".png"
    if ext not in ALLOWED_EXTENSIONS:
        raise HTTPException(status_code=400, detail=f"Định dạng file không được hỗ trợ: {ext}")

    # 1. Save uploaded file to a temporary file, enforcing a size cap while
    # streaming so an oversized upload can't sit fully in memory/disk first.
    temp_input_path = None
    temp_output_path = None
    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=ext) as temp_in:
            temp_input_path = temp_in.name
            total_bytes = 0
            while True:
                chunk = file.file.read(1024 * 1024)
                if not chunk:
                    break
                total_bytes += len(chunk)
                if total_bytes > MAX_FILE_SIZE_BYTES:
                    raise HTTPException(status_code=413, detail="File ảnh vượt quá 20MB.")
                temp_in.write(chunk)

        temp_output_path = temp_input_path + "_output.png"

        # 2. Run detection
        try:
            yolo_path = YOLO_MODEL_PATH if os.path.exists(YOLO_MODEL_PATH) else None

            regions = yolo_core.detect_fractures(
                input_path=temp_input_path,
                output_path=temp_output_path,
                yolo_model_path=yolo_path,
                yolo_conf_thresh=0.25,
                verbose=False
            )

            # 3. Build ANFIS diagnosis support
            img, _ = yolo_core.load_image(temp_input_path)
            enhanced = yolo_core.preprocess(img)
            diagnosis_inputs = build_diagnosis_features(img, enhanced, regions)
            diagnosis = anfis_service.predict(diagnosis_inputs)
        except HTTPException:
            raise
        except Exception as exc:
            raise HTTPException(status_code=400, detail=f"Không thể đọc hoặc phân tích ảnh: {exc}")

        # 4. Read output image as base64
        encoded_image = None
        if os.path.exists(temp_output_path):
            with open(temp_output_path, "rb") as image_file:
                encoded_image = base64.b64encode(image_file.read()).decode('utf-8')

        # 5. Format response
        fractures = []
        max_conf = 0.0
        for r in regions:
            x, y, w, h = r.bbox
            fractures.append(FractureRegionInfo(
                source=r.source,
                fractal_dim=r.fractal_dim,
                confidence=r.confidence,
                bbox=BoundingBox(x=x, y=y, w=w, h=h)
            ))
            if r.confidence > max_conf:
                max_conf = r.confidence

        return PredictResponse(
            status="success",
            fractures=fractures,
            diagnosis=PredictionSummary(**diagnosis),
            annotated_image_base64=encoded_image,
            fracture_detected=len(fractures) > 0,
            highest_confidence=max_conf
        )

    finally:
        # Cleanup temporary files
        if temp_input_path and os.path.exists(temp_input_path):
            os.remove(temp_input_path)
        if temp_output_path and os.path.exists(temp_output_path):
            os.remove(temp_output_path)

# Run instructions:
# uvicorn app:app --host 127.0.0.1 --port 8000
