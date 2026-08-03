package com.example.mediscanauth.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Singleton row (id is always 1) holding the clinic operating parameters an
 * admin can edit at runtime — replaces the compile-time defaults in
 * OperationalConfig once this row exists, so the whole system (booking
 * validation, the receptionist schedule timeline, slot dropdowns) reads the
 * same live values instead of hardcoded constants.
 */
@Entity
@Table(name = "clinic_settings")
public class ClinicSettings {

    public static final Long SINGLETON_ID = 1L;

    @Id
    @Column(name = "id")
    private Long id = SINGLETON_ID;

    @Column(name = "open_hour", nullable = false)
    private Integer openHour;

    @Column(name = "close_hour", nullable = false)
    private Integer closeHour;

    @Column(name = "slot_minutes", nullable = false)
    private Integer slotMinutes;

    @Column(name = "max_future_booking_days", nullable = false)
    private Integer maxFutureBookingDays;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 150)
    private String updatedBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getOpenHour() {
        return openHour;
    }

    public void setOpenHour(Integer openHour) {
        this.openHour = openHour;
    }

    public Integer getCloseHour() {
        return closeHour;
    }

    public void setCloseHour(Integer closeHour) {
        this.closeHour = closeHour;
    }

    public Integer getSlotMinutes() {
        return slotMinutes;
    }

    public void setSlotMinutes(Integer slotMinutes) {
        this.slotMinutes = slotMinutes;
    }

    public Integer getMaxFutureBookingDays() {
        return maxFutureBookingDays;
    }

    public void setMaxFutureBookingDays(Integer maxFutureBookingDays) {
        this.maxFutureBookingDays = maxFutureBookingDays;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
