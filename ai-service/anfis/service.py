from typing import Dict, List


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
    """Trả về danh sách nhận xét từ ảnh với giọng điệu thân thiện, dễ hiểu."""
    region_count = int(round(inputs["region_count"]))
    conf = inputs["max_confidence"]
    ei = inputs["edge_irregularity"]
    ms = inputs["morphology_strength"]
    bc = inputs["bone_contrast"]
    sa = inputs["source_agreement"]

    findings = []

    # --- Nhận xét mật độ xương ---
    if bc >= 0.6:
        findings.append("Hình ảnh X-quang của bạn chụp khá rõ nét, giúp bác sĩ dễ dàng quan sát các chi tiết xương.")
    elif bc >= 0.3:
        findings.append("Hình ảnh X-quang nhìn tương đối ổn để bác sĩ có thể chẩn đoán.")
    else:
        findings.append("Hình ảnh X-quang hơi mờ một chút, nên bác sĩ sẽ cần quan sát thật kỹ để không bỏ sót tổn thương nhỏ nào.")

    # --- Nhận xét vùng tổn thương ---
    if region_count == 0:
        findings.append("Bác sĩ không thấy có dấu hiệu xương bị gãy hay nứt trên ảnh của bạn.")
    else:
        findings.append(
            f"Bác sĩ chú ý thấy có {_region_desc(region_count)} trên xương trông hơi khác thường "
            f"(độ tin cậy của hệ thống là {round(conf * 100)}%)."
        )

    # --- Nhận xét bờ vỏ xương ---
    if ei >= 0.65:
        findings.append("Ở phần mép xương có chỗ không được nhẵn nhụi, đây là dấu hiệu rất giống với việc xương bị nứt hoặc gãy do va chạm.")
    elif ei >= 0.35:
        findings.append("Phần mép xương có một chút thay đổi nhẹ, có thể là do xương bị nứt hoặc rạn nhẹ.")
    elif region_count > 0:
        findings.append("Bác sĩ thấy có chút thay đổi nhỏ ở viền xương, nhưng chưa thực sự rõ ràng.")

    # --- Nhận xét hình thái đường gãy ---
    if ms >= 0.65 and region_count > 0:
        findings.append(
            "Vết tổn thương có vẻ khá dài, giống như một đường gãy chéo. Bác sĩ có thể sẽ cần xem thêm ảnh chụp ở góc khác để chắc chắn hơn."
        )
    elif ms >= 0.35 and region_count > 0:
        findings.append("Vết tổn thương trông giống như một vết nứt hoặc gãy ngang chưa hoàn toàn.")

    # --- Tính nhất quán giữa hai nguồn phát hiện ---
    if sa >= 0.95 and region_count > 0:
        findings.append("Cả hai phương pháp phân tích của hệ thống đều chỉ ra cùng một vị trí này, nên khả năng rất cao là có vấn đề ở đây.")

    return findings


# ── Impression lâm sàng (kết luận chẩn đoán hình ảnh) ────────────────────────

def _build_impression(score: float, level: str, inputs: Dict[str, float]) -> str:
    """Tạo kết luận với giọng điệu thân thiện, dễ hiểu cho bệnh nhân."""
    region_count = int(round(inputs["region_count"]))
    conf_pct     = round(inputs["max_confidence"] * 100)
    ei           = inputs["edge_irregularity"]
    ms           = inputs["morphology_strength"]

    # ── Không phát hiện tổn thương ──
    if region_count == 0 or level == "very_low":
        return (
            "Chào bạn, qua kết quả phân tích ảnh, hiện tại bác sĩ không thấy có vết nứt hay gãy xương nào rõ rệt. "
            "Cấu trúc xương của bạn trông khá bình thường. Tuy nhiên, X-quang đôi khi có thể không hiện rõ những vết rạn quá nhỏ. "
            "Nếu bạn vẫn cảm thấy đau nhiều ở chỗ va chạm, hãy báo lại để bác sĩ kiểm tra thêm hoặc dùng phương pháp chụp khác nhé!"
        )

    # ── Nguy cơ rất cao ──
    if level == "very_high":
        fracture_char = ""
        if ei >= 0.65 and ms >= 0.65:
            fracture_char = "Vết gãy trông khá rõ và có vẻ kéo dài."
        elif ei >= 0.65:
            fracture_char = "Chỗ xương này dường như đã bị gãy tách ra."
        else:
            fracture_char = "Có vẻ như xương bị tổn thương ở diện khá rộng."
            
        return (
            f"Chào bạn, qua quan sát ảnh chụp, bác sĩ thấy có {_region_desc(region_count)} "
            f"xương bị tổn thương khá rõ (hệ thống tự tin {conf_pct}%). {fracture_char} "
            "Kết luận của bác sĩ là khả năng bạn bị gãy xương ở vị trí này là RẤT CAO."
        )

    # ── Nguy cơ cao ──
    if level == "high":
        if ms >= 0.5:
            char = "Hình ảnh này rất giống với việc xương bị nứt hoặc gãy một phần."
        else:
            char = "Chỗ viền xương trông không được liền mạch, có khả năng là một vết nứt hoặc gãy ngang."
            
        return (
            f"Chào bạn, bác sĩ phát hiện thấy {_region_desc(region_count)} trên xương có dấu hiệu bất thường "
            f"(hệ thống tự tin {conf_pct}%). {char} "
            "Kết luận là nguy cơ gãy hoặc rạn xương của bạn ở mức CAO. Bác sĩ có thể sẽ cần bạn chụp thêm một tấm ảnh ở góc nghiêng "
            "để nhìn cho thật chắc chắn nhé."
        )

    # ── Nguy cơ trung bình ──
    if level == "moderate":
        if ei >= 0.4:
            detail = "Viền xương ở đây có một chút thay đổi, có thể bạn bị nứt nhẹ xương."
        else:
            detail = "Vùng xương này trông hơi khác một chút, có thể là rạn xương nhỏ hoặc chỉ là do cấu trúc xương bẩm sinh của bạn."
            
        return (
            f"Chào bạn, bác sĩ thấy có {_region_desc(region_count)} thay đổi nhẹ trên ảnh "
            f"(hệ thống tự tin {conf_pct}%). {detail} "
            "Nhìn chung, khả năng tổn thương xương ở mức TRUNG BÌNH. Ảnh chụp chưa đủ rõ ràng để bác sĩ khẳng định ngay. "
            "Bác sĩ sẽ cần hỏi thêm xem bạn đau ở đâu và bị ngã/va chạm như thế nào để đánh giá chính xác hơn."
        )

    # ── Nguy cơ thấp ──
    return (
        f"Chào bạn, bác sĩ chỉ ghi nhận {_region_desc(region_count)} có chút thay đổi rất nhẹ trên bề mặt xương "
        f"(hệ thống tự tin {conf_pct}%). Những dấu hiệu này chưa đủ để cho thấy là xương bị gãy, có thể "
        "chỉ là tổn thương phần mềm (như cơ, gân) hoặc hình dáng xương tự nhiên của bạn thôi. "
        "Tuy nhiên, khả năng gãy xương dù THẤP nhưng nếu bạn đang thấy rất đau, hãy nhớ nói với bác sĩ nhé."
    )


# ── Khuyến nghị lâm sàng ─────────────────────────────────────────────────────

def _build_recommendations(level: str, inputs: Dict[str, float]) -> List[str]:
    """Lời khuyên mang tính dặn dò bệnh nhân."""
    bc = inputs["bone_contrast"]
    ms = inputs["morphology_strength"]
    recommendations = []

    if level == "very_high":
        recommendations.extend([
            "Bạn hãy giữ cố định vùng bị đau ngay lập tức nhé, tuyệt đối tránh cử động hay đi lại để không làm xương lệch thêm.",
            "Bác sĩ sẽ cần chụp thêm một kiểu ảnh khác (như góc nghiêng) để xem kỹ hơn tình trạng bên trong.",
            "Bạn chờ một chút để bác sĩ chuyên khoa xương khớp hội chẩn và đưa ra phương án bó bột hay phẫu thuật phù hợp nhất cho bạn."
        ])
    elif level == "high":
        recommendations.extend([
            "Bạn nên nẹp cố định tạm thời chỗ đau và hạn chế cử động tối đa nhé.",
            "Bác sĩ có thể sẽ yêu cầu chụp thêm một tấm ảnh ở góc khác để xác nhận chính xác vết nứt.",
            "Nếu thấy chỗ đau sưng to hoặc tê bì, bạn phải báo ngay cho y tá hoặc bác sĩ."
        ])
    elif level == "moderate":
        recommendations.extend([
            "Bạn hãy chỉ cho bác sĩ biết chính xác chỗ nào đau nhất nhé, điều đó rất quan trọng.",
            "Nếu vài ngày nữa vẫn còn đau hoặc sưng, bạn nhớ quay lại để bác sĩ kiểm tra và chụp lại ảnh.",
            "Tạm thời bạn nên hạn chế vận động mạnh ở vùng này để tránh làm tổn thương nặng thêm."
        ])
    else:
        recommendations.extend([
            "Tạm thời bạn cứ yên tâm nghỉ ngơi nhé. Nhưng nếu về nhà mà thấy chỗ đó sưng tấy hoặc đau tăng lên thì phải quay lại bệnh viện khám ngay.",
            "Bạn có thể chườm lạnh nhẹ nhàng để giảm đau phần mềm."
        ])

    if bc < 0.25:
        recommendations.append(
            "À, tấm ảnh X-quang vừa rồi hơi mờ một chút. Có thể lát nữa bác sĩ sẽ nhờ bạn chụp lại một tấm rõ hơn nhé."
        )

    if ms >= 0.65 and level in {"high", "very_high"}:
        recommendations.append(
            "Lưu ý nhỏ: vết tổn thương này khá dễ bị xê dịch nếu bạn cử động, nên hãy nằm/ngồi thật yên nhé."
        )

    return recommendations


# ── Main service class ────────────────────────────────────────────────────────

class AnfisDiagnosisService:
    """ANFIS-based fracture risk diagnostic support service."""

    def __init__(self):
        from .model import build_diagnosis_control_system
        self._build_simulation = build_diagnosis_control_system

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

        simulation = self._build_simulation()
        simulation.input["edge_irregularity"]  = inputs["edge_irregularity"]
        simulation.input["bone_contrast"]      = inputs["bone_contrast"]
        simulation.input["yolo_confidence"]    = inputs["yolo_confidence"]
        simulation.input["region_burden"]      = inputs["region_burden"]
        simulation.input["morphology_strength"] = inputs["morphology_strength"]
        simulation.compute()

        raw_score = float(simulation.output.get("fracture_score", 0.0))
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
            "Lưu ý: Hệ thống AI chỉ là công cụ hỗ trợ để bác sĩ tham khảo thêm. Bác sĩ của bạn sẽ luôn là người đưa ra quyết định cuối cùng.",
            "AI chỉ phân tích được dựa trên hình ảnh. Những lời bạn kể về cơn đau, cảm giác tê mỏi hay lúc bạn bị ngã thế nào mới là thông tin quan trọng nhất với bác sĩ.",
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
