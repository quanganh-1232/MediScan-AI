"""
Defines the ANFIS network architecture used for fracture-risk scoring.

This module is imported by both the offline training script
(train_anfis.py) and the runtime inference service (service.py), so the
two always agree on input order, variable count and membership-function
layout. Only the *state_dict* (learned parameters) differs between a
freshly-initialised model and a trained one loaded from
anfis/weights/fracture_anfis.pt.
"""

from typing import List, Tuple

from .anfis import AnfisNet
from .membership import make_gauss_mfs

# Order matters: this must match the order features are fed into the
# model's input tensor, both when generating training data and at
# inference time in AnfisDiagnosisService.
INPUT_ORDER: List[str] = [
    "edge_irregularity",
    "bone_contrast",
    "yolo_confidence",
    "region_burden",
    "morphology_strength",
]

OUTPUT_NAME = "fracture_score"

# Initial membership-function centers per variable (low / medium / high),
# seeded to mirror the thresholds used by the original hand-written fuzzy
# rules (anfis/model.py) so training starts close to the expert system's
# behaviour instead of from scratch. All centers/widths remain trainable
# parameters and are refined during distillation.
_VAR_CENTERS: List[Tuple[str, List[float]]] = [
    ("edge_irregularity", [0.15, 0.5, 0.85]),
    ("bone_contrast", [0.15, 0.5, 0.85]),
    ("yolo_confidence", [0.15, 0.5, 0.85]),
    ("region_burden", [0.05, 0.3, 0.75]),
    ("morphology_strength", [0.15, 0.5, 0.85]),
]
_INIT_SIGMA = 0.22


def build_fracture_anfis(hybrid: bool = True) -> AnfisNet:
    """Construct a fresh (untrained, unless a state_dict is loaded onto it)
    ANFIS network for fracture-risk scoring: 5 inputs x 3 Gaussian MFs
    each (243 rules), 1 output (fracture_score, 0-100)."""
    invardefs = [
        (name, make_gauss_mfs(_INIT_SIGMA, centers))
        for name, centers in _VAR_CENTERS
    ]
    return AnfisNet("FractureRiskANFIS", invardefs, [OUTPUT_NAME], hybrid=hybrid)
