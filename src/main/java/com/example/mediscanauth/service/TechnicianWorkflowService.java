package com.example.mediscanauth.service;

import com.example.mediscanauth.model.Appointment;
import com.example.mediscanauth.model.MedicalRecord;
import com.example.mediscanauth.model.XrayImage;

import java.time.LocalDateTime;
import java.util.List;

public interface TechnicianWorkflowService {

    List<Appointment> findRecentAppointments();

    List<MedicalRecord> findRecentRecords();

    List<XrayImage> findRecentImages();

    /** Số phim (ImagingRecord) kỹ thuật viên này đã chụp trong hôm nay, bất kể trạng thái. */
    long countCapturedToday(String technicianEmail);

    /** Số phim kỹ thuật viên này chụp hôm nay đang chờ AI phân tích (status PENDING_AI). */
    long countPendingAiToday(String technicianEmail);

    /** Số phim kỹ thuật viên này chụp hôm nay chưa được bác sĩ chốt kết luận (đang chờ AI hoặc chờ bác sĩ). */
    long countPendingProcessingToday(String technicianEmail);

    /** Số phim kỹ thuật viên này chụp hôm nay đã được AI xử lý xong (thành công hoặc bị bác sĩ từ chối), không còn kẹt ở bước AI. */
    long countAiProcessedToday(String technicianEmail);

    /** Số phim kỹ thuật viên này chụp hôm nay đã được bác sĩ xác nhận kết luận (COMPLETED). */
    long countDoctorApprovedToday(String technicianEmail);

    Appointment createAppointment(String technicianEmail, String patientEmail, String doctorEmail, LocalDateTime scheduledTime, String bodyPart, String location, String note);

    MedicalRecord uploadImageAndCreateRecord(String technicianEmail, Long appointmentId, String patientEmail, String doctorEmail, String symptomDescription, String bodyPart, String originalImagePath, String viewPosition);
}
