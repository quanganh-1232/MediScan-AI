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
        return "một vùng khu trú"
    if n == 2:
        return "hai vùng riêng biệt"
    if n <= 4:
        return f"{n} vùng rải rác"
    return f"{n} vùng diện rộng"


# ── Clinical findings (bằng chứng lâm sàng từ ảnh) ───────────────────────────

def _build_evidence(inputs: Dict[str, float]) -> List[str]:
    """Trả về danh sách nhận xét lâm sàng từ ảnh, ngôn ngữ bác sĩ X-quang."""
    region_count = int(round(inputs["region_count"]))
    conf = inputs["max_confidence"]
    ei = inputs["edge_irregularity"]
    ms = inputs["morphology_strength"]
    bc = inputs["bone_contrast"]
    sa = inputs["source_agreement"]

    findings = []

    # --- Nhận xét mật độ xương ---
    if bc >= 0.6:
        findings.append("Tương phản vỏ xương tốt, cho phép đánh giá đường gãy khá rõ.")
    elif bc >= 0.3:
        findings.append("Tương phản ảnh ở mức chấp nhận được.")
    else:
        findings.append("Chất lượng tương phản ảnh hạn chế — cần thận trọng khi loại trừ tổn thương nhỏ.")

    # --- Nhận xét vùng tổn thương ---
    if region_count == 0:
        findings.append("Không xác định được vùng gián đoạn mật độ xương trên ảnh phân tích.")
    else:
        findings.append(
            f"Ghi nhận {_region_desc(region_count)} có bất thường mật độ xương "
            f"(độ tin cậy phát hiện {round(conf * 100)}%)."
        )

    # --- Nhận xét bờ vỏ xương ---
    if ei >= 0.65:
        findings.append("Bờ vỏ xương không đều rõ ràng, hình thái phù hợp đường gãy chấn thương.")
    elif ei >= 0.35:
        findings.append("Bờ vỏ xương có biến đổi nhẹ đến vừa, gợi ý tổn thương không hoàn toàn hoặc nứt xương.")
    elif region_count > 0:
        findings.append("Bờ vỏ xương biến đổi nhẹ, chưa đặc hiệu.")

    # --- Nhận xét hình thái đường gãy ---
    if ms >= 0.65 and region_count > 0:
        findings.append(
            "Hình thái đường gãy có tính chất kéo dài, gợi ý gãy xiên hoặc gãy xoắn — "
            "cần xét thêm tư thế vuông góc."
        )
    elif ms >= 0.35 and region_count > 0:
        findings.append("Hình thái tổn thương ở mức trung bình, có thể là gãy ngang hoặc nứt không hoàn toàn.")

    # --- Tính nhất quán giữa hai nguồn phát hiện ---
    if sa >= 0.95 and region_count > 0:
        findings.append("Cả YOLO và phân tích hình thái cùng ghi nhận vùng bất thường — độ nhất quán cao.")
    elif sa < 0.65 and region_count > 0:
        findings.append("Vùng bất thường chỉ được ghi nhận bởi một phương pháp — cần đối chiếu thêm.")

    return findings


# ── Impression lâm sàng (kết luận chẩn đoán hình ảnh) ────────────────────────

def _build_impression(score: float, level: str, inputs: Dict[str, float]) -> str:
    """Tạo kết luận theo chuẩn báo cáo chẩn đoán hình ảnh của bác sĩ X-quang."""
    region_count = int(round(inputs["region_count"]))
    conf_pct     = round(inputs["max_confidence"] * 100)
    ei           = inputs["edge_irregularity"]
    ms           = inputs["morphology_strength"]
    fd           = inputs["max_fractal_dim"]

    # ── Không phát hiện tổn thương ──
    if region_count == 0 or level == "very_low":
        return (
            "Không ghi nhận dấu hiệu gãy xương hay gián đoạn mật độ vỏ xương trên ảnh phân tích. "
            "Cấu trúc xương và đường khớp trong giới hạn bình thường theo phân tích tự động. "
            "Lưu ý: kết quả âm tính không loại trừ hoàn toàn gãy kín, nứt tóc hoặc tổn thương "
            "tại vùng chồng chéo giải phẫu — cần tương quan lâm sàng."
        )

    # ── Nguy cơ rất cao ──
    if level == "very_high":
        fracture_char = ""
        if ei >= 0.65 and ms >= 0.65:
            fracture_char = (
                "Đường gãy có đặc điểm không đều, hình thái kéo dài, "
                "vỏ xương mất liên tục rõ — gợi ý gãy xiên hoặc gãy phức tạp."
            )
        elif ei >= 0.65:
            fracture_char = (
                "Gián đoạn bờ vỏ xương rõ ràng, mật độ thay đổi khu trú "
                "— hình ảnh phù hợp gãy hoàn toàn."
            )
        else:
            fracture_char = (
                "Tổn thương diện rộng với gián đoạn mật độ xương nhiều vùng "
                "— gợi ý gãy phức tạp hoặc đa tổn thương."
            )
        return (
            f"NHẬN XÉT: Phát hiện {_region_desc(region_count)} gián đoạn mật độ xương "
            f"với bờ vỏ không đều rõ (độ tin cậy {conf_pct}%, FD {fd:.2f}). "
            f"{fracture_char} "
            f"KẾT LUẬN: Gãy xương — nghi ngờ rất cao. "
            f"Điểm nguy cơ ANFIS: {round(score)}/100."
        )

    # ── Nguy cơ cao ──
    if level == "high":
        if ms >= 0.5:
            char = (
                "Hình thái tổn thương gợi ý gãy không hoàn toàn hoặc nứt xiên, "
                "có thể kèm di lệch nhỏ."
            )
        else:
            char = (
                "Gián đoạn mật độ vỏ xương khu trú, bờ xương không đều vừa — "
                "phù hợp gãy ngang hoặc gãy cành tươi."
            )
        return (
            f"NHẬN XÉT: Ghi nhận {_region_desc(region_count)} bất thường mật độ xương "
            f"với bờ vỏ không đều (độ tin cậy {conf_pct}%). "
            f"{char} "
            f"KẾT LUẬN: Nghi ngờ gãy xương mức độ cao. "
            f"Cần đối chiếu tư thế vuông góc và khám lâm sàng. "
            f"Điểm nguy cơ ANFIS: {round(score)}/100."
        )

    # ── Nguy cơ trung bình ──
    if level == "moderate":
        if ei >= 0.4:
            detail = (
                "Bờ vỏ xương có biến đổi nhẹ đến vừa, hình ảnh gợi ý nứt xương "
                "hoặc gãy không hoàn toàn (incomplete fracture)."
            )
        else:
            detail = (
                "Vùng thay đổi mật độ khu trú, chưa đủ đặc hiệu — "
                "có thể là gãy nhỏ hoặc biến thể giải phẫu."
            )
        return (
            f"NHẬN XÉT: Ghi nhận {_region_desc(region_count)} bất thường nhẹ đến vừa "
            f"(độ tin cậy {conf_pct}%). "
            f"{detail} "
            f"KẾT LUẬN: Nghi ngờ tổn thương xương mức trung bình — "
            f"bằng chứng hình ảnh chưa đủ để khẳng định chắc chắn. "
            f"Cần tương quan chặt chẽ với lâm sàng và cơ chế chấn thương. "
            f"Điểm nguy cơ ANFIS: {round(score)}/100."
        )

    # ── Nguy cơ thấp ──
    return (
        f"NHẬN XÉT: Ghi nhận {_region_desc(region_count)} thay đổi nhẹ mật độ xương "
        f"(độ tin cậy {conf_pct}%), chưa đủ đặc hiệu. "
        "Bờ vỏ xương biến đổi tối thiểu, có thể là biến thể giải phẫu bình thường "
        "hoặc tổn thương phần mềm. "
        f"KẾT LUẬN: Khả năng gãy xương thấp — "
        "không nên loại trừ hoàn toàn nếu đau khu trú nhiều hoặc cơ chế chấn thương rõ. "
        f"Điểm nguy cơ ANFIS: {round(score)}/100."
    )


# ── Khuyến nghị lâm sàng ─────────────────────────────────────────────────────

def _build_recommendations(level: str, inputs: Dict[str, float]) -> List[str]:
    """Khuyến nghị theo chuẩn thực hành lâm sàng chấn thương chỉnh hình."""
    bc = inputs["bone_contrast"]
    ms = inputs["morphology_strength"]
    recommendations = []

    if level == "very_high":
        recommendations.extend([
            "Bất động ngay vùng nghi tổn thương, hạn chế tải trọng.",
            "Chụp bổ sung tư thế vuông góc (AP và lateral) để đánh giá di lệch và phân loại gãy.",
            "Cân nhắc CT scan nếu đường gãy phức tạp, trong khớp hoặc khó xác định trên X-quang.",
            "Đánh giá mạch máu và thần kinh ngoại vi tại vùng tổn thương.",
            "Hội chẩn bác sĩ chấn thương chỉnh hình để xác định phương án điều trị.",
        ])
    elif level == "high":
        recommendations.extend([
            "Cố định tạm thời vùng nghi tổn thương, tránh vận động không cần thiết.",
            "Chụp thêm tư thế vuông góc hoặc tư thế chuyên biệt để xác nhận đường gãy.",
            "Đánh giá lâm sàng: sưng nề, đau khu trú, hạn chế tầm vận động, biến dạng trục.",
            "Cân nhắc MRI nếu nghi gãy kín, gãy mệt hoặc chấn thương phần mềm kèm theo.",
        ])
    elif level == "moderate":
        recommendations.extend([
            "Đối chiếu với vị trí đau khu trú và cơ chế chấn thương của bệnh nhân.",
            "Chụp lại hoặc đổi tư thế nếu triệu chứng lâm sàng không phù hợp ảnh hiện tại.",
            "Nếu nghi gãy cành tươi hoặc gãy không hoàn toàn: nẹp bột tạm thời và tái khám sau 5–7 ngày.",
        ])
    else:
        recommendations.extend([
            "Nếu bệnh nhân còn đau nhiều hoặc sưng, không nên chỉ dựa vào kết quả AI để loại trừ.",
            "Theo dõi lâm sàng và chụp lại nếu triệu chứng tiến triển.",
        ])

    if bc < 0.25:
        recommendations.append(
            "Chất lượng ảnh hạn chế (tương phản thấp) — kiểm tra lại thông số kỹ thuật chụp "
            "hoặc cân nhắc chụp lại để đánh giá chính xác hơn."
        )

    if ms >= 0.65 and level in {"high", "very_high"}:
        recommendations.append(
            "Hình thái gợi ý gãy xiên/xoắn — lưu ý nguy cơ di lệch thứ phát khi vận động."
        )

    recommendations.append(
        "Kết quả AI chỉ mang tính hỗ trợ. Bác sĩ chẩn đoán hình ảnh hoặc bác sĩ chấn thương "
        "cần xác nhận trên phim gốc trước khi kết luận và điều trị."
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
            "Kết quả phân tích AI chỉ mang tính hỗ trợ chẩn đoán, không thay thế kết luận của bác sĩ.",
            "Hệ thống không đánh giá được cơ chế chấn thương, triệu chứng đau khu trú, "
            "bệnh sử hay tình trạng lâm sàng tổng thể của bệnh nhân.",
        ]

        # summary: văn bản ngắn gọn phù hợp hiển thị trong giao diện
        summary = (
            f"[{_level_vi(level).upper()} — {round(score)}/100] "
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
