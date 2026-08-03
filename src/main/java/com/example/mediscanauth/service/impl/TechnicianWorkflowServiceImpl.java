package com.example.mediscanauth.service.impl;

import com.example.mediscanauth.model.*;
import com.example.mediscanauth.repository.*;
import com.example.mediscanauth.service.TechnicianWorkflowService;
import com.example.mediscanauth.service.UserAccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TechnicianWorkflowServiceImpl implements TechnicianWorkflowService {

    // Da AI xu ly xong, khong con ket o buoc phan tich AI nua (thanh cong hoac AI that bai deu tinh la "da xu ly").
    private static final List<String> AI_PROCESSED_STATUSES = List.of("PENDING_DOCTOR", "COMPLETED", "DOCTOR_REJECTED");
    // Con dang nam trong hang cho, chua duoc bac si chot ket luan.
    private static final List<String> PENDING_PROCESSING_STATUSES = List.of("PENDING_AI", "PENDING_DOCTOR");

    private final AppointmentRepository appointmentRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final XrayImageRepository xrayImageRepository;
    private final PatientRepository patientRepository;
    private final AiAnalysisResultRepository aiAnalysisResultRepository;
    private final DoctorReviewRepository doctorReviewRepository;
    private final UserAccountService userAccountService;
    private final ImagingRecordRepository imagingRecordRepository;

    public TechnicianWorkflowServiceImpl(AppointmentRepository appointmentRepository,
                                         MedicalRecordRepository medicalRecordRepository,
                                         XrayImageRepository xrayImageRepository,
                                         PatientRepository patientRepository,
                                         AiAnalysisResultRepository aiAnalysisResultRepository,
                                         DoctorReviewRepository doctorReviewRepository,
                                         UserAccountService userAccountService,
                                         ImagingRecordRepository imagingRecordRepository) {
        this.appointmentRepository = appointmentRepository;
        this.medicalRecordRepository = medicalRecordRepository;
        this.xrayImageRepository = xrayImageRepository;
        this.patientRepository = patientRepository;
        this.aiAnalysisResultRepository = aiAnalysisResultRepository;
        this.doctorReviewRepository = doctorReviewRepository;
        this.userAccountService = userAccountService;
        this.imagingRecordRepository = imagingRecordRepository;
    }

    @Override
    public List<Appointment> findRecentAppointments() {
        return appointmentRepository.findTop10ByOrderByScheduledTimeDesc();
    }

    @Override
    public List<MedicalRecord> findRecentRecords() {
        return medicalRecordRepository.findTop10ByOrderByCreatedAtDesc();
    }

    @Override
    public List<XrayImage> findRecentImages() {
        return xrayImageRepository.findTop10ByOrderByUploadedAtDesc();
    }

    @Override
    public long countCapturedToday(String technicianEmail) {
        return imagingRecordRepository.countByTechnicianEmailAndCapturedAt(technicianEmail, LocalDate.now());
    }

    @Override
    public long countPendingAiToday(String technicianEmail) {
        return imagingRecordRepository.countByTechnicianEmailAndStatusAndCapturedAt(
                technicianEmail, "PENDING_AI", LocalDate.now());
    }

    @Override
    public long countPendingProcessingToday(String technicianEmail) {
        return imagingRecordRepository.countByTechnicianEmailAndStatusInAndCapturedAt(
                technicianEmail, PENDING_PROCESSING_STATUSES, LocalDate.now());
    }

    @Override
    public long countAiProcessedToday(String technicianEmail) {
        return imagingRecordRepository.countByTechnicianEmailAndStatusInAndCapturedAt(
                technicianEmail, AI_PROCESSED_STATUSES, LocalDate.now());
    }

    @Override
    public long countDoctorApprovedToday(String technicianEmail) {
        return imagingRecordRepository.countByTechnicianEmailAndStatusAndCapturedAt(
                technicianEmail, "COMPLETED", LocalDate.now());
    }

    @Override
    @Transactional
    public Appointment createAppointment(String technicianEmail,
                                         String patientEmail,
                                         String doctorEmail,
                                         LocalDateTime scheduledTime,
                                         String bodyPart,
                                         String location,
                                         String note) {
        User technician = userAccountService.findByEmail(technicianEmail);
        Patient patient = findOrCreatePatient(patientEmail);
        User doctor = findOptionalUser(doctorEmail);

        Appointment appointment = new Appointment();
        appointment.setAppointmentCode(nextCode("APT", appointmentRepository.count() + 1));
        appointment.setPatient(patient);
        appointment.setTechnician(technician);
        appointment.setDoctor(doctor);
        appointment.setScheduledTime(scheduledTime);
        appointment.setBodyPart(bodyPart);
        appointment.setLocation(location);
        appointment.setNote(note);
        appointment.setStatus("SCHEDULED");
        return appointmentRepository.save(appointment);
    }

    @Override
    @Transactional
    public MedicalRecord uploadImageAndCreateRecord(String technicianEmail,
                                                    Long appointmentId,
                                                    String patientEmail,
                                                    String doctorEmail,
                                                    String symptomDescription,
                                                    String bodyPart,
                                                    String originalImagePath,
                                                    String viewPosition) {
        User technician = userAccountService.findByEmail(technicianEmail);
        Appointment appointment = appointmentId == null ? null : appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lịch chụp #" + appointmentId));
        Patient patient = appointment != null ? appointment.getPatient() : findOrCreatePatient(patientEmail);
        User doctor = appointment != null && appointment.getDoctor() != null ? appointment.getDoctor() : findOptionalUser(doctorEmail);
        String resolvedBodyPart = isBlank(bodyPart) && appointment != null ? appointment.getBodyPart() : bodyPart;

        MedicalRecord record = new MedicalRecord();
        record.setRecordCode(nextCode("MR", medicalRecordRepository.count() + 1));
        record.setPatient(patient);
        record.setAppointment(appointment);
        record.setTechnician(technician);
        record.setDoctor(doctor);
        record.setSymptomDescription(symptomDescription);
        record.setBodyPart(resolvedBodyPart);
        record.setStatus("UPLOADED");
        MedicalRecord savedRecord = medicalRecordRepository.save(record);

        XrayImage image = new XrayImage();
        image.setRecord(savedRecord);
        image.setUploadedBy(technician);
        image.setOriginalImagePath(originalImagePath);
        image.setBodyPart(resolvedBodyPart);
        image.setViewPosition(viewPosition);
        image.setStatus("UPLOADED");
        xrayImageRepository.save(image);

        if (appointment != null) {
            appointment.setStatus("COMPLETED");
            appointmentRepository.save(appointment);
        }

        return savedRecord;
    }

    private Patient findOrCreatePatient(String patientEmail) {
        User user = userAccountService.findByEmail(patientEmail);
        return patientRepository.findByUser(user).orElseGet(() -> {
            Patient patient = new Patient();
            patient.setUser(user);
            patient.setFullName(user.getFullName());
            patient.setPhone(user.getPhone());
            patient.setGender("OTHER");
            patient.setDateOfBirth(LocalDate.of(2000, 1, 1));
            return patientRepository.save(patient);
        });
    }

    private User findOptionalUser(String email) {
        if (isBlank(email)) {
            return null;
        }
        return userAccountService.findByEmail(email);
    }

    private String nextCode(String prefix, long next) {
        return prefix + "-" + LocalDate.now().getYear() + "-" + String.format("%05d", next);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}