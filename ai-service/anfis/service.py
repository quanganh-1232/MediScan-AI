from typing import Dict, List

from .model import build_diagnosis_control_system


def _clamp(value: float, minimum: float = 0.0, maximum: float = 1.0) -> float:
    try:
        value = float(value)
    except (TypeError, ValueError):
        return minimum
    return max(minimum, min(maximum, value))


def _risk_level(score: float) -> str:
    if score < 20:
        return "very_low"
    if score < 40:
        return "low"
    if score < 60:
        return "moderate"
    if score < 80:
        return "high"
    return "very_high"


def _level_vi(level: str) -> str:
    labels = {
        "very_low": "rất thấp",
        "low": "thấp",
        "moderate": "trung bình",
        "high": "cao",
        "very_high": "rất cao",
    }
    return labels.get(level, level)


def _percent(value: float) -> str:
    return f"{round(_clamp(value) * 100):.0f}%"


def _rounded_inputs(values: Dict[str, float]) -> Dict[str, float]:
    rounded = {}
    for key, value in values.items():
        try:
            rounded[key] = round(float(value), 4)
        except (TypeError, ValueError):
            rounded[key] = 0.0
    return rounded


def _build_evidence(inputs: Dict[str, float]) -> List[str]:
    region_count = int(round(inputs["region_count"]))
    evidence = [
        f"Số vùng nghi ngờ: {region_count}.",
        f"Độ tin cậy phát hiện cao nhất: {_percent(inputs['max_confidence'])}.",
        f"Mức bất thường bờ xương/fractal: {_percent(inputs['edge_irregularity'])}; FD cao nhất: {inputs['max_fractal_dim']:.3f}.",
        f"Độ mạnh hình thái đường gãy: {_percent(inputs['morphology_strength'])}.",
        f"Gánh nặng vùng tổn thương theo diện tích/số lượng: {_percent(inputs['region_burden'])}.",
        f"Độ tương phản ảnh sau tiền xử lý: {_percent(inputs['bone_contrast'])}.",
    ]

    if inputs["source_agreement"] >= 0.95:
        evidence.append("YOLO và phân tích fractal cùng ghi nhận vùng bất thường.")
    elif inputs["source_agreement"] >= 0.65:
        evidence.append("Vùng bất thường chủ yếu đến từ YOLO; cần đối chiếu lại trên ảnh gốc.")
    elif region_count > 0:
        evidence.append("Vùng bất thường chủ yếu đến từ phân tích hình thái/fractal.")
    else:
        evidence.append("Không có vùng nghi ngờ đủ điều kiện sau các bước lọc hiện tại.")

    return evidence


def _build_impression(score: float, level: str, inputs: Dict[str, float]) -> str:
    region_count = int(round(inputs["region_count"]))
    if region_count == 0:
        return (
            "Chưa ghi nhận dấu hiệu gãy xương rõ trên ảnh đã phân tích. "
            "Kết quả này không loại trừ hoàn toàn gãy kín, nứt nhỏ hoặc tổn thương bị che khuất."
        )
    if level in {"very_high", "high"}:
        return (
            "Rất nghi ngờ gãy xương tại một hoặc nhiều vùng được khoanh. "
            "Mẫu hình có sự kết hợp giữa độ tin cậy phát hiện, bất thường bờ xương và hình thái đường nứt."
        )
    if level == "moderate":
        return (
            "Có dấu hiệu nghi ngờ gãy xương mức trung bình. "
            "Nên đối chiếu triệu chứng đau khu trú, sưng, hạn chế vận động và cân nhắc chụp thêm tư thế."
        )
    return (
        "Có vùng bất thường nhẹ nhưng bằng chứng hiện chưa đủ mạnh để khẳng định gãy xương. "
        "Nên theo dõi lâm sàng và đọc phim bởi bác sĩ chẩn đoán hình ảnh."
    )


def _build_recommendations(level: str, inputs: Dict[str, float]) -> List[str]:
    recommendations = [
        "Bác sĩ chẩn đoán hình ảnh hoặc bác sĩ chấn thương chỉnh hình cần xác nhận kết quả trên phim gốc.",
    ]

    if level in {"very_high", "high"}:
        recommendations.extend([
            "Ưu tiên bất động vùng nghi tổn thương và đánh giá đau, biến dạng, mạch, cảm giác ngoại vi.",
            "Cân nhắc chụp thêm tư thế vuông góc hoặc CT nếu đường gãy phức tạp, trong khớp hoặc khó quan sát.",
        ])
    elif level == "moderate":
        recommendations.extend([
            "So sánh với vị trí đau khu trú và cân nhắc chụp lại/đổi tư thế nếu triệu chứng không phù hợp ảnh.",
            "Nếu nghi gãy kín hoặc gãy mảnh nhỏ, nên hẹn đọc lại phim hoặc dùng phương tiện hình ảnh bổ sung.",
        ])
    else:
        recommendations.append(
            "Nếu bệnh nhân vẫn đau nhiều, sưng hoặc giảm chức năng, không nên chỉ dựa vào AI để loại trừ gãy xương."
        )

    if inputs["bone_contrast"] < 0.25:
        recommendations.append("Chất lượng tương phản ảnh thấp; nên kiểm tra lại kỹ thuật chụp hoặc ảnh đầu vào.")

    return recommendations


class AnfisDiagnosisService:
    """A scikit-fuzzy-based diagnostic support service."""

    def __init__(self):
        self._build_simulation = build_diagnosis_control_system

    def predict(self, features: Dict[str, float]) -> Dict[str, object]:
        inputs = {
            "edge_irregularity": _clamp(features.get("edge_irregularity", 0.0)),
            "bone_contrast": _clamp(features.get("bone_contrast", 0.0)),
            "yolo_confidence": _clamp(features.get("yolo_confidence", 0.0)),
            "region_burden": _clamp(features.get("region_burden", 0.0)),
            "morphology_strength": _clamp(features.get("morphology_strength", 0.0)),
            "source_agreement": _clamp(features.get("source_agreement", 0.0)),
            "max_confidence": _clamp(features.get("max_confidence", 0.0)),
            "max_fractal_dim": float(features.get("max_fractal_dim", 0.0) or 0.0),
            "region_count": max(0.0, float(features.get("region_count", 0.0) or 0.0)),
        }

        simulation = self._build_simulation()
        simulation.input["edge_irregularity"] = inputs["edge_irregularity"]
        simulation.input["bone_contrast"] = inputs["bone_contrast"]
        simulation.input["yolo_confidence"] = inputs["yolo_confidence"]
        simulation.input["region_burden"] = inputs["region_burden"]
        simulation.input["morphology_strength"] = inputs["morphology_strength"]
        simulation.compute()

        raw_score = float(simulation.output.get("fracture_score", 0.0))
        if inputs["region_count"] == 0:
            raw_score = min(raw_score, 12.0)
        elif inputs["source_agreement"] >= 0.95 and inputs["max_confidence"] >= 0.55:
            raw_score += 4.0
        elif inputs["source_agreement"] < 0.6 and inputs["max_confidence"] < 0.45:
            raw_score -= 5.0

        score = round(max(0.0, min(100.0, raw_score)), 2)
        level = _risk_level(score)
        clinical_confidence = round(
            (
                (0.35 * inputs["max_confidence"])
                + (0.25 * inputs["morphology_strength"])
                + (0.2 * inputs["source_agreement"])
                + (0.2 * max(inputs["bone_contrast"], 0.25))
            )
            * 100,
            2,
        )
        impression = _build_impression(score, level, inputs)
        evidence = _build_evidence(inputs)
        recommendations = _build_recommendations(level, inputs)
        limitations = [
            "Đây là hệ thống hỗ trợ đọc ảnh, không thay thế kết luận của bác sĩ.",
            "AI không đánh giá được đầy đủ cơ chế chấn thương, khám lâm sàng, đau khu trú hoặc bệnh sử.",
        ]
        summary = (
            f"ANFIS đánh giá nguy cơ gãy xương mức {_level_vi(level)} "
            f"({score}/100, độ tin cậy báo cáo {clinical_confidence}/100). {impression} "
            f"Bằng chứng chính: {' '.join(evidence[:4])}"
        )

        return {
            "fracture_score": score,
            "risk_level": level,
            "clinical_confidence": clinical_confidence,
            "impression": impression,
            "summary": summary,
            "evidence": evidence,
            "recommendations": recommendations,
            "limitations": limitations,
            "inputs": _rounded_inputs(inputs),
        }
