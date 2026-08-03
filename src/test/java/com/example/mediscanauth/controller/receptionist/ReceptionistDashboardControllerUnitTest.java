package com.example.mediscanauth.controller.receptionist;

import com.example.mediscanauth.exception.customize.DoctorScheduleConflictException;
import com.example.mediscanauth.exception.customize.InvalidFieldException;
import com.example.mediscanauth.model.Appointment;
import com.example.mediscanauth.model.Patient;
import com.example.mediscanauth.model.Role;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.repository.AppointmentRepository;
import com.example.mediscanauth.repository.AppointmentStatusHistoryRepository;
import com.example.mediscanauth.repository.UserRepository;
import com.example.mediscanauth.service.ReceptionistService;
import com.example.mediscanauth.service.impl.ClinicSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * VIEW LAYER — kiểm thử {@link ReceptionistDashboardController}: tên view trả về,
 * model attribute đưa ra cho Thymeleaf, redirect và flash message. KHÔNG kiểm thử
 * lại nghiệp vụ (đã có ở ReceptionistServiceUnitTest) — ReceptionistService luôn
 * được mock, controller ở đây chỉ được xác nhận là "chuyển tiếp" đúng dữ liệu và
 * điều hướng đúng route.
 *
 * Dùng MockMvc standalone (không dựng context Spring/Security thật) nên chạy
 * nhanh như unit test thuần và không cần template Thymeleaf thật render nội dung.
 *
 * Các test case KHÔNG nằm trong file này:
 *  - REC_TC04, TC05, TC06: chỉ là thẻ <a href> tĩnh trong template, không có
 *    handler phía server tương ứng để kiểm thử.
 *  - REC_TC07, TC34, TC40: hành vi JavaScript phía client (tab lọc, chip gợi ý,
 *    reset form) không chạm tới controller.
 *  - REC_TC11, TC12: phân quyền route "/receptionist/**" -> hasRole(RECEPTIONIST)
 *    nằm ở SecurityConfig (filter chain thật), cần kiểm thử tích hợp/security-test
 *    riêng, không phải unit test của một @Controller đơn lẻ.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReceptionistDashboardControllerUnitTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentStatusHistoryRepository appointmentStatusHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReceptionistService receptionistService;

    @Mock
    private ClinicSettingsService clinicSettingsService;

    private ReceptionistDashboardController controller;
    private MockMvc mockMvc;

    private static final String RECEPTIONIST_EMAIL = "reception@mediscan.com";
    private final Authentication receptionistAuth =
            new UsernamePasswordAuthenticationToken(RECEPTIONIST_EMAIL, null);

    private User doctorA;
    private User doctorB;

    @BeforeEach
    void setUp() {
        when(clinicSettingsService.getOpenTime()).thenReturn(LocalTime.of(7, 0));
        when(clinicSettingsService.getCloseTime()).thenReturn(LocalTime.of(17, 0));
        when(clinicSettingsService.getSlotMinutes()).thenReturn(30);
        when(clinicSettingsService.getMaxFutureBookingDays()).thenReturn(90);

        controller = new ReceptionistDashboardController(
                appointmentRepository, appointmentStatusHistoryRepository, userRepository,
                receptionistService, clinicSettingsService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        Role doctorRole = new Role();
        doctorRole.setRoleName("DOCTOR");

        doctorA = new User();
        doctorA.setUserId(2L);
        doctorA.setFullName("Doctor Nguyen Van A");
        doctorA.setStatus("ACTIVE");
        doctorA.setRole(doctorRole);

        doctorB = new User();
        doctorB.setUserId(7L);
        doctorB.setFullName("Ha Thai Son");
        doctorB.setStatus("ACTIVE");
        doctorB.setRole(doctorRole);
    }

    /** appointmentId la @GeneratedValue, khong co setter cong khai -> gan qua reflection cho test. */
    private static void setAppointmentId(Appointment appointment, Long id) {
        ReflectionTestUtils.setField(appointment, "appointmentId", id);
    }

    private Appointment appointment(Long id, String status, User doctor, LocalDateTime time) {
        Appointment a = new Appointment();
        setAppointmentId(a, id);
        a.setAppointmentCode("APT-2026-" + String.format("%05d", id));
        a.setStatus(status);
        a.setDoctor(doctor);
        a.setScheduledTime(time);
        return a;
    }

    // =========================================================================
    // 1. NHOM DASHBOARD (REC_TC01 -> REC_TC10)
    // =========================================================================

    @Test
    @DisplayName("REC_TC01: Xem dashboard thanh cong -> tra ve dung view va co du KPI card")
    void test_REC_TC01_Dashboard_ShouldReturnDashboardView() throws Exception {
        when(appointmentRepository.findByScheduledTimeBetweenOrderByScheduledTimeAsc(any(), any()))
                .thenReturn(List.of());
        when(userRepository.findByRoleRoleNameInAndStatusOrderByFullNameAsc(anyList(), eq("ACTIVE")))
                .thenReturn(List.of(doctorA, doctorB));

        mockMvc.perform(get("/receptionist/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("receptionist/dashboard"))
                .andExpect(model().attributeExists("todayCount", "waitingCheckinCount", "waitingCount",
                        "inProgressCount", "completedCount", "doctorWorkloads", "doctorsOnDutyCount"));
    }

    @Test
    @DisplayName("REC_TC02: KPI dashboard phan anh dung so luong lich hen theo tung trang thai")
    void test_REC_TC02_Dashboard_KpiCounts_ShouldMatchAppointmentStatuses() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        List<Appointment> today = List.of(
                appointment(1L, "PENDING", null, now),
                appointment(2L, "CONFIRMED", doctorA, now),
                appointment(3L, "CHECKED_IN", doctorA, now),
                appointment(4L, "IN_PROGRESS", doctorA, now),
                appointment(5L, "COMPLETED", doctorB, now)
        );
        when(appointmentRepository.findByScheduledTimeBetweenOrderByScheduledTimeAsc(any(), any())).thenReturn(today);
        when(userRepository.findByRoleRoleNameInAndStatusOrderByFullNameAsc(anyList(), eq("ACTIVE")))
                .thenReturn(List.of(doctorA, doctorB));

        mockMvc.perform(get("/receptionist/dashboard"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("todayCount", 5L))
                .andExpect(model().attribute("pendingCount", 1L))
                .andExpect(model().attribute("confirmedCount", 1L))
                .andExpect(model().attribute("waitingCount", 1L))
                .andExpect(model().attribute("inProgressCount", 1L))
                .andExpect(model().attribute("completedCount", 1L));
    }

    @Test
    @DisplayName("REC_TC03: The chia ca dem dung so lich hen theo khung gio sang/chieu/toi")
    void test_REC_TC03_Dashboard_ShiftBreakdown_ShouldCountByHourRange() throws Exception {
        LocalDate today = LocalDate.now();
        List<Appointment> appointments = List.of(
                appointment(1L, "CONFIRMED", doctorA, LocalDateTime.of(today, LocalTime.of(8, 0))),   // sang
                appointment(2L, "CONFIRMED", doctorA, LocalDateTime.of(today, LocalTime.of(14, 0))),  // chieu
                appointment(3L, "CONFIRMED", doctorB, LocalDateTime.of(today, LocalTime.of(18, 0)))   // toi
        );
        when(appointmentRepository.findByScheduledTimeBetweenOrderByScheduledTimeAsc(any(), any()))
                .thenReturn(appointments);
        when(userRepository.findByRoleRoleNameInAndStatusOrderByFullNameAsc(anyList(), eq("ACTIVE")))
                .thenReturn(List.of(doctorA, doctorB));

        mockMvc.perform(get("/receptionist/dashboard"))
                .andExpect(model().attribute("morningShiftCount", 1L))
                .andExpect(model().attribute("afternoonShiftCount", 1L))
                .andExpect(model().attribute("eveningShiftCount", 1L));
    }

    @Test
    @DisplayName("REC_TC08: Widget tai kham bac si khong hien thi trung lap (moi bac si dung 1 lan)")
    void test_REC_TC08_Dashboard_DoctorWorkload_ShouldHaveNoDuplicateEntries() throws Exception {
        when(appointmentRepository.findByScheduledTimeBetweenOrderByScheduledTimeAsc(any(), any()))
                .thenReturn(List.of());
        when(userRepository.findByRoleRoleNameInAndStatusOrderByFullNameAsc(anyList(), eq("ACTIVE")))
                .thenReturn(List.of(doctorA, doctorB));

        mockMvc.perform(get("/receptionist/dashboard"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("doctorWorkloads",
                        org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    @DisplayName("REC_TC09: Widget tai kham bac si hien thi dung chuyen khoa cua tung bac si")
    void test_REC_TC09_Dashboard_DoctorWorkload_ShouldShowCorrectSpecialty() throws Exception {
        when(appointmentRepository.findByScheduledTimeBetweenOrderByScheduledTimeAsc(any(), any()))
                .thenReturn(List.of());
        when(userRepository.findByRoleRoleNameInAndStatusOrderByFullNameAsc(anyList(), eq("ACTIVE")))
                .thenReturn(List.of(doctorA, doctorB));

        var result = mockMvc.perform(get("/receptionist/dashboard"))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        List<ReceptionistDashboardController.DoctorWorkloadDto> workloads =
                (List<ReceptionistDashboardController.DoctorWorkloadDto>)
                        result.getModelAndView().getModel().get("doctorWorkloads");

        var byDoctorId = workloads.stream()
                .collect(java.util.stream.Collectors.toMap(w -> w.getDoctor().getUserId(), w -> w.getSpecialty()));
        org.junit.jupiter.api.Assertions.assertEquals("Chấn thương chỉnh hình", byDoctorId.get(2L));
        org.junit.jupiter.api.Assertions.assertEquals("Cột sống - Cơ xương khớp", byDoctorId.get(7L));
    }

    @Test
    @DisplayName("REC_TC10: Dashboard khong co lich hen nao hom nay -> tat ca KPI = 0")
    void test_REC_TC10_Dashboard_ZeroAppointmentsToday_ShouldShowZeroCounts() throws Exception {
        when(appointmentRepository.findByScheduledTimeBetweenOrderByScheduledTimeAsc(any(), any()))
                .thenReturn(List.of());
        when(userRepository.findByRoleRoleNameInAndStatusOrderByFullNameAsc(anyList(), eq("ACTIVE")))
                .thenReturn(List.of());

        mockMvc.perform(get("/receptionist/dashboard"))
                .andExpect(model().attribute("todayCount", 0L))
                .andExpect(model().attribute("pendingCount", 0L))
                .andExpect(model().attribute("completedCount", 0L))
                .andExpect(model().attribute("todayAppointments", org.hamcrest.Matchers.empty()));
    }

    // =========================================================================
    // 2. NHOM TIEP NHAN & CHECK-IN / APPOINTMENT LIST (REC_TC13 -> REC_TC21)
    // =========================================================================

    @Test
    @DisplayName("REC_TC13: Xem danh sach lich hen thanh cong")
    void test_REC_TC13_AppointmentsList_ShouldReturnAppointmentsView() throws Exception {
        Page<Appointment> page = new PageImpl<>(List.of(appointment(1L, "PENDING", null, LocalDateTime.now())));
        when(appointmentRepository.searchAppointments(isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/receptionist/appointments"))
                .andExpect(status().isOk())
                .andExpect(view().name("receptionist/appointments"))
                .andExpect(model().attributeExists("appointmentsPage"));
    }

    @Test
    @DisplayName("REC_TC14: Tim theo tu khoa -> keyword duoc truyen dung xuong repository")
    void test_REC_TC14_AppointmentsList_SearchByKeyword_ShouldPassKeywordToRepository() throws Exception {
        Page<Appointment> page = new PageImpl<>(List.of());
        when(appointmentRepository.searchAppointments(eq("0912345678"), isNull(), isNull(), isNull(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/receptionist/appointments").param("keyword", "0912345678"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("keyword", "0912345678"));

        verify(appointmentRepository).searchAppointments(eq("0912345678"), isNull(), isNull(), isNull(), any());
    }

    @Test
    @DisplayName("REC_TC15: Loc theo trang thai -> status duoc truyen dung xuong repository")
    void test_REC_TC15_AppointmentsList_FilterByStatus_ShouldPassStatusToRepository() throws Exception {
        Page<Appointment> page = new PageImpl<>(List.of());
        when(appointmentRepository.searchAppointments(isNull(), isNull(), isNull(), eq("CONFIRMED"), any()))
                .thenReturn(page);

        mockMvc.perform(get("/receptionist/appointments").param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedStatus", "CONFIRMED"));
    }

    @Test
    @DisplayName("REC_TC16: Dat lai bo loc -> khong truyen keyword/status, tra ve chuoi rong")
    void test_REC_TC16_AppointmentsList_Reset_ShouldClearFilters() throws Exception {
        Page<Appointment> page = new PageImpl<>(List.of());
        when(appointmentRepository.searchAppointments(isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/receptionist/appointments"))
                .andExpect(model().attribute("keyword", ""))
                .andExpect(model().attribute("selectedStatus", ""));
    }

    @Test
    @DisplayName("REC_TC20: Phan trang -> tham so page duoc chuyen thanh dung PageRequest")
    void test_REC_TC20_AppointmentsList_Pagination_ShouldRequestCorrectPage() throws Exception {
        Page<Appointment> page = new PageImpl<>(List.of(), PageRequest.of(2, 10), 25);
        when(appointmentRepository.searchAppointments(isNull(), isNull(), isNull(), isNull(),
                eq(PageRequest.of(2, 10)))).thenReturn(page);

        mockMvc.perform(get("/receptionist/appointments").param("page", "2"))
                .andExpect(status().isOk());

        verify(appointmentRepository).searchAppointments(isNull(), isNull(), isNull(), isNull(),
                eq(PageRequest.of(2, 10)));
    }

    @Test
    @DisplayName("REC_TC21/TC22: Mo chi tiet dung lich hen theo id")
    void test_REC_TC21_AppointmentDetail_ShouldOpenCorrectAppointment() throws Exception {
        Appointment apt = appointment(9L, "CONFIRMED", doctorA, LocalDateTime.now());
        when(appointmentRepository.findById(9L)).thenReturn(Optional.of(apt));
        when(appointmentStatusHistoryRepository.findByAppointmentOrderByCreatedAtAsc(apt)).thenReturn(List.of());
        when(userRepository.findByRoleRoleNameInAndStatusOrderByFullNameAsc(anyList(), eq("ACTIVE")))
                .thenReturn(List.of(doctorA, doctorB));

        mockMvc.perform(get("/receptionist/appointments/{id}", 9L))
                .andExpect(status().isOk())
                .andExpect(view().name("receptionist/appointment-detail"))
                .andExpect(model().attribute("appointment", apt));
    }

    @Test
    @DisplayName("Bo sung: Mo chi tiet lich hen khong ton tai -> redirect ve danh sach kem loi")
    void test_AppointmentDetail_NotFound_ShouldRedirectWithError() throws Exception {
        when(appointmentRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/receptionist/appointments/{id}", 999L))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/receptionist/appointments"))
                .andExpect(flash().attribute("error", "Không tìm thấy lịch hẹn."));
    }

    // =========================================================================
    // 3. NHOM HANH DONG TREN LICH HEN (REC_TC17, TC19, TC23, TC24, TC26, TC28)
    // =========================================================================

    @Test
    @DisplayName("REC_TC17 (view): Xac nhan thanh cong -> redirect va flash success")
    void test_ConfirmAppointment_Success_ShouldRedirectWithSuccessFlash() throws Exception {
        when(receptionistService.confirmAppointment(1L, RECEPTIONIST_EMAIL)).thenReturn(new Appointment());

        mockMvc.perform(post("/receptionist/appointments/{id}/confirm", 1L).principal(receptionistAuth))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/receptionist/appointments"))
                .andExpect(flash().attribute("success", "Đã xác nhận lịch hẹn."));
    }

    @Test
    @DisplayName("REC_TC24 (view): Xac nhan trung lich bac si -> redirect va flash conflictError rieng biet")
    void test_ConfirmAppointment_Conflict_ShouldRedirectWithConflictFlash() throws Exception {
        when(receptionistService.confirmAppointment(2L, RECEPTIONIST_EMAIL))
                .thenThrow(new DoctorScheduleConflictException("Trùng lịch: BS. A đã có lịch hẹn lúc 09:00"));

        mockMvc.perform(post("/receptionist/appointments/{id}/confirm", 2L).principal(receptionistAuth))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("conflictError", "Trùng lịch: BS. A đã có lịch hẹn lúc 09:00"))
                .andExpect(flash().attributeCount(1));
    }

    @Test
    @DisplayName("An toan: redirectTo tro ra ngoai '/receptionist/**' phai bi bo qua (chan open-redirect)")
    void test_ConfirmAppointment_MaliciousRedirectTo_ShouldFallbackToDefault() throws Exception {
        when(receptionistService.confirmAppointment(3L, RECEPTIONIST_EMAIL)).thenReturn(new Appointment());

        mockMvc.perform(post("/receptionist/appointments/{id}/confirm", 3L)
                        .principal(receptionistAuth)
                        .param("redirectTo", "https://evil.example.com/phish"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/receptionist/appointments"));
    }

    @Test
    @DisplayName("An toan: redirectTo hop le trong '/receptionist/**' duoc giu nguyen")
    void test_ConfirmAppointment_ValidRedirectTo_ShouldBeHonored() throws Exception {
        when(receptionistService.confirmAppointment(4L, RECEPTIONIST_EMAIL)).thenReturn(new Appointment());

        mockMvc.perform(post("/receptionist/appointments/{id}/confirm", 4L)
                        .principal(receptionistAuth)
                        .param("redirectTo", "/receptionist/waiting"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/receptionist/waiting"));
    }

    @Test
    @DisplayName("REC_TC23 (view): Gan bac si thanh cong -> redirect ve trang chi tiet chinh lich hen do")
    void test_AssignDoctor_Success_ShouldRedirectToAppointmentDetail() throws Exception {
        when(receptionistService.assignDoctor(eq(5L), eq(2L), any(), eq(RECEPTIONIST_EMAIL)))
                .thenReturn(new Appointment());

        mockMvc.perform(post("/receptionist/appointments/{id}/assign-doctor", 5L)
                        .principal(receptionistAuth)
                        .param("doctorId", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/receptionist/appointments/5"))
                .andExpect(flash().attribute("success", "Đã điều hướng bệnh nhân đến bác sĩ."));
    }

    @Test
    @DisplayName("REC_TC24 (view): Gan bac si trung lich -> flash conflictError, van redirect ve chi tiet")
    void test_AssignDoctor_Conflict_ShouldRedirectWithConflictFlash() throws Exception {
        when(receptionistService.assignDoctor(eq(6L), eq(2L), any(), eq(RECEPTIONIST_EMAIL)))
                .thenThrow(new DoctorScheduleConflictException("Trùng lịch bác sĩ"));

        mockMvc.perform(post("/receptionist/appointments/{id}/assign-doctor", 6L)
                        .principal(receptionistAuth)
                        .param("doctorId", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/receptionist/appointments/6"))
                .andExpect(flash().attribute("conflictError", "Trùng lịch bác sĩ"));
    }

    @Test
    @DisplayName("REC_TC26 (view): Huy lich hen -> redirect va flash success")
    void test_CancelAppointment_ShouldRedirectWithSuccessFlash() throws Exception {
        when(receptionistService.cancelAppointment(eq(7L), any(), eq(RECEPTIONIST_EMAIL)))
                .thenReturn(new Appointment());

        mockMvc.perform(post("/receptionist/appointments/{id}/cancel", 7L)
                        .principal(receptionistAuth)
                        .param("reason", "Bệnh nhân đổi lịch"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("success", "Đã hủy lịch hẹn."));
    }

    @Test
    @DisplayName("REC_TC27 (view): Huy lich hen da ket thuc -> flash error, khong crash 500")
    void test_CancelAppointment_TerminalStatus_ShouldRedirectWithErrorFlash() throws Exception {
        when(receptionistService.cancelAppointment(eq(8L), any(), eq(RECEPTIONIST_EMAIL)))
                .thenThrow(new InvalidFieldException("Lịch hẹn đã kết thúc, không thể hủy."));

        mockMvc.perform(post("/receptionist/appointments/{id}/cancel", 8L).principal(receptionistAuth))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", "Lịch hẹn đã kết thúc, không thể hủy."));
    }

    @Test
    @DisplayName("REC_TC28 (view): Danh dau vang mat -> redirect va flash success")
    void test_MarkMissed_ShouldRedirectWithSuccessFlash() throws Exception {
        when(receptionistService.markMissed(9L, RECEPTIONIST_EMAIL)).thenReturn(new Appointment());

        mockMvc.perform(post("/receptionist/appointments/{id}/missed", 9L).principal(receptionistAuth))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("success", "Đã đánh dấu bệnh nhân vắng mặt."));
    }

    // =========================================================================
    // 4. NHOM TAO LICH MOI / WALK-IN (REC_TC30, TC31, TC32, TC33, TC37, TC39)
    // =========================================================================

    @Test
    @DisplayName("REC_TC30: Xem form dang ky walk-in -> du cac model attribute can cho form")
    void test_REC_TC30_NewWalkInForm_ShouldReturnFormViewWithModelAttributes() throws Exception {
        when(userRepository.findByRoleRoleNameInAndStatusOrderByFullNameAsc(anyList(), eq("ACTIVE")))
                .thenReturn(List.of(doctorA, doctorB));
        when(appointmentRepository.findByScheduledTimeBetweenOrderByScheduledTimeAsc(any(), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/receptionist/appointments/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("receptionist/new-appointment"))
                .andExpect(model().attributeExists("doctorsOnDuty", "doctorWorkloads", "timeSlots",
                        "clinicOpenHour", "clinicCloseHour", "slotMinutes", "maxFutureBookingDays"));
    }

    @Test
    @DisplayName("REC_TC39 (view): Widget bac si o man hinh walk-in hien thi dung chuyen khoa")
    void test_REC_TC39_NewWalkInForm_DoctorWorkload_ShouldShowCorrectSpecialty() throws Exception {
        when(userRepository.findByRoleRoleNameInAndStatusOrderByFullNameAsc(anyList(), eq("ACTIVE")))
                .thenReturn(List.of(doctorA, doctorB));
        when(appointmentRepository.findByScheduledTimeBetweenOrderByScheduledTimeAsc(any(), any()))
                .thenReturn(List.of());

        var result = mockMvc.perform(get("/receptionist/appointments/new"))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        List<ReceptionistDashboardController.DoctorWorkloadDto> workloads =
                (List<ReceptionistDashboardController.DoctorWorkloadDto>)
                        result.getModelAndView().getModel().get("doctorWorkloads");
        var byDoctorId = workloads.stream()
                .collect(java.util.stream.Collectors.toMap(w -> w.getDoctor().getUserId(), w -> w.getSpecialty()));
        org.junit.jupiter.api.Assertions.assertEquals("Chấn thương chỉnh hình", byDoctorId.get(2L));
        org.junit.jupiter.api.Assertions.assertEquals("Cột sống - Cơ xương khớp", byDoctorId.get(7L));
    }

    @Test
    @DisplayName("REC_TC31 (view): Dang ky walk-in thanh cong -> redirect va flash success chua ma lich hen")
    void test_CreateWalkInAppointment_Success_ShouldRedirectWithSuccessFlashContainingCode() throws Exception {
        Appointment created = appointment(50L, "CONFIRMED", doctorA, LocalDateTime.now());
        when(receptionistService.createWalkInAppointment(
                eq("Nguyen Van An"), eq("0912345678"), any(), any(), any(), any(), any(), any(), eq(RECEPTIONIST_EMAIL)))
                .thenReturn(created);

        mockMvc.perform(post("/receptionist/appointments/walk-in")
                        .principal(receptionistAuth)
                        .param("fullName", "Nguyen Van An")
                        .param("phone", "0912345678")
                        .param("gender", "MALE")
                        .param("doctorId", "2")
                        .param("scheduledTime", "09:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/receptionist/appointments/new"))
                .andExpect(flash().attribute("success",
                        "Đã đăng ký lịch hẹn " + created.getAppointmentCode() + " cho khách vãng lai."));
    }

    @Test
    @DisplayName("REC_TC32/TC33 (view): Loi validation -> flash error va giu lai du lieu da nhap (backfill)")
    void test_CreateWalkInAppointment_ValidationError_ShouldBackfillFormFields() throws Exception {
        when(receptionistService.createWalkInAppointment(
                eq("Nguyen123!"), any(), any(), any(), any(), any(), any(), any(), eq(RECEPTIONIST_EMAIL)))
                .thenThrow(new InvalidFieldException("Họ và tên không hợp lệ."));

        mockMvc.perform(post("/receptionist/appointments/walk-in")
                        .principal(receptionistAuth)
                        .param("fullName", "Nguyen123!")
                        .param("phone", "0912345678"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/receptionist/appointments/new"))
                .andExpect(flash().attribute("error", "Họ và tên không hợp lệ."))
                .andExpect(flash().attribute("formFullName", "Nguyen123!"))
                .andExpect(flash().attribute("formPhone", "0912345678"));
    }

    @Test
    @DisplayName("REC_TC37 (view): Trung lich bac si -> flash conflictError rieng va van backfill form")
    void test_CreateWalkInAppointment_Conflict_ShouldBackfillFormFieldsWithConflictFlash() throws Exception {
        when(receptionistService.createWalkInAppointment(
                eq("Nguyen Van An"), any(), any(), any(), any(), any(), any(), any(), eq(RECEPTIONIST_EMAIL)))
                .thenThrow(new DoctorScheduleConflictException("Trùng Lịch Khám Bác Sĩ"));

        mockMvc.perform(post("/receptionist/appointments/walk-in")
                        .principal(receptionistAuth)
                        .param("fullName", "Nguyen Van An")
                        .param("phone", "0912345678")
                        .param("doctorId", "2")
                        .param("scheduledTime", "09:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("conflictError", "Trùng Lịch Khám Bác Sĩ"))
                .andExpect(flash().attribute("formDoctorId", 2L));
    }

    // =========================================================================
    // 5. NHOM DANH SACH CHO (REC_TC41, TC45, TC46)
    // =========================================================================

    @Test
    @DisplayName("REC_TC41: Xem hang cho -> tra ve dung view va danh sach cho theo thu tu queueNumber")
    void test_REC_TC41_WaitingList_ShouldReturnWaitingViewWithQueue() throws Exception {
        List<Appointment> waiting = List.of(appointment(60L, "CHECKED_IN", doctorA, LocalDateTime.now()));
        when(appointmentRepository.findByStatusOrderByQueueNumberAsc("CHECKED_IN")).thenReturn(waiting);

        mockMvc.perform(get("/receptionist/waiting"))
                .andExpect(status().isOk())
                .andExpect(view().name("receptionist/waiting"))
                .andExpect(model().attribute("waitingList", waiting));
    }

    @Test
    @DisplayName("REC_TC46: Hang cho rong -> model attribute la danh sach rong (template tu hien thi empty state)")
    void test_REC_TC46_WaitingList_Empty_ShouldReturnEmptyList() throws Exception {
        when(appointmentRepository.findByStatusOrderByQueueNumberAsc("CHECKED_IN")).thenReturn(List.of());

        mockMvc.perform(get("/receptionist/waiting"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("waitingList", org.hamcrest.Matchers.empty()));
    }

    @Test
    @DisplayName("REC_TC43 (view): Goi so tiep theo thanh cong -> redirect ve waiting kem ten & ma benh nhan")
    void test_CallNextPatient_Success_ShouldRedirectToWaitingWithSuccessFlash() throws Exception {
        Appointment claimed = appointment(61L, "IN_PROGRESS", doctorA, LocalDateTime.now());
        Patient patient = new Patient();
        patient.setFullName("Nguyen Van A");
        claimed.setPatient(patient);
        when(receptionistService.callNextPatient(RECEPTIONIST_EMAIL)).thenReturn(claimed);

        mockMvc.perform(post("/receptionist/appointments/call-next").principal(receptionistAuth))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/receptionist/waiting"))
                .andExpect(flash().attribute("success",
                        "Đã gọi Nguyen Van A (" + claimed.getAppointmentCode() + ") vào phòng khám."));
    }

    @Test
    @DisplayName("REC_TC45 (view): Goi so tiep theo khi hang cho rong -> flash error, khong crash")
    void test_CallNextPatient_EmptyQueue_ShouldRedirectWithErrorFlash() throws Exception {
        when(receptionistService.callNextPatient(RECEPTIONIST_EMAIL))
                .thenThrow(new InvalidFieldException("Không có bệnh nhân nào đang chờ."));

        mockMvc.perform(post("/receptionist/appointments/call-next").principal(receptionistAuth))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/receptionist/waiting"))
                .andExpect(flash().attribute("error", "Không có bệnh nhân nào đang chờ."));
    }

    // =========================================================================
    // 6. NHOM LICH BAC SI THEO NGAY (REC_TC47 -> REC_TC49)
    // =========================================================================

    @Test
    @DisplayName("REC_TC47: Xem lich theo ngay duoc chon -> hien dung slot cua tung bac si trong ngay do")
    void test_REC_TC47_Schedule_SelectedDate_ShouldBuildScheduleRowsForThatDate() throws Exception {
        LocalDate selected = LocalDate.now().plusDays(1);
        List<Appointment> dayAppointments = List.of(
                appointment(70L, "CONFIRMED", doctorA, LocalDateTime.of(selected, LocalTime.of(9, 0))));
        when(userRepository.findByRoleRoleNameInAndStatusOrderByFullNameAsc(anyList(), eq("ACTIVE")))
                .thenReturn(List.of(doctorA, doctorB));
        when(appointmentRepository.findByScheduledTimeBetweenOrderByScheduledTimeAsc(any(), any()))
                .thenReturn(dayAppointments);

        mockMvc.perform(get("/receptionist/schedule").param("date", selected.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("receptionist/schedule"))
                .andExpect(model().attribute("selectedDate", selected))
                .andExpect(model().attribute("scheduleRows", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    @DisplayName("REC_TC48: Doi ngay xem -> truy van lai dung khoang thoi gian cua ngay moi")
    void test_REC_TC48_Schedule_DifferentDate_ShouldQueryNewDateRange() throws Exception {
        LocalDate day1 = LocalDate.now();
        LocalDate day2 = LocalDate.now().plusDays(3);
        when(userRepository.findByRoleRoleNameInAndStatusOrderByFullNameAsc(anyList(), eq("ACTIVE")))
                .thenReturn(List.of(doctorA));
        when(appointmentRepository.findByScheduledTimeBetweenOrderByScheduledTimeAsc(any(), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/receptionist/schedule").param("date", day2.toString()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedDate", day2));

        verify(appointmentRepository).findByScheduledTimeBetweenOrderByScheduledTimeAsc(
                day2.atStartOfDay(), day2.plusDays(1).atStartOfDay());
        verify(appointmentRepository, never()).findByScheduledTimeBetweenOrderByScheduledTimeAsc(
                day1.atStartOfDay(), day1.plusDays(1).atStartOfDay());
    }

    @Test
    @DisplayName("REC_TC49: Man hinh lich chi de xem, khong co API thay doi du lieu (DTO khong co setter)")
    void test_REC_TC49_Schedule_DtosAreReadOnly() {
        long mutatingMethods = java.util.Arrays.stream(
                        ReceptionistDashboardController.ScheduleSlot.class.getDeclaredMethods())
                .filter(m -> m.getName().startsWith("set"))
                .count();
        org.junit.jupiter.api.Assertions.assertEquals(0, mutatingMethods,
                "ScheduleSlot chỉ nên là DTO hiển thị, không có setter làm thay đổi dữ liệu lịch hẹn/bác sĩ");
    }

    // =========================================================================
    // 7. NHOM BAC SI TRUC (REC_TC50 -> REC_TC52)
    // =========================================================================

    @Test
    @DisplayName("REC_TC50: Xem danh sach bac si truc thanh cong")
    void test_REC_TC50_DoctorsOnDuty_ShouldReturnDoctorsView() throws Exception {
        when(userRepository.findByRoleRoleNameInAndStatusOrderByFullNameAsc(anyList(), eq("ACTIVE")))
                .thenReturn(List.of(doctorA, doctorB));

        mockMvc.perform(get("/receptionist/doctors"))
                .andExpect(status().isOk())
                .andExpect(view().name("receptionist/doctors"))
                .andExpect(model().attribute("doctorsOnDuty", List.of(doctorA, doctorB)));
    }

    @Test
    @DisplayName("REC_TC51: So luong badge bac si khop dung voi so luong bac si ACTIVE thuc te, khong trung lap")
    void test_REC_TC51_DoctorsOnDuty_CountShouldMatchActiveDoctorCountNoDuplicates() throws Exception {
        when(userRepository.findByRoleRoleNameInAndStatusOrderByFullNameAsc(anyList(), eq("ACTIVE")))
                .thenReturn(List.of(doctorA, doctorB));

        var result = mockMvc.perform(get("/receptionist/doctors"))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        List<User> doctorsOnDuty = (List<User>) result.getModelAndView().getModel().get("doctorsOnDuty");
        long distinctIds = doctorsOnDuty.stream().map(User::getUserId).distinct().count();

        org.junit.jupiter.api.Assertions.assertEquals(2, doctorsOnDuty.size());
        org.junit.jupiter.api.Assertions.assertEquals(distinctIds, doctorsOnDuty.size(),
                "Danh sách bác sĩ trực không được chứa tài khoản trùng lặp");
    }

    @Test
    @DisplayName("REC_TC52: Khong co bac si nao dang hoat dong -> danh sach rong")
    void test_REC_TC52_DoctorsOnDuty_Empty_ShouldReturnEmptyList() throws Exception {
        when(userRepository.findByRoleRoleNameInAndStatusOrderByFullNameAsc(anyList(), eq("ACTIVE")))
                .thenReturn(List.of());

        mockMvc.perform(get("/receptionist/doctors"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("doctorsOnDuty", org.hamcrest.Matchers.empty()));
    }
}
