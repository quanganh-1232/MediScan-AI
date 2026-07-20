package com.example.mediscanauth.service;

import com.example.mediscanauth.model.Appointment;
import com.example.mediscanauth.model.Patient;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.repository.AppointmentRepository;
import com.example.mediscanauth.repository.PatientRepository;
import com.example.mediscanauth.repository.UserRepository;
import com.example.mediscanauth.service.impl.AdminManagementService;
import com.example.mediscanauth.service.impl.UserAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class AdminManagementServiceIntegrationTests {

    @Autowired private UserAdminService userAdminService;
    @Autowired private UserAccountService userAccountService;
    @Autowired private AdminManagementService adminManagementService;
    @Autowired private UserRepository userRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AppointmentRepository appointmentRepository;

    @Test
    void createsUpdatesAndLocksStaffByRole() {
        User doctor = userAdminService.createStaff(
                "Bác sĩ kiểm thử", "doctor.test@mediscan.com", "0901000000", "123456", "DOCTOR");

        userAdminService.updateStaff(doctor.getUserId(), "Bác sĩ đã sửa",
                doctor.getEmail(), "0901999999", "DOCTOR");
        userAdminService.updateStaffStatus(doctor.getUserId(), "LOCKED", "DOCTOR");

        User saved = userRepository.findById(doctor.getUserId()).orElseThrow();
        assertEquals("Bác sĩ đã sửa", saved.getFullName());
        assertEquals("LOCKED", saved.getStatus());
        assertTrue(userAdminService.findStaffByRole("DOCTOR", "đã sửa").stream()
                .anyMatch(item -> item.getUserId().equals(doctor.getUserId())));
    }

    @Test
    void assignsTechnicianAndTracksCompletedCases() {
        User patientUser = userRepository.findByEmail("patient@mediscan.com").orElseThrow();
        User technician = userRepository.findByEmail("tech@mediscan.com").orElseThrow();
        long completedBefore = adminManagementService.completedCases(technician.getUserId());
        Patient patient = patientRepository.findByUser(patientUser).orElseGet(() -> {
            Patient created = new Patient();
            created.setUser(patientUser);
            created.setFullName(patientUser.getFullName());
            return patientRepository.save(created);
        });
        Appointment appointment = new Appointment();
        appointment.setAppointmentCode("APT-ADMIN-TEST");
        appointment.setPatient(patient);
        appointment.setScheduledTime(LocalDateTime.now().plusDays(1));
        appointment.setStatus("PENDING");
        appointment = appointmentRepository.save(appointment);

        adminManagementService.updateAppointment(appointment.getAppointmentId(),
                appointment.getScheduledTime(), "Phòng X-quang", "", "COMPLETED",
                technician.getUserId(), null);

        assertEquals(completedBefore + 1, adminManagementService.completedCases(technician.getUserId()));
        assertEquals("COMPLETED", appointmentRepository.findById(appointment.getAppointmentId()).orElseThrow().getStatus());
    }

    @Test
    void backfillsMissingProfilesForExistingPatientAccounts() {
        User patientUser = userRepository.findByEmail("patient@mediscan.com").orElseThrow();
        patientRepository.findByUser(patientUser).ifPresent(patientRepository::delete);
        patientRepository.flush();

        assertTrue(adminManagementService.findPatients("patient@mediscan.com").stream()
                .anyMatch(patient -> patient.getUser() != null
                        && patientUser.getUserId().equals(patient.getUser().getUserId())));
    }

    @Test
    void registrationCreatesPatientProfileImmediately() {
        User user = userAccountService.registerPatient(
                "Bệnh nhân mới", "new.patient@mediscan.com", "0902000000", "123456", "123456");

        Patient profile = patientRepository.findByUser(user).orElseThrow();
        assertEquals("Bệnh nhân mới", profile.getFullName());
        assertEquals("0902000000", profile.getPhone());
    }
}
