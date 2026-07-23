package com.example.mediscanauth.service.impl;

import com.example.mediscanauth.model.AiModel;
import com.example.mediscanauth.model.ImagingRecord;
import com.example.mediscanauth.repository.AiModelRepository;
import com.example.mediscanauth.repository.ImagingRecordRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Backs the Admin "AI Diagnosis Monitoring" screen: risk-level distribution,
 * AI confidence trend, doctor override/rejection rate, AI_FAILED case list,
 * and a simple model-version registry (ai_models).
 */
@Service
public class AiMonitoringService {

    private static final List<String> CONFIRMED_STATUSES = List.of("COMPLETED", "DOCTOR_CONFIRMED");
    private static final List<String> RISK_LEVEL_ORDER = List.of("very_low", "low", "moderate", "high", "very_high");
    private static final Map<String, String> RISK_LEVEL_LABELS = Map.of(
            "very_low", "Rất thấp",
            "low", "Thấp",
            "moderate", "Trung bình",
            "high", "Cao",
            "very_high", "Rất cao"
    );

    private final ImagingRecordRepository imagingRecordRepository;
    private final AiModelRepository aiModelRepository;

    public AiMonitoringService(ImagingRecordRepository imagingRecordRepository, AiModelRepository aiModelRepository) {
        this.imagingRecordRepository = imagingRecordRepository;
        this.aiModelRepository = aiModelRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> riskLevelDistribution() {
        Map<String, Long> raw = new LinkedHashMap<>();
        for (Object[] row : imagingRecordRepository.countGroupByRiskLevel()) {
            String level = row[0] == null ? "unknown" : row[0].toString().toLowerCase(Locale.ROOT);
            raw.merge(level, (Long) row[1], Long::sum);
        }
        Map<String, Long> ordered = new LinkedHashMap<>();
        for (String level : RISK_LEVEL_ORDER) {
            ordered.put(RISK_LEVEL_LABELS.get(level), raw.getOrDefault(level, 0L));
        }
        long knownOther = raw.entrySet().stream()
                .filter(e -> !RISK_LEVEL_ORDER.contains(e.getKey()))
                .mapToLong(Map.Entry::getValue)
                .sum();
        if (knownOther > 0) {
            ordered.put("Khác", knownOther);
        }
        return ordered;
    }

    @Transactional(readOnly = true)
    public long totalWithKnownRiskLevel() {
        return riskLevelDistribution().values().stream().mapToLong(Long::longValue).sum();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> confidenceTrend(int days) {
        LocalDate since = LocalDate.now().minusDays(days - 1L);
        Map<LocalDate, Double> byDate = new LinkedHashMap<>();
        for (Object[] row : imagingRecordRepository.avgConfidenceByDateSince(since)) {
            LocalDate date = (LocalDate) row[0];
            Double avg = row[1] == null ? null : ((Number) row[1]).doubleValue();
            byDate.put(date, avg);
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        for (LocalDate d = since; !d.isAfter(LocalDate.now()); d = d.plusDays(1)) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", d);
            Double avg = byDate.get(d);
            point.put("avgConfidence", avg == null ? null
                    : BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP));
            trend.add(point);
        }
        return trend;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> overrideStats() {
        long overridden = imagingRecordRepository.countByDoctorOverrodeAiTrue();
        long accepted = imagingRecordRepository.countByDoctorOverrodeAiFalse();
        long tracked = overridden + accepted;
        long legacyUntracked = imagingRecordRepository.countByStatusInAndDoctorOverrodeAiIsNull(CONFIRMED_STATUSES);
        long rejected = imagingRecordRepository.countByStatus("DOCTOR_REJECTED");
        long totalReviewed = tracked + rejected;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("overriddenCount", overridden);
        stats.put("acceptedCount", accepted);
        stats.put("trackedCount", tracked);
        stats.put("legacyUntrackedCount", legacyUntracked);
        stats.put("rejectedCount", rejected);
        stats.put("overrideRatePercent", percent(overridden, tracked));
        stats.put("rejectionRatePercent", percent(rejected, totalReviewed));
        return stats;
    }

    @Transactional(readOnly = true)
    public List<ImagingRecord> recentAiFailedCases(int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        return imagingRecordRepository.findByStatusOrderByCreatedAtDesc("AI_FAILED", pageable);
    }

    @Transactional(readOnly = true)
    public long countAiFailed() {
        return imagingRecordRepository.countByStatus("AI_FAILED");
    }

    @Transactional(readOnly = true)
    public List<AiModel> listModelVersions() {
        return aiModelRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public AiModel registerModelVersion(String modelName, String version, String apiEndpoint,
                                         BigDecimal accuracy, String description) {
        if (isBlank(modelName) || isBlank(version)) {
            throw new IllegalArgumentException("Tên model và phiên bản không được để trống.");
        }
        AiModel model = AiModel.builder()
                .modelName(modelName.trim())
                .version(version.trim())
                .apiEndpoint(isBlank(apiEndpoint) ? null : apiEndpoint.trim())
                .accuracy(accuracy)
                .description(isBlank(description) ? null : description.trim())
                .status("ACTIVE")
                .createdAt(Instant.now())
                .build();
        return aiModelRepository.save(model);
    }

    private BigDecimal percent(long part, long total) {
        if (total <= 0) return null;
        return BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
