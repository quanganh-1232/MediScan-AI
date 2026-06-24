from fastapi import FastAPI, UploadFile, File
import shutil
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

@app.post("/predict")
async def predict_fracture(file: UploadFile = File(...)):
    # 1. Save uploaded file to a temporary file
    ext = os.path.splitext(file.filename)[1]
    if not ext:
        ext = ".png" # default
    
    with tempfile.NamedTemporaryFile(delete=False, suffix=ext) as temp_in:
        shutil.copyfileobj(file.file, temp_in)
        temp_input_path = temp_in.name

    temp_output_path = temp_input_path + "_output.png"

    try:
        # 2. Run detection
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

        response = PredictResponse(
            status="success",
            fractures=fractures,
            diagnosis=PredictionSummary(**diagnosis),
            annotated_image_base64=encoded_image,
            fracture_detected=len(fractures) > 0,
            highest_confidence=max_conf
        )

        return response

    finally:
        # Cleanup temporary files
        if os.path.exists(temp_input_path):
            os.remove(temp_input_path)
        if os.path.exists(temp_output_path):
            os.remove(temp_output_path)

# Run instructions:
# uvicorn app:app --host 0.0.0.0 --port 8000
