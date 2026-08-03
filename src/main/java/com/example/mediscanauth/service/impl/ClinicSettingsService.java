package com.example.mediscanauth.service.impl;

import com.example.mediscanauth.constant.OperationalConfig;
import com.example.mediscanauth.model.ClinicSettings;
import com.example.mediscanauth.repository.ClinicSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Single source of truth for the clinic's editable operating parameters
 * (hours, slot length, max future booking window). Backed by a singleton DB
 * row so admin edits take effect immediately across the whole system —
 * booking validation (ReceptionistServiceImpl) and the schedule timeline /
 * slot picker (ReceptionistDashboardController) both read through here
 * instead of hardcoded constants.
 */
@Service
public class ClinicSettingsService {

    private static final int MIN_SLOT_MINUTES = 5;
    private static final int MAX_SLOT_MINUTES = 240;
    private static final int MAX_BOOKING_HORIZON_DAYS = 3650;

    private final ClinicSettingsRepository repository;
    private final AuditLogService auditLogService;

    public ClinicSettingsService(ClinicSettingsRepository repository, AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public ClinicSettings getSettings() {
        return repository.findById(ClinicSettings.SINGLETON_ID).orElseGet(this::createDefaultSettings);
    }

    public LocalTime getOpenTime() {
        return LocalTime.of(getSettings().getOpenHour(), 0);
    }

    public LocalTime getCloseTime() {
        return LocalTime.of(getSettings().getCloseHour(), 0);
    }

    public int getSlotMinutes() {
        return getSettings().getSlotMinutes();
    }

    public int getMaxFutureBookingDays() {
        return getSettings().getMaxFutureBookingDays();
    }

    @Transactional
    public ClinicSettings updateSettings(Integer openHour, Integer closeHour, Integer slotMinutes,
                                          Integer maxFutureBookingDays, String adminEmail) {
        if (openHour == null || closeHour == null || slotMinutes == null || maxFutureBookingDays == null) {
            throw new IllegalArgumentException("Vui lòng nhập đầy đủ các thông số.");
        }
        if (openHour < 0 || openHour > 23 || closeHour < 1 || closeHour > 24) {
            throw new IllegalArgumentException("Giờ hoạt động phải nằm trong khoảng 0-24.");
        }
        if (openHour >= closeHour) {
            throw new IllegalArgumentException("Giờ mở cửa phải trước giờ đóng cửa.");
        }
        if (slotMinutes < MIN_SLOT_MINUTES || slotMinutes > MAX_SLOT_MINUTES) {
            throw new IllegalArgumentException(
                    "Độ dài mỗi ca khám phải từ " + MIN_SLOT_MINUTES + " đến " + MAX_SLOT_MINUTES + " phút.");
        }
        if ((closeHour - openHour) * 60 < slotMinutes) {
            throw new IllegalArgumentException("Giờ hoạt động quá ngắn so với độ dài mỗi ca khám.");
        }
        if (maxFutureBookingDays < 1 || maxFutureBookingDays > MAX_BOOKING_HORIZON_DAYS) {
            throw new IllegalArgumentException("Đặt lịch trước tối đa phải từ 1 đến " + MAX_BOOKING_HORIZON_DAYS + " ngày.");
        }

        ClinicSettings settings = getSettings();
        String before = describe(settings);
        settings.setOpenHour(openHour);
        settings.setCloseHour(closeHour);
        settings.setSlotMinutes(slotMinutes);
        settings.setMaxFutureBookingDays(maxFutureBookingDays);
        settings.setUpdatedAt(LocalDateTime.now());
        settings.setUpdatedBy(adminEmail);
        ClinicSettings saved = repository.save(settings);

        auditLogService.log(adminEmail, "SYSTEM_CONFIG_UPDATED", "ClinicSettings", "1",
                "Cap nhat cau hinh phong kham: " + before + " -> " + describe(saved) + ".");
        return saved;
    }

    private ClinicSettings createDefaultSettings() {
        ClinicSettings settings = new ClinicSettings();
        settings.setId(ClinicSettings.SINGLETON_ID);
        settings.setOpenHour(OperationalConfig.CLINIC_OPEN_HOUR);
        settings.setCloseHour(OperationalConfig.CLINIC_CLOSE_HOUR);
        settings.setSlotMinutes(OperationalConfig.SLOT_MINUTES);
        settings.setMaxFutureBookingDays(OperationalConfig.MAX_FUTURE_BOOKING_DAYS);
        settings.setUpdatedAt(LocalDateTime.now());
        return repository.save(settings);
    }

    private String describe(ClinicSettings s) {
        return s.getOpenHour() + "h-" + s.getCloseHour() + "h, " + s.getSlotMinutes()
                + " phut/ca, dat truoc toi da " + s.getMaxFutureBookingDays() + " ngay";
    }
}
