import numpy as np
import skfuzzy as fuzz
from skfuzzy import control as ctrl


def build_diagnosis_control_system() -> ctrl.ControlSystemSimulation:
    """Build a fuzzy inference system for fracture risk support."""
    edge_irregularity = ctrl.Antecedent(np.arange(0, 1.01, 0.01), "edge_irregularity")
    bone_contrast = ctrl.Antecedent(np.arange(0, 1.01, 0.01), "bone_contrast")
    yolo_confidence = ctrl.Antecedent(np.arange(0, 1.01, 0.01), "yolo_confidence")
    region_burden = ctrl.Antecedent(np.arange(0, 1.01, 0.01), "region_burden")
    morphology_strength = ctrl.Antecedent(np.arange(0, 1.01, 0.01), "morphology_strength")
    fracture_score = ctrl.Consequent(np.arange(0, 101, 1), "fracture_score")

    edge_irregularity["low"] = fuzz.trimf(edge_irregularity.universe, [0.0, 0.0, 0.4])
    edge_irregularity["medium"] = fuzz.trimf(edge_irregularity.universe, [0.25, 0.5, 0.75])
    edge_irregularity["high"] = fuzz.trimf(edge_irregularity.universe, [0.6, 1.0, 1.0])

    bone_contrast["low"] = fuzz.trimf(bone_contrast.universe, [0.0, 0.0, 0.4])
    bone_contrast["medium"] = fuzz.trimf(bone_contrast.universe, [0.25, 0.5, 0.75])
    bone_contrast["high"] = fuzz.trimf(bone_contrast.universe, [0.6, 1.0, 1.0])

    yolo_confidence["low"] = fuzz.trimf(yolo_confidence.universe, [0.0, 0.0, 0.4])
    yolo_confidence["medium"] = fuzz.trimf(yolo_confidence.universe, [0.25, 0.5, 0.75])
    yolo_confidence["high"] = fuzz.trimf(yolo_confidence.universe, [0.6, 1.0, 1.0])

    region_burden["none"] = fuzz.trimf(region_burden.universe, [0.0, 0.0, 0.08])
    region_burden["limited"] = fuzz.trimf(region_burden.universe, [0.03, 0.25, 0.5])
    region_burden["extensive"] = fuzz.trimf(region_burden.universe, [0.4, 1.0, 1.0])

    morphology_strength["weak"] = fuzz.trimf(morphology_strength.universe, [0.0, 0.0, 0.4])
    morphology_strength["suggestive"] = fuzz.trimf(morphology_strength.universe, [0.25, 0.5, 0.75])
    morphology_strength["strong"] = fuzz.trimf(morphology_strength.universe, [0.6, 1.0, 1.0])

    fracture_score["very_low"] = fuzz.trimf(fracture_score.universe, [0, 0, 25])
    fracture_score["low"] = fuzz.trimf(fracture_score.universe, [0, 25, 50])
    fracture_score["moderate"] = fuzz.trimf(fracture_score.universe, [25, 50, 75])
    fracture_score["high"] = fuzz.trimf(fracture_score.universe, [50, 75, 100])
    fracture_score["very_high"] = fuzz.trimf(fracture_score.universe, [75, 100, 100])

    rules = [
        ctrl.Rule(region_burden["none"] & yolo_confidence["low"] & morphology_strength["weak"], fracture_score["very_low"]),
        ctrl.Rule(edge_irregularity["low"] & yolo_confidence["low"] & morphology_strength["weak"], fracture_score["very_low"]),

        ctrl.Rule(edge_irregularity["high"] & yolo_confidence["high"] & morphology_strength["strong"], fracture_score["very_high"]),
        ctrl.Rule(edge_irregularity["high"] & morphology_strength["strong"] & region_burden["extensive"], fracture_score["very_high"]),
        ctrl.Rule(yolo_confidence["high"] & morphology_strength["strong"] & region_burden["limited"], fracture_score["high"]),

        ctrl.Rule(edge_irregularity["high"] & yolo_confidence["medium"], fracture_score["high"]),
        ctrl.Rule(edge_irregularity["medium"] & yolo_confidence["high"], fracture_score["high"]),
        ctrl.Rule(morphology_strength["strong"] & yolo_confidence["medium"], fracture_score["high"]),
        ctrl.Rule(edge_irregularity["high"] & morphology_strength["strong"], fracture_score["high"]),
        ctrl.Rule(morphology_strength["strong"] & region_burden["limited"], fracture_score["high"]),
        ctrl.Rule(bone_contrast["high"] & edge_irregularity["high"] & morphology_strength["suggestive"], fracture_score["high"]),

        ctrl.Rule(edge_irregularity["medium"] & yolo_confidence["medium"], fracture_score["moderate"]),
        ctrl.Rule(edge_irregularity["low"] & yolo_confidence["high"], fracture_score["moderate"]),
        ctrl.Rule(edge_irregularity["high"] & yolo_confidence["low"], fracture_score["moderate"]),
        ctrl.Rule(morphology_strength["suggestive"] & region_burden["limited"], fracture_score["moderate"]),
        ctrl.Rule(morphology_strength["suggestive"] & yolo_confidence["low"], fracture_score["moderate"]),
        ctrl.Rule(bone_contrast["low"] & yolo_confidence["medium"], fracture_score["moderate"]),

        ctrl.Rule(edge_irregularity["low"] & yolo_confidence["medium"] & morphology_strength["weak"], fracture_score["low"]),
        ctrl.Rule(edge_irregularity["medium"] & yolo_confidence["low"] & morphology_strength["weak"], fracture_score["low"]),
        ctrl.Rule(region_burden["limited"] & yolo_confidence["low"] & edge_irregularity["low"], fracture_score["low"]),
        ctrl.Rule(bone_contrast["low"] & yolo_confidence["low"], fracture_score["very_low"]),
    ]

    system = ctrl.ControlSystem(rules)
    return ctrl.ControlSystemSimulation(system)
