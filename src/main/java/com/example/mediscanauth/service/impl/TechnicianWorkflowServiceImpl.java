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

    private final AppointmentRepository appointmentRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final XrayImageRepository xrayImageRepository;
    private final PatientRepository patientRepository;
    private final AiAnalysisResultRepository aiAnalysisResultRepository;
    private final DoctorReviewRepository doctorReviewRepository;
    private final UserAccountService userAccountService;

    public TechnicianWorkflowServiceImpl(AppointmentRepository appointmentRepository,
                                         MedicalRecordRepository medicalRecordRepository,
                                         XrayImageRepository xrayImageRepository,
                                         PatientRepository patientRepository,
                                         AiAnalysisResultRepository aiAnalysisResultRepository,
                                         DoctorReviewRepository doctorReviewRepository,
                                         UserAccountService userAccountService) {
        this.appointmentRepository = appointmentRepository;
        this.medicalRecordRepository = medicalRecordRepository;
        this.xrayImageRepository = xrayImageRepository;
        this.patientRepository = patientRepository;
        this.aiAnalysisResultRepository = aiAnalysisResultRepository;
        this.doctorReviewRepository = doctorReviewRepository;
        this.userAccountService = userAccountService;
    }

    @Override
    public List<Appointment> findRecentAppointments() {
        return appointmentRepository.findTop10ByOrderByScheduledTimeDesc();
    }

    @Override
    public List<Appointment> findScheduledAppointments() {
        return appointmentRepository.findByStatusOrderByScheduledTimeAsc("SCHEDULED");
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
    public long countScheduledAppointments() {
        return appointmentRepository.countByStatus("SCHEDULED");
    }

    @Override
    public long countUploadedRecords() {
        return medicalRecordRepository.countByStatus("UPLOADED");
    }

    @Override
    public long countUploadedImages() {
        return xrayImageRepository.countByStatus("UPLOADED");
    }

    @Override
    public long countSuccessfulAiResults() {
        return aiAnalysisResultRepository.countByStatus("SUCCESS");
    }

    @Override
    public long countApprovedReviews() {
        return doctorReviewRepository.countByApprovalStatus("APPROVED");
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