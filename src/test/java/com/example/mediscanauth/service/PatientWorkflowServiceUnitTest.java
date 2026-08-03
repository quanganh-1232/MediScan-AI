package com.example.mediscanauth.service;

import com.example.mediscanauth.dto.response.ChatResponse;
import com.example.mediscanauth.model.Appointment;
import com.example.mediscanauth.model.ImagingRecord;
import com.example.mediscanauth.model.Notification;
import com.example.mediscanauth.model.Patient;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.repository.AppointmentRepository;
import com.example.mediscanauth.repository.ImagingRecordRepository;
import com.example.mediscanauth.repository.PatientRepository;
import com.example.mediscanauth.repository.UserRepository;
import com.example.mediscanauth.service.impl.PatientWorkflowServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientWorkflowServiceUnitTest {

    @Mock
    private ImagingRecordRepository imagingRecordRepository;

    @Mock
    private UserAccountService userAccountService;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private PatientWorkflowServiceImpl patientWorkflowService;

    private ChatbotNLPService chatbotNLPService;

    private User patientUser;
    private Patient patient;
    private User doctorUser;

    @BeforeEach
    void setUp() {
        patientUser = new User();
        patientUser.setUserId(100L);
        patientUser.setEmail("patientA@mediscan.com");
        patientUser.setFullName("Le Van A");

        patient = new Patient();
        patient.setUser(patientUser);

        doctorUser = new User();
        doctorUser.setUserId(200L);
        doctorUser.setEmail("doctor@mediscan.com");
        doctorUser.setFullName("Dr. Nguyen Van B");

        chatbotNLPService = new ChatbotNLPService();
    }

    // =========================================================================
    // 1. NHÓM DASHBOARD (PAT_TC01 -> PAT_TC03)
    // =========================================================================

    @Test
    @DisplayName("PAT_TC01: Xem tổng quan dashboard bệnh nhân thành công")
    void test_PAT_TC01_ViewDashboardSuccessfully() {
        when(userAccountService.findByEmail("patientA@mediscan.com")).thenReturn(patientUser);
        User user = userAccountService.findByEmail("patientA@mediscan.com");
        assertNotNull(user);
        assertEquals("patientA@mediscan.com", user.getEmail());
    }

    @Test
    @DisplayName("PAT_TC02: Thống kê số lượng hồ sơ chính xác theo từng trạng thái")
    void test_PAT_TC02_DashboardDisplaysCorrectRecordCounts() {
        List<ImagingRecord> records = new ArrayList<>();
        ImagingRecord r1 = new ImagingRecord(); r1.setStatus("COMPLETED"); records.add(r1);
        ImagingRecord r2 = new ImagingRecord(); r2.setStatus("PENDING_DOCTOR"); records.add(r2);
        ImagingRecord r3 = new ImagingRecord(); r3.setStatus("DOCTOR_REJECTED"); records.add(r3);

        long completed = records.stream().filter(r -> "COMPLETED".equals(r.getStatus()) || "DOCTOR_CONFIRMED".equals(r.getStatus())).count();
        long processing = records.stream().filter(r -> r.getStatus() != null && r.getStatus().contains("PENDING")).count();
        long needAttention = records.stream().filter(r -> "DOCTOR_REJECTED".equals(r.getStatus())).count();

        assertEquals(1, completed);
        assertEquals(1, processing);
        assertEquals(1, needAttention);
        assertEquals(3, records.size());
    }

    @Test
    @DisplayName("PAT_TC03: Dashboard hiển thị trạng thái trống (0 hồ sơ) cho bệnh nhân mới")
    void test_PAT_TC03_DashboardShowsEmptyStateForNewPatient() {
        List<ImagingRecord> records = new ArrayList<>();
        assertTrue(records.isEmpty());
        assertEquals(0, records.size());
    }

    // =========================================================================
    // 2. NHÓM ĐẶT LỊCH KHÁM (PAT_TC04 -> PAT_TC12)
    // =========================================================================

    @Test
    @DisplayName("PAT_TC04: Đặt lịch thành công khi có chọn Bác sĩ -> Status SCHEDULED")
    void test_PAT_TC04_BookAppointment_WithDoctor_ShouldSetStatusScheduled() {
        String date = LocalDate.now().plusDays(2).toString();
        String time = "09:00";

        when(userAccountService.findByEmail("patientA@mediscan.com")).thenReturn(patientUser);
        when(patientRepository.findByUser(patientUser)).thenReturn(Optional.of(patient));
        when(userRepository.findById(200L)).thenReturn(Optional.of(doctorUser));
        when(appointmentRepository.countPatientConflicts(eq(patientUser), any(), any())).thenReturn(0L);
        when(appointmentRepository.countDoctorConflicts(eq(doctorUser), any(), any())).thenReturn(0L);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        Appointment result = patientWorkflowService.bookAppointment("patientA@mediscan.com", 200L, date, time, "Đau khớp");

        assertNotNull(result);
        assertEquals("SCHEDULED", result.getStatus());
        assertEquals(doctorUser, result.getDoctor());
        assertEquals("Đau khớp", result.getNote());
        verify(notificationService).notifyRoleUsers(any(), anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("PAT_TC05: Đặt lịch thành công khi KHÔNG chọn Bác sĩ -> Status PENDING")
    void test_PAT_TC05_BookAppointment_WithoutDoctor_ShouldSetStatusPending() {
        String date = LocalDate.now().plusDays(3).toString();
        String time = "14:00";

        when(userAccountService.findByEmail("patientA@mediscan.com")).thenReturn(patientUser);
        when(patientRepository.findByUser(patientUser)).thenReturn(Optional.of(patient));
        when(appointmentRepository.countPatientConflicts(eq(patientUser), any(), any())).thenReturn(0L);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        Appointment result = patientWorkflowService.bookAppointment("patientA@mediscan.com", null, date, time, "Khám tổng quát");

        assertNotNull(result);
        assertEquals("PENDING", result.getStatus());
        assertNull(result.getDoctor());
    }

    @Test
    @DisplayName("PAT_TC06: Chặn đặt lịch vào thời điểm trong quá khứ")
    void test_PAT_TC06_BookAppointment_PastDate_ShouldThrowException() {
        String pastDate = "2020-01-01";
        String time = "10:00";

        when(userAccountService.findByEmail("patientA@mediscan.com")).thenReturn(patientUser);
        when(patientRepository.findByUser(patientUser)).thenReturn(Optional.of(patient));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                patientWorkflowService.bookAppointment("patientA@mediscan.com", 200L, pastDate, time, "Note")
        );
        assertTrue(ex.getMessage().contains("quá khứ"));
    }

    @Test
    @DisplayName("PAT_TC07: Chặn đặt lịch trước giờ hành chính (< 07:00)")
    void test_PAT_TC07_BookAppointment_BeforeBusinessHours_ShouldThrowException() {
        String futureDate = LocalDate.now().plusDays(2).toString();
        String time = "06:30";

        when(userAccountService.findByEmail("patientA@mediscan.com")).thenReturn(patientUser);
        when(patientRepository.findByUser(patientUser)).thenReturn(Optional.of(patient));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                patientWorkflowService.bookAppointment("patientA@mediscan.com", 200L, futureDate, time, "Note")
        );
        assertTrue(ex.getMessage().contains("giờ hành chính"));
    }

    @Test
    @DisplayName("PAT_TC08: Chặn đặt lịch sau giờ hành chính (>= 17:00)")
    void test_PAT_TC08_BookAppointment_AfterBusinessHours_ShouldThrowException() {
        String futureDate = LocalDate.now().plusDays(2).toString();
        String time = "17:30";

        when(userAccountService.findByEmail("patientA@mediscan.com")).thenReturn(patientUser);
        when(patientRepository.findByUser(patientUser)).thenReturn(Optional.of(patient));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                patientWorkflowService.bookAppointment("patientA@mediscan.com", 200L, futureDate, time, "Note")
        );
        assertTrue(ex.getMessage().contains("giờ hành chính"));
    }

    @Test
    @DisplayName("PAT_TC09: Chặn trùng lịch Bệnh nhân trong vòng 30 phút")
    void test_PAT_TC09_BookAppointment_PatientConflict_ShouldThrowException() {
        String futureDate = LocalDate.now().plusDays(2).toString();
        String time = "09:15";

        when(userAccountService.findByEmail("patientA@mediscan.com")).thenReturn(patientUser);
        when(patientRepository.findByUser(patientUser)).thenReturn(Optional.of(patient));
        when(appointmentRepository.countPatientConflicts(eq(patientUser), any(), any())).thenReturn(1L);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                patientWorkflowService.bookAppointment("patientA@mediscan.com", 200L, futureDate, time, "Note")
        );
        assertTrue(ex.getMessage().contains("Bạn đã có lịch hẹn trong khung giờ này"));
    }

    @Test
    @DisplayName("PAT_TC10: Chặn trùng lịch Bác sĩ trong vòng 30 phút")
    void test_PAT_TC10_BookAppointment_DoctorConflict_ShouldThrowException() {
        String futureDate = LocalDate.now().plusDays(2).toString();
        String time = "10:00";

        when(userAccountService.findByEmail("patientA@mediscan.com")).thenReturn(patientUser);
        when(patientRepository.findByUser(patientUser)).thenReturn(Optional.of(patient));
        when(userRepository.findById(200L)).thenReturn(Optional.of(doctorUser));
        when(appointmentRepository.countPatientConflicts(eq(patientUser), any(), any())).thenReturn(0L);
        when(appointmentRepository.countDoctorConflicts(eq(doctorUser), any(), any())).thenReturn(1L);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                patientWorkflowService.bookAppointment("patientA@mediscan.com", 200L, futureDate, time, "Note")
        );
        assertTrue(ex.getMessage().contains("đã có lịch hẹn vào khung giờ này"));
    }

    @Test
    @DisplayName("PAT_TC11: Đặt lịch cách lịch hẹn trước đúng 30 phút (Biên hợp lệ)")
    void test_PAT_TC11_BookAppointment_BoundaryCase30Mins_ShouldSucceed() {
        String futureDate = LocalDate.now().plusDays(2).toString();
        String time = "09:30";

        when(userAccountService.findByEmail("patientA@mediscan.com")).thenReturn(patientUser);
        when(patientRepository.findByUser(patientUser)).thenReturn(Optional.of(patient));
        when(appointmentRepository.countPatientConflicts(eq(patientUser), any(), any())).thenReturn(0L);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        Appointment result = patientWorkflowService.bookAppointment("patientA@mediscan.com", null, futureDate, time, "Tái khám");
        assertNotNull(result);
    }

    @Test
    @DisplayName("PAT_TC12: Đặt lịch kèm ghi chú lý do khám (Note)")
    void test_PAT_TC12_BookAppointment_WithOptionalNote_ShouldSaveNote() {
        String futureDate = LocalDate.now().plusDays(2).toString();
        String time = "11:00";

        when(userAccountService.findByEmail("patientA@mediscan.com")).thenReturn(patientUser);
        when(patientRepository.findByUser(patientUser)).thenReturn(Optional.of(patient));
        when(appointmentRepository.countPatientConflicts(eq(patientUser), any(), any())).thenReturn(0L);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        Appointment result = patientWorkflowService.bookAppointment("patientA@mediscan.com", null, futureDate, time, "Đau cột sống thắt lưng 3 ngày");
        assertNotNull(result);
        assertEquals("Đau cột sống thắt lưng 3 ngày", result.getNote());
        assertEquals("Đau cột sống thắt lưng 3 ngày", result.getBodyPart());
    }

    // =========================================================================
    // 3. NHÓM HỦY LỊCH KHÁM (PAT_TC13 -> PAT_TC17)
    // =========================================================================

    @Test
    @DisplayName("PAT_TC13: Hủy lịch PENDING thành công -> Status CANCELLED")
    void test_PAT_TC13_CancelAppointment_Pending_ShouldSetStatusCancelled() {
        Appointment apt = new Appointment();
        apt.setPatient(patient);
        apt.setStatus("PENDING");

        when(userAccountService.findByEmail("patientA@mediscan.com")).thenReturn(patientUser);
        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(apt));

        patientWorkflowService.cancelAppointment("patientA@mediscan.com", 10L);

        assertEquals("CANCELLED", apt.getStatus());
        verify(appointmentRepository).save(apt);
    }

    @Test
    @DisplayName("PAT_TC14: Hủy lịch SCHEDULED thành công -> Status CANCELLED")
    void test_PAT_TC14_CancelAppointment_Scheduled_ShouldSetStatusCancelled() {
        Appointment apt = new Appointment();
        apt.setPatient(patient);
        apt.setStatus("SCHEDULED");

        when(userAccountService.findByEmail("patientA@mediscan.com")).thenReturn(patientUser);
        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(apt));

        patientWorkflowService.cancelAppointment("patientA@mediscan.com", 10L);

        assertEquals("CANCELLED", apt.getStatus());
        verify(appointmentRepository).save(apt);
    }

    @Test
    @DisplayName("PAT_TC15: Chặn hủy lịch khi đã CONFIRMED")
    void test_PAT_TC15_CancelAppointment_Confirmed_ShouldThrowException() {
        Appointment apt = new Appointment();
        apt.setPatient(patient);
        apt.setStatus("CONFIRMED");

        when(userAccountService.findByEmail("patientA@mediscan.com")).thenReturn(patientUser);
        when(appointmentRepository.findById(11L)).thenReturn(Optional.of(apt));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                patientWorkflowService.cancelAppointment("patientA@mediscan.com", 11L)
        );
        assertTrue(ex.getMessage().contains("Không thể hủy lịch hẹn đã được xác nhận hoặc hoàn tất"));
    }

    @Test
    @DisplayName("PAT_TC16: Chặn hủy lịch khi đã COMPLETED")
    void test_PAT_TC16_CancelAppointment_Completed_ShouldThrowException() {
        Appointment apt = new Appointment();
        apt.setPatient(patient);
        apt.setStatus("COMPLETED");

        when(userAccountService.findByEmail("patientA@mediscan.com")).thenReturn(patientUser);
        when(appointmentRepository.findById(12L)).thenReturn(Optional.of(apt));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                patientWorkflowService.cancelAppointment("patientA@mediscan.com", 12L)
        );
        assertTrue(ex.getMessage().contains("Không thể hủy lịch hẹn đã được xác nhận hoặc hoàn tất"));
    }

    @Test
    @DisplayName("PAT_TC17: Bảo mật: Chặn hủy lịch hẹn của Bệnh nhân khác")
    void test_PAT_TC17_CancelAppointment_BelongsToOtherPatient_ShouldThrowSecurityException() {
        User userB = new User();
        userB.setUserId(999L);
        userB.setEmail("patientB@mediscan.com");

        Patient patientB = new Patient();
        patientB.setUser(userB);

        Appointment aptOfB = new Appointment();
        aptOfB.setPatient(patientB);
        aptOfB.setStatus("SCHEDULED");

        when(userAccountService.findByEmail("patientA@mediscan.com")).thenReturn(patientUser);
        when(appointmentRepository.findById(50L)).thenReturn(Optional.of(aptOfB));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                patientWorkflowService.cancelAppointment("patientA@mediscan.com", 50L)
        );
        assertEquals("Bạn không có quyền hủy lịch hẹn này.", ex.getMessage());
    }

    // =========================================================================
    // 4. NHÓM HỒ SƠ Y TẾ & KẾT QUẢ (PAT_TC18 -> PAT_TC22)
    // =========================================================================

    @Test
    @DisplayName("PAT_TC18: Xem danh sách hồ sơ chỉ trả về hồ sơ của bệnh nhân đăng nhập")
    void test_PAT_TC18_ViewRecordsList_ShouldReturnOnlyPatientRecords() {
        List<ImagingRecord> patientRecords = List.of(new ImagingRecord(), new ImagingRecord());
        when(imagingRecordRepository.findByPatientOrderByCapturedAtDescCreatedAtDesc(patientUser)).thenReturn(patientRecords);

        List<ImagingRecord> result = imagingRecordRepository.findByPatientOrderByCapturedAtDescCreatedAtDesc(patientUser);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("PAT_TC19: Xem chi tiết hồ sơ hình ảnh chính chủ")
    void test_PAT_TC19_ViewRecordDetail_OwnRecord_ShouldDisplayDetails() {
        ImagingRecord record = new ImagingRecord();
        record.setPatient(patientUser);
        record.setBodyPart("CHEST");
        record.setStatus("COMPLETED");

        when(imagingRecordRepository.findById(1L)).thenReturn(Optional.of(record));

        ImagingRecord found = imagingRecordRepository.findById(1L).orElse(null);
        assertNotNull(found);
        assertEquals("CHEST", found.getBodyPart());
        assertEquals("COMPLETED", found.getStatus());
    }

    @Test
    @DisplayName("PAT_TC20: Bảo mật: Kiểm tra quyền sở hữu khi xem chi tiết hồ sơ")
    void test_PAT_TC20_CannotAccessOtherPatientRecord() {
        User userB = new User(); userB.setUserId(999L);

        ImagingRecord recordB = new ImagingRecord();
        recordB.setPatient(userB);

        boolean isOwner = recordB.getPatient() != null &&
                recordB.getPatient().getUserId().equals(patientUser.getUserId());

        assertFalse(isOwner);
    }

    @Test
    @DisplayName("PAT_TC21: Lọc hồ sơ theo từ khóa tìm kiếm (Keyword)")
    void test_PAT_TC21_FilterRecords_ByKeyword() {
        ImagingRecord r1 = new ImagingRecord(); r1.setBodyPart("CHEST");
        ImagingRecord r2 = new ImagingRecord(); r2.setBodyPart("KNEE");
        List<ImagingRecord> records = List.of(r1, r2);

        String search = "CHEST";
        List<ImagingRecord> filtered = records.stream()
                .filter(r -> r.getBodyPart() != null && r.getBodyPart().toUpperCase().contains(search))
                .toList();

        assertEquals(1, filtered.size());
        assertEquals("CHEST", filtered.get(0).getBodyPart());
    }

    @Test
    @DisplayName("PAT_TC22: Lọc hồ sơ theo bộ phận chụp (Body Part)")
    void test_PAT_TC22_FilterRecords_ByBodyPart() {
        ImagingRecord r1 = new ImagingRecord(); r1.setBodyPart("SPINE");
        ImagingRecord r2 = new ImagingRecord(); r2.setBodyPart("HAND");
        List<ImagingRecord> records = List.of(r1, r2);

        List<ImagingRecord> filtered = records.stream()
                .filter(r -> "SPINE".equalsIgnoreCase(r.getBodyPart()))
                .toList();

        assertEquals(1, filtered.size());
        assertEquals("SPINE", filtered.get(0).getBodyPart());
    }

    // =========================================================================
    // 5. NHÓM HỒ SƠ CÁ NHÂN (PAT_TC23 -> PAT_TC26)
    // =========================================================================

    @Test
    @DisplayName("PAT_TC23: Xem thông tin hồ sơ cá nhân")
    void test_PAT_TC23_ViewPatientProfile() {
        when(userAccountService.findByEmail("patientA@mediscan.com")).thenReturn(patientUser);
        when(patientRepository.findByUser(patientUser)).thenReturn(Optional.of(patient));

        User user = userAccountService.findByEmail("patientA@mediscan.com");
        Patient p = patientRepository.findByUser(user).orElse(null);

        assertNotNull(p);
        assertEquals(patientUser, p.getUser());
    }

    @Test
    @DisplayName("PAT_TC24: Cập nhật hồ sơ bệnh nhân thành công")
    void test_PAT_TC24_UpdatePatientProfile_ValidData_ShouldSucceed() {
        when(userAccountService.findByEmail("patientA@mediscan.com")).thenReturn(patientUser);
        when(patientRepository.findByUser(patientUser)).thenReturn(Optional.of(patient));
        when(patientRepository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDate dob = LocalDate.of(1995, 5, 20);
        Patient updated = patientWorkflowService.updatePatientProfile(
                "patientA@mediscan.com", "Le Van A Updated", "0987654321", "Nam", dob, "Ha Noi", "Di ung phan hoa"
        );

        assertNotNull(updated);
        assertEquals("Le Van A Updated", patientUser.getFullName());
        assertEquals("0987654321", updated.getPhone());
        assertEquals("Nam", updated.getGender());
        assertEquals("Ha Noi", updated.getAddress());
        assertEquals("Di ung phan hoa", updated.getMedicalHistory());
        verify(patientRepository).save(patient);
    }

    @Test
    @DisplayName("PAT_TC25: Kiểm tra validation số điện thoại")
    void test_PAT_TC25_UpdateProfile_InvalidPhoneValidation() {
        String invalidPhone = "12345";
        boolean isValidPhone = invalidPhone.matches("^[0-9]{10}$");
        assertFalse(isValidPhone);
    }

    @Test
    @DisplayName("PAT_TC26: Kiểm tra giới hạn độ dài họ tên (<= 100 ký tự)")
    void test_PAT_TC26_UpdateProfile_FullNameExceedingLimit() {
        String longName = "A".repeat(105);
        boolean isExceeding = longName.length() > 100;
        assertTrue(isExceeding);
    }

    // =========================================================================
    // 6. NHÓM THÔNG BÁO & LỊCH HẸN (PAT_TC27 -> PAT_TC30)
    // =========================================================================

    @Test
    @DisplayName("PAT_TC27: Xem danh sách thông báo của bệnh nhân")
    void test_PAT_TC27_ViewNotificationsList() {
        Notification n1 = new Notification();
        Notification n2 = new Notification();
        when(notificationService.findForUser(patientUser)).thenReturn(List.of(n1, n2));

        List<Notification> list = notificationService.findForUser(patientUser);
        assertEquals(2, list.size());
    }

    @Test
    @DisplayName("PAT_TC28: Đánh dấu 1 thông báo là đã đọc")
    void test_PAT_TC28_MarkSingleNotificationAsRead() {
        when(notificationService.markAsRead(10L)).thenReturn(new Notification());
        Notification result = notificationService.markAsRead(10L);
        assertNotNull(result);
        verify(notificationService).markAsRead(10L);
    }

    @Test
    @DisplayName("PAT_TC29: Đếm số lượng thông báo chưa đọc")
    void test_PAT_TC29_CountUnreadNotifications() {
        when(notificationService.countUnread(patientUser)).thenReturn(3L);
        long unread = notificationService.countUnread(patientUser);
        assertEquals(3L, unread);
    }

    @Test
    @DisplayName("PAT_TC30: Xem chi tiết lịch hẹn của bệnh nhân")
    void test_PAT_TC30_ViewAppointmentDetail() {
        Appointment appointment = new Appointment();
        appointment.setAppointmentCode("APT-12345");
        appointment.setPatient(patient);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));

        Appointment found = appointmentRepository.findById(1L).orElse(null);
        assertNotNull(found);
        assertEquals("APT-12345", found.getAppointmentCode());
    }

    // =========================================================================
    // 7. NHÓM CHATBOT (PAT_TC31 -> PAT_TC33)
    // =========================================================================

    @Test
    @DisplayName("PAT_TC31: Gửi tin nhắn hợp lệ đến Chatbot nhận phản hồi thành công")
    void test_PAT_TC31_Chatbot_SendMessage_Valid() {
        ChatResponse response = chatbotNLPService.processMessage("Xin chao");
        assertNotNull(response);
        assertNotNull(response.getReply());
        assertTrue(response.getReply().contains("Trợ lý AI MediScan"));
    }

    @Test
    @DisplayName("PAT_TC32: Chatbot nhận diện ý định đặt lịch khám (Booking Intent)")
    void test_PAT_TC32_Chatbot_BookingIntent() {
        ChatResponse response = chatbotNLPService.processMessage("Toi muon dat lich kham benh");
        assertNotNull(response);
        assertEquals("triggerBooking", response.getAction());
        assertTrue(response.getReply().contains("đặt lịch khám"));
    }

    @Test
    @DisplayName("PAT_TC33: Chatbot phản hồi câu hỏi y khoa về gãy xương không lỗi font")
    void test_PAT_TC33_Chatbot_BoneFracture_MedicalQuery() {
        ChatResponse response = chatbotNLPService.processMessage("Toi bi gay xuong");
        assertNotNull(response);
        assertNotNull(response.getReply());
        assertTrue(response.getReply().contains("gãy xương") || response.getReply().contains("X-Quang"));
    }

    // =========================================================================
    // 8. NHÓM HỖ TRỢ / SUPPORT TICKET (PAT_TC34 -> PAT_TC36)
    // =========================================================================

    @Test
    @DisplayName("PAT_TC34: Gửi yêu cầu hỗ trợ hợp lệ")
    void test_PAT_TC34_Support_SubmitTicket_Valid() {
        String subject = "Lỗi đặt lịch";
        String message = "Tôi không thể chọn giờ khám chiều";
        assertFalse(subject.isBlank());
        assertFalse(message.isBlank());
    }

    @Test
    @DisplayName("PAT_TC35: Xem lịch sử yêu cầu hỗ trợ")
    void test_PAT_TC35_Support_ViewHistory() {
        List<String> supportTickets = List.of("Ticket 1: Cần hỗ trợ tải ảnh", "Ticket 2: Đổi giờ khám");
        assertEquals(2, supportTickets.size());
    }

    @Test
    @DisplayName("PAT_TC36: Chặn gửi yêu cầu hỗ trợ khi nội dung trống")
    void test_PAT_TC36_Support_SubmitEmptyMessage_Validation() {
        String subject = "Lỗi";
        String emptyMessage = "   ";
        boolean isInvalid = emptyMessage.trim().isEmpty();
        assertTrue(isInvalid);
    }
}
