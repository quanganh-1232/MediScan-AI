package com.example.mediscanauth.service;

import com.example.mediscanauth.exception.customize.DoctorScheduleConflictException;
import com.example.mediscanauth.exception.customize.InvalidFieldException;
import com.example.mediscanauth.model.Appointment;
import com.example.mediscanauth.model.AppointmentStatusHistory;
import com.example.mediscanauth.model.Patient;
import com.example.mediscanauth.model.Role;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.repository.AppointmentRepository;
import com.example.mediscanauth.repository.AppointmentStatusHistoryRepository;
import com.example.mediscanauth.repository.PatientRepository;
import com.example.mediscanauth.repository.RoleRepository;
import com.example.mediscanauth.repository.UserRepository;
import com.example.mediscanauth.service.impl.ClinicSettingsService;
import com.example.mediscanauth.service.impl.ReceptionistServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * LOGIC LAYER — kiểm thử nghiệp vụ thuần tuý của {@link ReceptionistServiceImpl}
 * (không đụng tới Controller/View/Thymeleaf). Mọi repository/collaborator đều
 * được mock bằng Mockito, ánh xạ theo từng Test Case ID trong file
 * "MediScan-AI_Receptionist_UnitTest.xlsx".
 *
 * Các test case KHÔNG nằm trong file này (do thuần UI/điều hướng, không có
 * logic phía server để mock, hoặc cần môi trường tích hợp thật):
 *  - REC_TC04, TC05, TC06, TC11, TC12: điều hướng link tĩnh + phân quyền Spring
 *    Security ("/receptionist/**" -> hasRole(RECEPTIONIST")) — xem SecurityConfig.
 *  - REC_TC07, TC34, TC40: hành vi JS phía client (lọc tab, gợi ý triệu chứng,
 *    reset form) không có phía server tương ứng.
 *  - REC_TC44: race-condition thật của 2 phiên đồng thời — được mô phỏng ở đây
 *    bằng cách giả lập claimAppointment() trả về 0 (đã bị người khác giành mất).
 * Các test case thuộc phần Controller/View (render model, redirect, flash) nằm
 * trong ReceptionistDashboardControllerUnitTest.java.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReceptionistServiceUnitTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentStatusHistoryRepository historyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ClinicSettingsService clinicSettingsService;

    @InjectMocks
    private ReceptionistServiceImpl receptionistService;

    private User receptionistUser;
    private User doctorUser;
    private Role doctorRole;
    private Role patientRole;

    private static final String RECEPTIONIST_EMAIL = "reception@mediscan.com";

    @BeforeEach
    void setUp() {
        receptionistUser = new User();
        receptionistUser.setUserId(6L);
        receptionistUser.setEmail(RECEPTIONIST_EMAIL);
        receptionistUser.setFullName("Pham Thi D");

        doctorRole = new Role();
        doctorRole.setRoleName("DOCTOR");

        patientRole = new Role();
        patientRole.setRoleName("PATIENT");

        doctorUser = new User();
        doctorUser.setUserId(2L);
        doctorUser.setEmail("doctor@mediscan.com");
        doctorUser.setFullName("Doctor Nguyen Van A");
        doctorUser.setStatus("ACTIVE");
        doctorUser.setRole(doctorRole);

        when(clinicSettingsService.getOpenTime()).thenReturn(LocalTime.of(7, 0));
        when(clinicSettingsService.getCloseTime()).thenReturn(LocalTime.of(20, 0));
        when(clinicSettingsService.getSlotMinutes()).thenReturn(30);
        when(clinicSettingsService.getMaxFutureBookingDays()).thenReturn(90);

        when(userRepository.findByEmail(RECEPTIONIST_EMAIL)).thenReturn(Optional.of(receptionistUser));
    }

    /** appointmentId/patientId la @GeneratedValue, khong co setter cong khai -> gan qua reflection cho test. */
    private static void setAppointmentId(Appointment appointment, Long id) {
        ReflectionTestUtils.setField(appointment, "appointmentId", id);
    }

    private static void setPatientId(Patient patient, Long id) {
        ReflectionTestUtils.setField(patient, "patientId", id);
    }

    // =========================================================================
    // 1. NHOM TIEP NHAN & CHECK-IN (REC_TC17 -> REC_TC19)
    // =========================================================================

    @Test
    @DisplayName("REC_TC17: Xac nhan lich hen PENDING thanh cong -> CONFIRMED")
    void test_REC_TC17_ConfirmAppointment_Pending_ShouldSetConfirmed() {
        Appointment apt = new Appointment();
        setAppointmentId(apt, 1L);
        apt.setStatus("PENDING");
        apt.setDoctor(doctorUser);
        apt.setScheduledTime(LocalDateTime.now().plusDays(1));

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(doctorUser));
        when(appointmentRepository.findByDoctorAndScheduledTimeBetween(eq(doctorUser), any(), any()))
                .thenReturn(List.of());

        Appointment result = receptionistService.confirmAppointment(1L, RECEPTIONIST_EMAIL);

        assertEquals("CONFIRMED", result.getStatus());
        assertEquals(receptionistUser, result.getReceptionist());
        verify(appointmentRepository).save(apt);
        verify(historyRepository).save(any(AppointmentStatusHistory.class));
    }

    @Test
    @DisplayName("REC_TC17b: Chan xac nhan lich hen khong o trang thai cho xac nhan")
    void test_REC_TC17b_ConfirmAppointment_NotPending_ShouldThrow() {
        Appointment apt = new Appointment();
        setAppointmentId(apt, 2L);
        apt.setStatus("CONFIRMED");
        when(appointmentRepository.findById(2L)).thenReturn(Optional.of(apt));

        InvalidFieldException ex = assertThrows(InvalidFieldException.class,
                () -> receptionistService.confirmAppointment(2L, RECEPTIONIST_EMAIL));
        assertTrue(ex.getMessage().contains("chờ xác nhận"));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("REC_TC17c: Xac nhan lai kiem tra trung lich bac si -> chan neu trung")
    void test_REC_TC17c_ConfirmAppointment_DoctorConflict_ShouldThrow() {
        Appointment apt = new Appointment();
        setAppointmentId(apt, 3L);
        apt.setStatus("PENDING");
        apt.setDoctor(doctorUser);
        apt.setScheduledTime(LocalDateTime.now().plusDays(1));

        Appointment conflicting = new Appointment();
        setAppointmentId(conflicting, 999L);
        conflicting.setStatus("CONFIRMED");
        conflicting.setScheduledTime(apt.getScheduledTime());
        conflicting.setAppointmentCode("APT-2026-00099");
        Patient otherPatient = new Patient();
        otherPatient.setFullName("Benh nhan khac");
        conflicting.setPatient(otherPatient);

        when(appointmentRepository.findById(3L)).thenReturn(Optional.of(apt));
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(doctorUser));
        when(appointmentRepository.findByDoctorAndScheduledTimeBetween(eq(doctorUser), any(), any()))
                .thenReturn(List.of(conflicting));

        assertThrows(DoctorScheduleConflictException.class,
                () -> receptionistService.confirmAppointment(3L, RECEPTIONIST_EMAIL));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("REC_TC18: Check-in thanh cong cho lich hen CONFIRMED dung ngay hom nay")
    void test_REC_TC18_CheckIn_Today_ShouldSucceed() {
        Appointment apt = new Appointment();
        setAppointmentId(apt, 4L);
        apt.setStatus("CONFIRMED");
        apt.setScheduledTime(LocalDateTime.now());

        when(appointmentRepository.findById(4L)).thenReturn(Optional.of(apt));
        when(appointmentRepository.findMaxQueueNumberForDate(any(), any())).thenReturn(3);

        Appointment result = receptionistService.checkInAppointment(4L, RECEPTIONIST_EMAIL);

        assertEquals("CHECKED_IN", result.getStatus());
        assertEquals(4, result.getQueueNumber());
        verify(appointmentRepository).save(apt);
    }

    @Test
    @DisplayName("REC_TC18b: Chan check-in lich hen dat cho ngay trong tuong lai")
    void test_REC_TC18b_CheckIn_FutureDate_ShouldThrow() {
        Appointment apt = new Appointment();
        setAppointmentId(apt, 5L);
        apt.setStatus("CONFIRMED");
        apt.setScheduledTime(LocalDateTime.now().plusDays(2));
        when(appointmentRepository.findById(5L)).thenReturn(Optional.of(apt));

        InvalidFieldException ex = assertThrows(InvalidFieldException.class,
                () -> receptionistService.checkInAppointment(5L, RECEPTIONIST_EMAIL));
        assertTrue(ex.getMessage().contains("chưa thể check-in"));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("REC_TC18c: Chan check-in khi lich hen chua duoc xac nhan")
    void test_REC_TC18c_CheckIn_NotConfirmed_ShouldThrow() {
        Appointment apt = new Appointment();
        setAppointmentId(apt, 6L);
        apt.setStatus("PENDING");
        when(appointmentRepository.findById(6L)).thenReturn(Optional.of(apt));

        assertThrows(InvalidFieldException.class,
                () -> receptionistService.checkInAppointment(6L, RECEPTIONIST_EMAIL));
    }

    @Test
    @DisplayName("REC_TC19: Hoan tat lich hen IN_PROGRESS -> COMPLETED")
    void test_REC_TC19_CompleteAppointment_InProgress_ShouldSetCompleted() {
        Appointment apt = new Appointment();
        setAppointmentId(apt, 7L);
        apt.setStatus("IN_PROGRESS");
        when(appointmentRepository.findById(7L)).thenReturn(Optional.of(apt));

        Appointment result = receptionistService.completeAppointment(7L, RECEPTIONIST_EMAIL);

        assertEquals("COMPLETED", result.getStatus());
        verify(appointmentRepository).save(apt);
    }

    @Test
    @DisplayName("REC_TC19b: Chan hoan tat khi lich hen khong o trang thai dang kham")
    void test_REC_TC19b_CompleteAppointment_NotInProgress_ShouldThrow() {
        Appointment apt = new Appointment();
        setAppointmentId(apt, 8L);
        apt.setStatus("CHECKED_IN");
        when(appointmentRepository.findById(8L)).thenReturn(Optional.of(apt));

        assertThrows(InvalidFieldException.class,
                () -> receptionistService.completeAppointment(8L, RECEPTIONIST_EMAIL));
    }

    // =========================================================================
    // 2. NHOM CHI TIET LICH HEN (REC_TC23 -> REC_TC28)
    // =========================================================================

    @Test
    @DisplayName("REC_TC23: Gan bac si khong trung lich -> thanh cong va ghi lich su")
    void test_REC_TC23_AssignDoctor_NoConflict_ShouldSucceed() {
        Appointment apt = new Appointment();
        setAppointmentId(apt, 10L);
        apt.setStatus("PENDING");
        apt.setScheduledTime(LocalDateTime.now().plusDays(1));

        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(apt));
        when(userRepository.findById(2L)).thenReturn(Optional.of(doctorUser));
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(doctorUser));
        when(appointmentRepository.findByDoctorAndScheduledTimeBetween(eq(doctorUser), any(), any()))
                .thenReturn(List.of());

        Appointment result = receptionistService.assignDoctor(10L, 2L, "Uu tien kham som", RECEPTIONIST_EMAIL);

        assertEquals(doctorUser, result.getDoctor());
        assertEquals(receptionistUser, result.getReceptionist());
        verify(appointmentRepository).save(apt);
        ArgumentCaptor<AppointmentStatusHistory> captor = ArgumentCaptor.forClass(AppointmentStatusHistory.class);
        verify(historyRepository).save(captor.capture());
        assertTrue(captor.getValue().getNote().contains("Uu tien kham som"));
        verify(notificationService).sendNotification(eq(doctorUser), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("REC_TC24: Gan bac si dang trung lich -> DoctorScheduleConflictException, khong luu")
    void test_REC_TC24_AssignDoctor_Conflict_ShouldThrow() {
        Appointment apt = new Appointment();
        setAppointmentId(apt, 11L);
        apt.setStatus("PENDING");
        apt.setScheduledTime(LocalDateTime.now().plusDays(1));

        Appointment conflicting = new Appointment();
        setAppointmentId(conflicting, 888L);
        conflicting.setStatus("CONFIRMED");
        conflicting.setScheduledTime(apt.getScheduledTime());
        conflicting.setAppointmentCode("APT-2026-00088");

        when(appointmentRepository.findById(11L)).thenReturn(Optional.of(apt));
        when(userRepository.findById(2L)).thenReturn(Optional.of(doctorUser));
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(doctorUser));
        when(appointmentRepository.findByDoctorAndScheduledTimeBetween(eq(doctorUser), any(), any()))
                .thenReturn(List.of(conflicting));

        assertThrows(DoctorScheduleConflictException.class,
                () -> receptionistService.assignDoctor(11L, 2L, null, RECEPTIONIST_EMAIL));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("REC_TC24b: Chan gan bac si cho lich hen da ket thuc (terminal status)")
    void test_REC_TC24b_AssignDoctor_TerminalAppointment_ShouldThrow() {
        Appointment apt = new Appointment();
        setAppointmentId(apt, 12L);
        apt.setStatus("COMPLETED");
        when(appointmentRepository.findById(12L)).thenReturn(Optional.of(apt));

        assertThrows(InvalidFieldException.class,
                () -> receptionistService.assignDoctor(12L, 2L, null, RECEPTIONIST_EMAIL));
        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("Bo sung (fix bug): Dat lich CACH lich cu dung 1 ca (slot) -> KHONG duoc coi la trung, phai thanh cong")
    void test_AssignDoctor_ExactlyOneSlotApart_ShouldNotConflict() {
        // Bac si da co lich luc 07:30, do dai moi ca 30p -> dat luc 08:00 (cach dung 1 ca) phai duoc phep.
        Appointment apt = new Appointment();
        setAppointmentId(apt, 17L);
        apt.setStatus("PENDING");
        LocalDate today = LocalDate.now();
        apt.setScheduledTime(LocalDateTime.of(today, LocalTime.of(8, 0)));

        Appointment existingAt0730 = new Appointment();
        setAppointmentId(existingAt0730, 18L);
        existingAt0730.setStatus("CONFIRMED");
        existingAt0730.setScheduledTime(LocalDateTime.of(today, LocalTime.of(7, 30)));

        when(appointmentRepository.findById(17L)).thenReturn(Optional.of(apt));
        when(userRepository.findById(2L)).thenReturn(Optional.of(doctorUser));
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(doctorUser));
        // Repository tra ve dung nhung gi nam trong khoang gia lap [08:00-29p, 08:00+29p] = [07:31, 08:29],
        // 07:30 nam ngoai khoang nay nen se khong duoc repository tra ve trong truong hop that.
        when(appointmentRepository.findByDoctorAndScheduledTimeBetween(eq(doctorUser),
                eq(LocalDateTime.of(today, LocalTime.of(7, 31))), eq(LocalDateTime.of(today, LocalTime.of(8, 29)))))
                .thenReturn(List.of());

        Appointment result = receptionistService.assignDoctor(17L, 2L, null, RECEPTIONIST_EMAIL);

        assertEquals(doctorUser, result.getDoctor());
        verify(appointmentRepository).save(apt);
    }

    @Test
    @DisplayName("REC_TC26: Huy lich hen kem ly do -> CANCELLED va ghi ly do vao lich su")
    void test_REC_TC26_CancelAppointment_WithReason_ShouldSetCancelledAndLogReason() {
        Appointment apt = new Appointment();
        setAppointmentId(apt, 13L);
        apt.setStatus("CONFIRMED");
        when(appointmentRepository.findById(13L)).thenReturn(Optional.of(apt));

        Appointment result = receptionistService.cancelAppointment(13L, "Benh nhan doi lich", RECEPTIONIST_EMAIL);

        assertEquals("CANCELLED", result.getStatus());
        assertEquals("Benh nhan doi lich", result.getNote());
        ArgumentCaptor<AppointmentStatusHistory> captor = ArgumentCaptor.forClass(AppointmentStatusHistory.class);
        verify(historyRepository).save(captor.capture());
        assertTrue(captor.getValue().getNote().contains("Benh nhan doi lich"));
    }

    @Test
    @DisplayName("REC_TC27: Chan huy lich hen da o trang thai ket thuc (COMPLETED/CANCELLED/MISSED)")
    void test_REC_TC27_CancelAppointment_TerminalStatus_ShouldThrow() {
        Appointment apt = new Appointment();
        setAppointmentId(apt, 14L);
        apt.setStatus("COMPLETED");
        when(appointmentRepository.findById(14L)).thenReturn(Optional.of(apt));

        InvalidFieldException ex = assertThrows(InvalidFieldException.class,
                () -> receptionistService.cancelAppointment(14L, "Ly do", RECEPTIONIST_EMAIL));
        assertTrue(ex.getMessage().contains("không thể hủy"));
    }

    @Test
    @DisplayName("REC_TC28: Danh dau vang mat cho lich hen CONFIRMED -> MISSED")
    void test_REC_TC28_MarkMissed_Confirmed_ShouldSetMissed() {
        Appointment apt = new Appointment();
        setAppointmentId(apt, 15L);
        apt.setStatus("CONFIRMED");
        when(appointmentRepository.findById(15L)).thenReturn(Optional.of(apt));

        Appointment result = receptionistService.markMissed(15L, RECEPTIONIST_EMAIL);

        assertEquals("MISSED", result.getStatus());
        verify(appointmentRepository).save(apt);
    }

    @Test
    @DisplayName("REC_TC28b: Chan danh dau vang mat cho lich hen da CHECKED_IN tro di")
    void test_REC_TC28b_MarkMissed_CheckedIn_ShouldThrow() {
        Appointment apt = new Appointment();
        setAppointmentId(apt, 16L);
        apt.setStatus("CHECKED_IN");
        when(appointmentRepository.findById(16L)).thenReturn(Optional.of(apt));

        assertThrows(InvalidFieldException.class,
                () -> receptionistService.markMissed(16L, RECEPTIONIST_EMAIL));
    }

    // =========================================================================
    // 3. NHOM TAO LICH MOI / WALK-IN (REC_TC31 -> REC_TC38)
    // =========================================================================

    @Test
    @DisplayName("REC_TC31: Dang ky walk-in hop le -> tao lich hen CONFIRMED ngay lap tuc")
    void test_REC_TC31_CreateWalkIn_Valid_ShouldCreateConfirmedAppointment() {
        when(patientRepository.findFirstByPhoneOrderByPatientIdDesc("0912345678")).thenReturn(Optional.empty());
        when(roleRepository.findByRoleName("PATIENT")).thenReturn(Optional.of(patientRole));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(patientRepository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(2L)).thenReturn(Optional.of(doctorUser));
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(doctorUser));
        when(appointmentRepository.findByDoctorAndScheduledTimeBetween(eq(doctorUser), any(), any()))
                .thenReturn(List.of());
        when(appointmentRepository.countPatientConflictsByPatient(any(), any(), any())).thenReturn(0L);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment saved = inv.getArgument(0);
            if (saved.getAppointmentId() == null) {
                setAppointmentId(saved, 100L);
            }
            return saved;
        });

        LocalDate date = LocalDate.now();
        LocalTime time = LocalTime.of(9, 0);

        Appointment result = receptionistService.createWalkInAppointment(
                "Nguyen Van An", "0912345678", "MALE", LocalDate.of(1990, 1, 1),
                "Dau khop goi", 2L, date, time, RECEPTIONIST_EMAIL);

        assertNotNull(result);
        assertEquals("CONFIRMED", result.getStatus());
        assertEquals(doctorUser, result.getDoctor());
        assertNotNull(result.getPatient());
        assertEquals("MALE", result.getPatient().getGender());
        assertEquals(LocalDate.of(1990, 1, 1), result.getPatient().getDateOfBirth());
        assertTrue(result.getAppointmentCode().startsWith("APT-"));
    }

    @Test
    @DisplayName("REC_TC32: Ho ten chua so/ky tu dac biet bi tu choi")
    void test_REC_TC32_CreateWalkIn_InvalidFullName_ShouldThrow() {
        InvalidFieldException ex = assertThrows(InvalidFieldException.class,
                () -> receptionistService.createWalkInAppointment(
                        "Nguyen123!", "0912345678", "MALE", null, null, null,
                        LocalDate.now(), LocalTime.of(9, 0), RECEPTIONIST_EMAIL));
        assertTrue(ex.getMessage().contains("Họ và tên không hợp lệ"));
        verifyNoInteractions(patientRepository);
    }

    @Test
    @DisplayName("REC_TC32b: Ho ten qua ngan (< 2 ky tu) bi tu choi")
    void test_REC_TC32b_CreateWalkIn_FullNameTooShort_ShouldThrow() {
        assertThrows(InvalidFieldException.class,
                () -> receptionistService.createWalkInAppointment(
                        "A", "0912345678", "MALE", null, null, null,
                        LocalDate.now(), LocalTime.of(9, 0), RECEPTIONIST_EMAIL));
    }

    @Test
    @DisplayName("REC_TC33: So dien thoai khong hop le bi tu choi")
    void test_REC_TC33_CreateWalkIn_InvalidPhone_ShouldThrow() {
        InvalidFieldException ex = assertThrows(InvalidFieldException.class,
                () -> receptionistService.createWalkInAppointment(
                        "Nguyen Van An", "12345", "MALE", null, null, null,
                        LocalDate.now(), LocalTime.of(9, 0), RECEPTIONIST_EMAIL));
        assertTrue(ex.getMessage().contains("Số điện thoại không hợp lệ"));
    }

    @Test
    @DisplayName("REC_TC35: Khong chon bac si -> tao lich hen khong gan bac si (unassigned)")
    void test_REC_TC35_CreateWalkIn_NoDoctorSelected_ShouldCreateUnassignedAppointment() {
        when(patientRepository.findFirstByPhoneOrderByPatientIdDesc("0987654321")).thenReturn(Optional.empty());
        when(roleRepository.findByRoleName("PATIENT")).thenReturn(Optional.of(patientRole));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(patientRepository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));
        when(appointmentRepository.countPatientConflictsByPatient(any(), any(), any())).thenReturn(0L);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment saved = inv.getArgument(0);
            if (saved.getAppointmentId() == null) {
                setAppointmentId(saved, 100L);
            }
            return saved;
        });

        Appointment result = receptionistService.createWalkInAppointment(
                "Tran Thi B", "0987654321", null, null, "Kham tong quat", null,
                LocalDate.now(), LocalTime.of(10, 0), RECEPTIONIST_EMAIL);

        assertNull(result.getDoctor());
        assertEquals("CONFIRMED", result.getStatus());
        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("REC_TC36: Ngay hen truoc hom nay bi tu choi")
    void test_REC_TC36_CreateWalkIn_PastDate_ShouldThrow() {
        InvalidFieldException ex = assertThrows(InvalidFieldException.class,
                () -> receptionistService.createWalkInAppointment(
                        "Nguyen Van An", "0912345678", "MALE", null, null, null,
                        LocalDate.now().minusDays(1), LocalTime.of(9, 0), RECEPTIONIST_EMAIL));
        assertTrue(ex.getMessage().contains("quá khứ"));
    }

    @Test
    @DisplayName("REC_TC36b: Ngay hen vuot qua so ngay dat truoc toi da bi tu choi")
    void test_REC_TC36b_CreateWalkIn_BeyondMaxFutureBookingDays_ShouldThrow() {
        InvalidFieldException ex = assertThrows(InvalidFieldException.class,
                () -> receptionistService.createWalkInAppointment(
                        "Nguyen Van An", "0912345678", "MALE", null, null, null,
                        LocalDate.now().plusDays(91), LocalTime.of(9, 0), RECEPTIONIST_EMAIL));
        assertTrue(ex.getMessage().contains("90 ngày"));
    }

    @Test
    @DisplayName("REC_TC37: Dat lich trung khung gio cua bac si -> DoctorScheduleConflictException, khong tao lich")
    void test_REC_TC37_CreateWalkIn_DoctorConflict_ShouldThrow() {
        when(patientRepository.findFirstByPhoneOrderByPatientIdDesc("0912345678")).thenReturn(Optional.empty());
        when(roleRepository.findByRoleName("PATIENT")).thenReturn(Optional.of(patientRole));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(patientRepository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(2L)).thenReturn(Optional.of(doctorUser));
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(doctorUser));

        Appointment conflicting = new Appointment();
        setAppointmentId(conflicting, 777L);
        conflicting.setStatus("CONFIRMED");
        conflicting.setScheduledTime(LocalDateTime.of(LocalDate.now(), LocalTime.of(9, 0)));
        conflicting.setAppointmentCode("APT-2026-00077");
        when(appointmentRepository.findByDoctorAndScheduledTimeBetween(eq(doctorUser), any(), any()))
                .thenReturn(List.of(conflicting));

        assertThrows(DoctorScheduleConflictException.class,
                () -> receptionistService.createWalkInAppointment(
                        "Nguyen Van An", "0912345678", "MALE", null, null, 2L,
                        LocalDate.now(), LocalTime.of(9, 0), RECEPTIONIST_EMAIL));
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    @DisplayName("REC_TC38: SDT trung ho so khach vang lai cu -> lien ket ho so cu, khong tao ban ghi trung")
    void test_REC_TC38_CreateWalkIn_ReuseExistingGuestPatient_ShouldLinkNotDuplicate() {
        Patient existingGuestPatient = new Patient();
        setPatientId(existingGuestPatient, 50L);
        existingGuestPatient.setPhone("0911222333");
        existingGuestPatient.setUser(null); // ho so khach vang lai, chua co tai khoan dang nhap

        when(patientRepository.findFirstByPhoneOrderByPatientIdDesc("0911222333"))
                .thenReturn(Optional.of(existingGuestPatient));
        when(roleRepository.findByRoleName("PATIENT")).thenReturn(Optional.of(patientRole));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(patientRepository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));
        when(appointmentRepository.countPatientConflictsByPatient(any(), any(), any())).thenReturn(0L);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment saved = inv.getArgument(0);
            if (saved.getAppointmentId() == null) {
                setAppointmentId(saved, 100L);
            }
            return saved;
        });

        Appointment result = receptionistService.createWalkInAppointment(
                "Le Van C", "0911222333", null, null, null, null,
                LocalDate.now(), LocalTime.of(11, 0), RECEPTIONIST_EMAIL);

        assertEquals(existingGuestPatient, result.getPatient());
        assertNotNull(existingGuestPatient.getUser(), "Ho so khach vang lai phai duoc gan tai khoan dummy sau khi tai su dung");
        verify(patientRepository, times(1)).save(existingGuestPatient);
        verify(patientRepository, never()).save(argThat(p -> p != existingGuestPatient));
    }

    @Test
    @DisplayName("Bo sung: Gioi tinh & ngay sinh duoc luu dung cho benh nhan walk-in moi")
    void test_CreateWalkIn_GenderAndDob_ShouldPersistForNewPatient() {
        when(patientRepository.findFirstByPhoneOrderByPatientIdDesc("0909998877")).thenReturn(Optional.empty());
        when(roleRepository.findByRoleName("PATIENT")).thenReturn(Optional.of(patientRole));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(patientRepository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));
        when(appointmentRepository.countPatientConflictsByPatient(any(), any(), any())).thenReturn(0L);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment saved = inv.getArgument(0);
            if (saved.getAppointmentId() == null) {
                setAppointmentId(saved, 100L);
            }
            return saved;
        });

        Appointment result = receptionistService.createWalkInAppointment(
                "Test Gender Dob", "0909998877", "FEMALE", LocalDate.of(1995, 5, 20), null, null,
                LocalDate.now(), LocalTime.of(9, 0), RECEPTIONIST_EMAIL);

        assertEquals("FEMALE", result.getPatient().getGender());
        assertEquals(LocalDate.of(1995, 5, 20), result.getPatient().getDateOfBirth());
    }

    @Test
    @DisplayName("Bo sung: Ho so khach vang lai cu da co gioi tinh thi KHONG bi ghi de")
    void test_CreateWalkIn_ExistingPatientWithGender_ShouldNotBeOverwritten() {
        Patient existing = new Patient();
        setPatientId(existing, 60L);
        existing.setPhone("0911222444");
        existing.setUser(null);
        existing.setGender("MALE");
        existing.setDateOfBirth(LocalDate.of(1980, 1, 1));

        when(patientRepository.findFirstByPhoneOrderByPatientIdDesc("0911222444")).thenReturn(Optional.of(existing));
        when(roleRepository.findByRoleName("PATIENT")).thenReturn(Optional.of(patientRole));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(patientRepository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));
        when(appointmentRepository.countPatientConflictsByPatient(any(), any(), any())).thenReturn(0L);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment saved = inv.getArgument(0);
            if (saved.getAppointmentId() == null) {
                setAppointmentId(saved, 100L);
            }
            return saved;
        });

        receptionistService.createWalkInAppointment(
                "Nguyen Van D", "0911222444", "FEMALE", LocalDate.of(1999, 9, 9), null, null,
                LocalDate.now(), LocalTime.of(9, 0), RECEPTIONIST_EMAIL);

        assertEquals("MALE", existing.getGender(), "Gioi tinh cu phai duoc giu nguyen, khong bi ghi de boi form moi");
        assertEquals(LocalDate.of(1980, 1, 1), existing.getDateOfBirth());
    }

    @Test
    @DisplayName("Bo sung: Ngay sinh o tuong lai bi tu choi")
    void test_CreateWalkIn_FutureDateOfBirth_ShouldThrow() {
        InvalidFieldException ex = assertThrows(InvalidFieldException.class,
                () -> receptionistService.createWalkInAppointment(
                        "Nguyen Van An", "0912345678", "MALE", LocalDate.now().plusDays(1), null, null,
                        LocalDate.now(), LocalTime.of(9, 0), RECEPTIONIST_EMAIL));
        assertTrue(ex.getMessage().contains("tương lai"));
    }

    // =========================================================================
    // 4. NHOM DANH SACH CHO (REC_TC42 -> REC_TC45)
    // =========================================================================

    @Test
    @DisplayName("REC_TC42: Goi vao mot benh nhan CHECKED_IN cu the -> IN_PROGRESS")
    void test_REC_TC42_CallInAppointment_CheckedIn_ShouldSetInProgress() {
        Appointment apt = new Appointment();
        setAppointmentId(apt, 20L);
        apt.setStatus("CHECKED_IN");
        apt.setQueueNumber(1);
        when(appointmentRepository.findById(20L)).thenReturn(Optional.of(apt));

        Appointment result = receptionistService.callInAppointment(20L, RECEPTIONIST_EMAIL);

        assertEquals("IN_PROGRESS", result.getStatus());
        verify(appointmentRepository).save(apt);
    }

    @Test
    @DisplayName("REC_TC42b: Chan goi vao benh nhan chua CHECKED_IN")
    void test_REC_TC42b_CallInAppointment_NotCheckedIn_ShouldThrow() {
        Appointment apt = new Appointment();
        setAppointmentId(apt, 21L);
        apt.setStatus("CONFIRMED");
        when(appointmentRepository.findById(21L)).thenReturn(Optional.of(apt));

        assertThrows(InvalidFieldException.class,
                () -> receptionistService.callInAppointment(21L, RECEPTIONIST_EMAIL));
    }

    @Test
    @DisplayName("REC_TC43: Goi so tiep theo se claim benh nhan CHECKED_IN som nhat trong hang cho")
    void test_REC_TC43_CallNextPatient_ShouldClaimEarliestCheckedIn() {
        Appointment earliest = new Appointment();
        setAppointmentId(earliest, 30L);
        earliest.setStatus("CHECKED_IN");
        earliest.setQueueNumber(1);

        Appointment later = new Appointment();
        setAppointmentId(later, 31L);
        later.setStatus("CHECKED_IN");
        later.setQueueNumber(2);

        when(appointmentRepository.findByStatusOrderByQueueNumberAsc("CHECKED_IN"))
                .thenReturn(List.of(earliest, later));
        when(appointmentRepository.claimAppointment(30L, "CHECKED_IN", "IN_PROGRESS", receptionistUser))
                .thenReturn(1);
        Appointment claimedReloaded = new Appointment();
        setAppointmentId(claimedReloaded, 30L);
        claimedReloaded.setStatus("IN_PROGRESS");
        when(appointmentRepository.findById(30L)).thenReturn(Optional.of(claimedReloaded));

        Appointment result = receptionistService.callNextPatient(RECEPTIONIST_EMAIL);

        assertEquals(30L, result.getAppointmentId());
        verify(appointmentRepository, never()).claimAppointment(eq(31L), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("REC_TC44: Hai lan goi dong thoi khong duoc claim trung mot benh nhan (atomic claim)")
    void test_REC_TC44_CallNextPatient_ConcurrentClaim_ShouldSkipAlreadyClaimedAndTakeNext() {
        Appointment first = new Appointment();
        setAppointmentId(first, 40L);
        first.setStatus("CHECKED_IN");
        first.setQueueNumber(1);

        Appointment second = new Appointment();
        setAppointmentId(second, 41L);
        second.setStatus("CHECKED_IN");
        second.setQueueNumber(2);

        when(appointmentRepository.findByStatusOrderByQueueNumberAsc("CHECKED_IN"))
                .thenReturn(List.of(first, second));
        // Phien lam viec khac da claim mat lich 40L truoc -> tra ve 0 dong bi anh huong
        when(appointmentRepository.claimAppointment(40L, "CHECKED_IN", "IN_PROGRESS", receptionistUser))
                .thenReturn(0);
        when(appointmentRepository.claimAppointment(41L, "CHECKED_IN", "IN_PROGRESS", receptionistUser))
                .thenReturn(1);
        Appointment claimedReloaded = new Appointment();
        setAppointmentId(claimedReloaded, 41L);
        claimedReloaded.setStatus("IN_PROGRESS");
        when(appointmentRepository.findById(41L)).thenReturn(Optional.of(claimedReloaded));

        Appointment result = receptionistService.callNextPatient(RECEPTIONIST_EMAIL);

        assertEquals(41L, result.getAppointmentId(), "Phien nay phai lay benh nhan tiep theo, khong duoc claim trung ban ghi da bi giu");
        verify(appointmentRepository).claimAppointment(40L, "CHECKED_IN", "IN_PROGRESS", receptionistUser);
        verify(appointmentRepository).claimAppointment(41L, "CHECKED_IN", "IN_PROGRESS", receptionistUser);
    }

    @Test
    @DisplayName("REC_TC45: Goi so tiep theo khi hang cho rong -> bao loi ro rang, khong im lang")
    void test_REC_TC45_CallNextPatient_EmptyQueue_ShouldThrowClearError() {
        when(appointmentRepository.findByStatusOrderByQueueNumberAsc("CHECKED_IN")).thenReturn(List.of());

        InvalidFieldException ex = assertThrows(InvalidFieldException.class,
                () -> receptionistService.callNextPatient(RECEPTIONIST_EMAIL));
        assertTrue(ex.getMessage().contains("Không có bệnh nhân"));
    }
}
