package com.example.mediscanauth.exception.customize;

/**
 * Raised when an appointment would be created or moved onto a doctor who
 * already has another active appointment too close to the same time.
 * Kept distinct from generic validation errors so the receptionist UI can
 * surface it as an attention-grabbing popup instead of an inline banner.
 */
public class DoctorScheduleConflictException extends RuntimeException {
    public DoctorScheduleConflictException(String message) {
        super(message);
    }
}
