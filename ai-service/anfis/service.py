import os
from typing import Dict, List

import torch

from .fracture_model import INPUT_ORDER, build_fracture_anfis

_WEIGHTS_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "weights", "fracture_anfis.pt")


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
        "very_low":  "Rất thấp",
        "low":       "Thấp",
        "moderate":  "Trung bình",
        "high":      "Cao",
        "very_high": "Rất cao",
    }
    return labels.get(level, level)


def _rounded_inputs(values: Dict[str, float]) -> Dict[str, float]:
    rounded = {}
    for key, value in values.items():
        try:
            rounded[key] = round(float(value), 4)
        except (TypeError, ValueError):
            rounded[key] = 0.0
    return rounded


# ── Mô tả vùng tổn thương theo số lượng ──────────────────────────────────────

def _region_desc(n: int) -> str:
    if n == 1:
        return "một vùng"
    if n == 2:
        return "hai vùng"
    if n <= 4:
        return f"{n} vùng"
    return f"{n} vùng tương đối lớn"


# ── Clinical findings (bằng chứng lâm sàng từ ảnh) ───────────────────────────

def _build_evidence(inputs: Dict[str, float]) -> List[str]:
    """Trả về danh sách nhận xét dưới góc độ trợ lý AI hỗ trợ bác sĩ."""
    region_count = int(round(inputs["region_count"]))
    conf = inputs["max_confidence"]
    ei = inputs["edge_irregularity"]
    ms = inputs["morphology_strength"]
    bc = inputs["bone_contrast"]
    sa = inputs["source_agreement"]

    findings = []

    if bc >= 0.6:
        findings.append("Chất lượng ảnh X-quang tốt, độ tương phản xương rõ nét.")
    elif bc >= 0.3:
        findings.append("Chất lượng ảnh X-quang ở mức đạt yêu cầu chẩn đoán.")
    else:
        findings.append("Chất lượng ảnh X-quang thấp (mờ/nhiễu), cần chú ý kỹ các tổn thương vi thể.")

    if region_count == 0:
        findings.append("Chưa phát hiện vùng bất thường hoặc hình thái gãy xương rõ rệt.")
    else:
        findings.append(f"Phát hiện {_region_desc(region_count)} bất thường trên màng xương (độ tin cậy {round(conf * 100)}%).")

    if ei >= 0.65:
        findings.append("Bờ xương có dấu hiệu gián đoạn/bất thường cao, gợi ý gãy/nứt xương.")
    elif ei >= 0.35:
        findings.append("Bờ xương có sự thay đổi nhẹ, theo dõi rạn xương vi thể.")
    elif region_count > 0:
        findings.append("Ghi nhận biến đổi nhỏ ở vỏ xương nhưng chưa đủ đặc hiệu.")

    if ms >= 0.65 and region_count > 0:
        findings.append("Hình thái tổn thương kéo dài, đường nét rõ, phù hợp với gãy xương cấu trúc.")
    elif ms >= 0.35 and region_count > 0:
        findings.append("Hình thái dạng rạn nứt hoặc gãy không hoàn toàn.")

    if sa >= 0.95 and region_count > 0:
        findings.append("Tính đồng thuận cao giữa các thuật toán phân tích, củng cố độ tin cậy của phát hiện.")

    return findings


# ── Impression lâm sàng (kết luận chẩn đoán hình ảnh) ────────────────────────

def _build_impression(score: float, level: str, inputs: Dict[str, float]) -> str:
    """Tạo kết luận chẩn đoán dưới góc độ trợ lý AI hỗ trợ bác sĩ."""
    region_count = int(round(inputs["region_count"]))
    conf_pct     = round(inputs["max_confidence"] * 100)
    ei           = inputs["edge_irregularity"]
    ms           = inputs["morphology_strength"]

    if region_count == 0 or level == "very_low":
        return "Thưa bác sĩ, hệ thống không ghi nhận dấu hiệu gãy xương rõ rệt trên phim chụp này. Cấu trúc xương trông khá bình thường. Tuy nhiên, tôi đề xuất bác sĩ kết hợp thêm khám lâm sàng để loại trừ hoàn toàn các vết rạn xương vi thể (nếu có)."

    if level == "very_high":
        fracture_char = "Đường gãy rất rõ nét và kéo dài." if ei >= 0.65 and ms >= 0.65 else "Có dấu hiệu tách rời vỏ xương khá rõ." if ei >= 0.65 else "Tổn thương xương lan rộng."
        return f"Thưa bác sĩ, hệ thống phát hiện {_region_desc(region_count)} có dấu hiệu gãy xương (độ tin cậy {conf_pct}%). {fracture_char} Dựa trên phân tích, tôi đánh giá nguy cơ gãy xương ở ca này là RẤT CAO."

    if level == "high":
        char = "Hình thái gợi ý gãy một phần hoặc nứt xương." if ms >= 0.5 else "Bờ xương mất liên tục, nghi ngờ nứt hoặc gãy ngang."
        return f"Thưa bác sĩ, hệ thống ghi nhận {_region_desc(region_count)} bất thường (độ tin cậy {conf_pct}%). {char} Tôi đánh giá nguy cơ tổn thương xương là CAO."

    if level == "moderate":
        detail = "Viền xương thay đổi cục bộ, nghi ngờ nứt nhẹ." if ei >= 0.4 else "Thay đổi cấu trúc nhẹ, cần theo dõi rạn xương hoặc biến thể giải phẫu."
        return f"Thưa bác sĩ, hệ thống ghi nhận {_region_desc(region_count)} thay đổi nhẹ (độ tin cậy {conf_pct}%). {detail} Nguy cơ tổn thương xương hiện ở mức TRUNG BÌNH. Đề nghị bác sĩ đối chiếu thêm với các triệu chứng lâm sàng của bệnh nhân."

    return f"Thưa bác sĩ, hệ thống có phát hiện {_region_desc(region_count)} bất thường nhẹ (độ tin cậy {conf_pct}%). Tuy nhiên, hiện chưa đủ bằng chứng để chẩn đoán gãy xương; đây có thể chỉ là tổn thương phần mềm hoặc hình thái tự nhiên. Nguy cơ tổn thương xương được đánh giá ở mức THẤP."


# ── Khuyến nghị lâm sàng ─────────────────────────────────────────────────────

def _build_recommendations(level: str, inputs: Dict[str, float]) -> List[str]:
    """Khuyến nghị lâm sàng từ hệ thống AI."""
    bc = inputs["bone_contrast"]
    ms = inputs["morphology_strength"]
    recommendations = []

    if level == "very_high":
        recommendations.extend([
            "Đề xuất bác sĩ chỉ định bất động ngay lập tức vùng tổn thương.",
            "Tôi đề xuất chụp X-quang thêm các góc (nghiêng, chếch) hoặc CT-scan để bác sĩ đánh giá chi tiết mức độ di lệch.",
            "Cân nhắc chuẩn bị hội chẩn chuyên khoa Chấn thương Chỉnh hình để can thiệp (bó bột/phẫu thuật)."
        ])
    elif level == "high":
        recommendations.extend([
            "Đề xuất bác sĩ chỉ định nẹp cố định tạm thời vùng nghi ngờ.",
            "Nên yêu cầu chụp thêm phim ở các góc khác để làm rõ tổn thương.",
            "Đề xuất theo dõi sát các triệu chứng chèn ép khoang hoặc tổn thương thần kinh mạch máu."
        ])
    elif level == "moderate":
        recommendations.extend([
            "Đề xuất khai thác kỹ tiền sử chấn thương và đối chiếu điểm đau khu trú của bệnh nhân.",
            "Nên chỉ định hạn chế vận động mạnh; hẹn chụp lại sau vài ngày nếu triệu chứng không thuyên giảm.",
            "Bác sĩ có thể cân nhắc siêu âm kiểm tra thêm tổn thương mô mềm hoặc dây chằng."
        ])
    else:
        recommendations.extend([
            "Đề xuất tiếp tục theo dõi lâm sàng; điều trị giảm đau mô mềm nếu cần.",
            "Bác sĩ có thể dặn dò bệnh nhân tái khám nếu xuất hiện sưng đau tăng dần."
        ])

    if bc < 0.25:
        recommendations.append("Chất lượng ảnh hạn chế. Đề nghị chụp lại phim với thông số tia X chuẩn hơn nếu lâm sàng nghi ngờ cao.")

    if ms >= 0.65 and level in {"high", "very_high"}:
        recommendations.append("Hình thái tổn thương có nguy cơ mất vững cao, cần thận trọng tối đa khi di chuyển bệnh nhân.")

    return recommendations


# ── Main service class ────────────────────────────────────────────────────────

class AnfisDiagnosisService:
    """ANFIS-based fracture risk diagnostic support service.

    Scoring is done by a trained ANFIS network (anfis/fracture_model.py),
    loaded once here instead of being rebuilt on every request. The network
    was distilled from the original hand-written fuzzy rule system
    (anfis/model.py) — see train_anfis.py — since no labelled clinical
    dataset exists yet to train on directly. If the trained weights file is
    missing for any reason, this falls back to running the original rule
    system live so the service still degrades gracefully instead of
    failing outright.
    """

    def __init__(self):
        self._anfis_model = self._load_anfis_model()
        self._build_simulation = None
        if self._anfis_model is None:
            from .model import build_diagnosis_control_system
            self._build_simulation = build_diagnosis_control_system

    @staticmethod
    def _load_anfis_model():
        if not os.path.exists(_WEIGHTS_PATH):
            print(f"[AnfisDiagnosisService] No trained weights at {_WEIGHTS_PATH}; "
                  "falling back to the rule-based fuzzy system. Run train_anfis.py to generate them.")
            return None
        try:
            model = build_fracture_anfis(hybrid=True)
            model.load_state_dict(torch.load(_WEIGHTS_PATH, map_location="cpu"))
            model.eval()
            return model
        except Exception as exc:  # defensive: never let a bad weights file take the service down
            print(f"[AnfisDiagnosisService] Failed to load trained ANFIS weights ({exc}); "
                  "falling back to the rule-based fuzzy system.")
            return None

    def _score_with_anfis(self, inputs: Dict[str, float]) -> float:
        x = torch.tensor([[inputs[name] for name in INPUT_ORDER]], dtype=torch.float)
        with torch.no_grad():
            y = self._anfis_model(x)
        return float(y.item())

    def _score_with_rule_system(self, inputs: Dict[str, float]) -> float:
        simulation = self._build_simulation()
        for name in INPUT_ORDER:
            simulation.input[name] = inputs[name]
        simulation.compute()
        return float(simulation.output.get("fracture_score", 0.0))

    def predict(self, features: Dict[str, float]) -> Dict[str, object]:
        inputs = {
            "edge_irregularity":  _clamp(features.get("edge_irregularity", 0.0)),
            "bone_contrast":      _clamp(features.get("bone_contrast", 0.0)),
            "yolo_confidence":    _clamp(features.get("yolo_confidence", 0.0)),
            "region_burden":      _clamp(features.get("region_burden", 0.0)),
            "morphology_strength": _clamp(features.get("morphology_strength", 0.0)),
            "source_agreement":   _clamp(features.get("source_agreement", 0.0)),
            "max_confidence":     _clamp(features.get("max_confidence", 0.0)),
            "max_fractal_dim":    float(features.get("max_fractal_dim", 0.0) or 0.0),
            "region_count":       max(0.0, float(features.get("region_count", 0.0) or 0.0)),
        }

        if self._anfis_model is not None:
            raw_score = self._score_with_anfis(inputs)
        else:
            raw_score = self._score_with_rule_system(inputs)

        if inputs["region_count"] == 0:
            raw_score = min(raw_score, 12.0)
        elif inputs["source_agreement"] >= 0.95 and inputs["max_confidence"] >= 0.55:
            raw_score += 4.0
        elif inputs["source_agreement"] < 0.6 and inputs["max_confidence"] < 0.45:
            raw_score -= 5.0

        score  = round(max(0.0, min(100.0, raw_score)), 2)
        level  = _risk_level(score)

        clinical_confidence = round(
            (
                (0.35 * inputs["max_confidence"])
                + (0.25 * inputs["morphology_strength"])
                + (0.2  * inputs["source_agreement"])
                + (0.2  * max(inputs["bone_contrast"], 0.25))
            ) * 100,
            2,
        )

        impression      = _build_impression(score, level, inputs)
        evidence        = _build_evidence(inputs)
        recommendations = _build_recommendations(level, inputs)

        limitations = [
            "Hệ thống AI đóng vai trò tham khảo; chẩn đoán xác định thuộc thẩm quyền của bác sĩ chuyên khoa.",
            "Kết quả phân tích hình ảnh cần được đối chiếu kết hợp với bệnh sử và triệu chứng lâm sàng của bệnh nhân.",
        ]

        # summary: văn bản ngắn gọn phù hợp hiển thị trong giao diện
        summary = (
            f"[Nguy cơ: {_level_vi(level).upper()}] "
            f"{impression}"
        )

        return {
            "fracture_score":      score,
            "risk_level":          level,
            "clinical_confidence": clinical_confidence,
            "impression":          impression,
            "summary":             summary,
            "evidence":            evidence,
            "recommendations":     recommendations,
            "limitations":         limitations,
            "inputs":              _rounded_inputs(inputs),
        }
