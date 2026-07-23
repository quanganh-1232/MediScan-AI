package com.example.mediscanauth.constant;

/**
 * Single source of truth for operational thresholds that used to be
 * duplicated (and, for LEGACY_TEXT_COLUMN_LIMIT, inconsistently duplicated)
 * as private constants across ImagingRecordServiceImpl, PatientWorkflowServiceImpl
 * and ReceptionistServiceImpl. Also backs the read-only Admin "System / AI
 * Configuration" screen so these values are visible instead of buried in code.
 */
public final class OperationalConfig {

    private OperationalConfig() {
    }

    // ==================== AI service call bounds ====================
    public static final int AI_CONNECT_TIMEOUT_MS = 5_000;
    public static final int AI_READ_TIMEOUT_MS = 45_000;

    /** Confidence (%) at or above which the AI recommendation wording is stronger. */
    public static final int AI_CONFIDENCE_STRONG_THRESHOLD = 80;

    /**
     * Safety-net truncation length applied to AI/doctor text before saving.
     * The backing DB columns (ai_prediction, doctor_conclusion, recommendation)
     * are all TEXT (unbounded in MySQL up to 65,535 bytes), so this is not a
     * hard DB limit — just a sane cap to avoid pathological payloads.
     */
    public static final int LEGACY_TEXT_COLUMN_LIMIT = 900;

    // ==================== Clinic scheduling (receptionist booking) ====================
    public static final int CLINIC_OPEN_HOUR = 6;
    public static final int CLINIC_CLOSE_HOUR = 21;
    public static final int SLOT_MINUTES = 30;
    public static final int MAX_FUTURE_BOOKING_DAYS = 90;
    public static final int MAX_SYMPTOM_LENGTH = 100;
    public static final int MAX_NOTE_LENGTH = 500;

    // ==================== Admin listing ====================
    public static final int ADMIN_USER_PAGE_SIZE = 20;
}
