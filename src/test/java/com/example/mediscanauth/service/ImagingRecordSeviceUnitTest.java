package com.example.mediscanauth.service;

import com.cloudinary.Cloudinary;
import com.example.mediscanauth.model.ImagingRecord;
import com.example.mediscanauth.model.Role;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.repository.*;
import com.example.mediscanauth.service.impl.AuditLogService;
import com.example.mediscanauth.service.impl.ImagingRecordServiceImpl;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * LOGIC LAYER — kiểm thử việc phân quyền xem/xử lý hồ sơ chẩn đoán theo bác
 * sĩ được phân công, và các số liệu thống kê "ca chờ đọc ảnh" của
 * {@link ImagingRecordServiceImpl}.
 *
 * Quy tắc nghiệp vụ: một bác sĩ chỉ được xem/xử lý hồ sơ CÒN ĐANG XỬ LÝ
 * (chưa COMPLETED) nếu hồ sơ đó được gán cho chính họ. Một khi hồ sơ đã
 * COMPLETED, nó thuộc "thư viện chẩn đoán" dùng chung — mọi bác sĩ đều xem
 * được, không còn bị giới hạn theo người phụ trách.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ImagingRecordSeviceUnitTest {

    @Mock
    private ImagingRecordRepository imagingRecordRepository;

    @Mock
    private UserAccountService userAccountService;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private CloudinaryService cloudinaryService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private AppointmentRepository appointmentRepository;

    @InjectMocks
    private ImagingRecordServiceImpl imagingRecordService;

    private User doctorA;
    private User doctorB;

    private static void setDoctor(ImagingRecord record, User doctor) {
        ReflectionTestUtils.setField(record, "doctor", doctor);
    }

    private static void setStatus(ImagingRecord record, String status) {
        ReflectionTestUtils.setField(record, "status", status);
    }

    @BeforeEach
    void setUp() {
        Role doctorRole = new Role();
        doctorRole.setRoleName("DOCTOR");

        doctorA = new User();
        doctorA.setUserId(2L);
        doctorA.setEmail("doctor@mediscan.com");
        doctorA.setRole(doctorRole);

        doctorB = new User();
        doctorB.setUserId(7L);
        doctorB.setEmail("hason.ls.it@gmail.com");
        doctorB.setRole(doctorRole);
    }

    // =========================================================================
    // Phan quyen xem/xu ly ho so (getRecordForDoctor)
    // =========================================================================

    @Test
    @DisplayName("Ho so dang xu ly (PENDING_DOCTOR), duoc bac si so huu xem -> thanh cong")
    void test_GetRecordForDoctor_OwnerViewsActiveRecord_ShouldSucceed() {
        ImagingRecord record = new ImagingRecord();
        setStatus(record, "PENDING_DOCTOR");
        setDoctor(record, doctorA);
        when(imagingRecordRepository.findById(1L)).thenReturn(Optional.of(record));

        ImagingRecord result = imagingRecordService.getRecordForDoctor(1L, "doctor@mediscan.com");

        assertSame(record, result);
    }

    @Test
    @DisplayName("Ho so dang xu ly (PENDING_DOCTOR), bac si KHAC co gang xem -> bi chan")
    void test_GetRecordForDoctor_OtherDoctorViewsActiveRecord_ShouldThrow() {
        ImagingRecord record = new ImagingRecord();
        setStatus(record, "PENDING_DOCTOR");
        setDoctor(record, doctorB);
        when(imagingRecordRepository.findById(1L)).thenReturn(Optional.of(record));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> imagingRecordService.getRecordForDoctor(1L, "doctor@mediscan.com"));
        assertTrue(ex.getMessage().contains("bác sĩ khác"));
    }

    @Test
    @DisplayName("Ho so da COMPLETED (thu vien chan doan): bac si KHAC van xem duoc binh thuong")
    void test_GetRecordForDoctor_OtherDoctorViewsCompletedRecord_ShouldSucceed() {
        ImagingRecord record = new ImagingRecord();
        setStatus(record, "COMPLETED");
        setDoctor(record, doctorB);
        when(imagingRecordRepository.findById(1L)).thenReturn(Optional.of(record));

        ImagingRecord result = imagingRecordService.getRecordForDoctor(1L, "doctor@mediscan.com");

        assertSame(record, result);
    }

    @Test
    @DisplayName("Ho so chua duoc gan bac si nao (legacy) -> ai cung xem duoc")
    void test_GetRecordForDoctor_UnassignedRecord_ShouldSucceed() {
        ImagingRecord record = new ImagingRecord();
        setStatus(record, "PENDING_DOCTOR");
        setDoctor(record, null);
        when(imagingRecordRepository.findById(1L)).thenReturn(Optional.of(record));

        ImagingRecord result = imagingRecordService.getRecordForDoctor(1L, "doctor@mediscan.com");

        assertSame(record, result);
    }

    // =========================================================================
    // Thong ke "ca cho doc anh" phai gioi han theo bac si (khong con la so
    // lieu toan he thong)
    // =========================================================================

    @Test
    @DisplayName("countTodayForDoctor: chi dem ho so hom nay cua dung bac si nay, khong phai toan he thong")
    void test_CountTodayForDoctor_ShouldDelegateWithDoctorIdAndToday() {
        when(imagingRecordRepository.countByDoctorUserIdAndCapturedAt(2L, LocalDate.now())).thenReturn(3L);

        long result = imagingRecordService.countTodayForDoctor(2L);

        assertEquals(3L, result);
        verify(imagingRecordRepository).countByDoctorUserIdAndCapturedAt(2L, LocalDate.now());
    }

    @Test
    @DisplayName("countAllForDoctor: chi dem tong ho so cua dung bac si nay, khong phai toan he thong")
    void test_CountAllForDoctor_ShouldDelegateWithDoctorId() {
        when(imagingRecordRepository.countByDoctorUserId(2L)).thenReturn(12L);

        long result = imagingRecordService.countAllForDoctor(2L);

        assertEquals(12L, result);
        verify(imagingRecordRepository).countByDoctorUserId(2L);
    }

    // =========================================================================
    // User.isGuestAccount() — nhan dien tai khoan khach vang lai qua email
    // @mediscan.local do le tan tu tao (ReceptionistServiceImpl#createDummyUser)
    // =========================================================================

    @Test
    @DisplayName("Tai khoan khach vang lai (walkin_xxx@mediscan.local) -> isGuestAccount = true")
    void test_IsGuestAccount_WalkinEmail_ShouldReturnTrue() {
        User guest = new User();
        guest.setEmail("walkin_ab12cd34@mediscan.local");
        assertTrue(guest.isGuestAccount());
    }

    @Test
    @DisplayName("Tai khoan that (email thuong) -> isGuestAccount = false")
    void test_IsGuestAccount_RealEmail_ShouldReturnFalse() {
        User real = new User();
        real.setEmail("patient@mediscan.com");
        assertFalse(real.isGuestAccount());
    }

    // =========================================================================
    // confirmDoctorReview: benh nhan khach vang lai luon bi khoa ve PRIVATE,
    // benh nhan co tai khoan that thi theo dung lua chon cua bac si
    // =========================================================================

    @Test
    @DisplayName("Xac nhan chan doan cho benh nhan KHACH VANG LAI -> luon luu PRIVATE du bac si chon PUBLIC")
    void test_ConfirmDoctorReview_GuestPatient_ShouldForcePrivateRegardlessOfChoice() {
        User guestPatient = new User();
        guestPatient.setUserId(50L);
        guestPatient.setEmail("walkin_zz99yy88@mediscan.local");

        ImagingRecord record = new ImagingRecord();
        setStatus(record, "PENDING_DOCTOR");
        record.setPatient(guestPatient);
        when(imagingRecordRepository.findById(10L)).thenReturn(Optional.of(record));
        when(userAccountService.findByEmail("doctor@mediscan.com")).thenReturn(doctorA);
        when(appointmentRepository.findByPatientUserOrderByScheduledTimeDesc(guestPatient)).thenReturn(List.of());
        when(imagingRecordRepository.save(any(ImagingRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        ImagingRecord result = imagingRecordService.confirmDoctorReview(
                10L, "doctor@mediscan.com", "Ket luan", null, null, "PUBLIC");

        assertEquals("PRIVATE", result.getVisibility());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Xac nhan chan doan cho benh nhan CO TAI KHOAN -> luu dung lua chon PUBLIC cua bac si")
    void test_ConfirmDoctorReview_RealPatient_ShouldRespectChosenVisibility() {
        User realPatient = new User();
        realPatient.setUserId(60L);
        realPatient.setEmail("patientA@mediscan.com");

        ImagingRecord record = new ImagingRecord();
        setStatus(record, "PENDING_DOCTOR");
        record.setPatient(realPatient);
        record.setRecordCode("XR-2026-0099");
        when(imagingRecordRepository.findById(11L)).thenReturn(Optional.of(record));
        when(userAccountService.findByEmail("doctor@mediscan.com")).thenReturn(doctorA);
        when(appointmentRepository.findByPatientUserOrderByScheduledTimeDesc(realPatient)).thenReturn(List.of());
        when(imagingRecordRepository.save(any(ImagingRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        ImagingRecord result = imagingRecordService.confirmDoctorReview(
                11L, "doctor@mediscan.com", "Ket luan", null, null, "PUBLIC");

        assertEquals("PUBLIC", result.getVisibility());
        verify(notificationRepository).save(any());
    }

    // =========================================================================
    // updateRecordVisibility: chinh sua trong thu vien chan doan
    // =========================================================================

    @Test
    @DisplayName("Bac si phu trach doi ho so COMPLETED tu PRIVATE sang PUBLIC -> thanh cong, gui thong bao")
    void test_UpdateRecordVisibility_OwnerMakesPublic_ShouldSucceedAndNotify() {
        User realPatient = new User();
        realPatient.setUserId(60L);
        realPatient.setEmail("patientA@mediscan.com");

        ImagingRecord record = new ImagingRecord();
        setStatus(record, "COMPLETED");
        setDoctor(record, doctorA);
        record.setPatient(realPatient);
        record.setRecordCode("XR-2026-0099");
        record.setVisibility("PRIVATE");
        when(imagingRecordRepository.findById(20L)).thenReturn(Optional.of(record));
        when(imagingRecordRepository.save(any(ImagingRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        ImagingRecord result = imagingRecordService.updateRecordVisibility(20L, "doctor@mediscan.com", "PUBLIC");

        assertEquals("PUBLIC", result.getVisibility());
        ArgumentCaptor<com.example.mediscanauth.model.Notification> captor =
                ArgumentCaptor.forClass(com.example.mediscanauth.model.Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertEquals(realPatient, captor.getValue().getUser());
    }

    @Test
    @DisplayName("Bac si KHONG phu trach ca nay co gang doi quyen xem -> bi chan")
    void test_UpdateRecordVisibility_NonOwner_ShouldThrow() {
        ImagingRecord record = new ImagingRecord();
        setStatus(record, "COMPLETED");
        setDoctor(record, doctorB);
        when(imagingRecordRepository.findById(21L)).thenReturn(Optional.of(record));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> imagingRecordService.updateRecordVisibility(21L, "doctor@mediscan.com", "PUBLIC"));
        assertTrue(ex.getMessage().contains("phụ trách"));
        verify(imagingRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("Chua COMPLETED thi khong duoc doi quyen xem")
    void test_UpdateRecordVisibility_NotCompleted_ShouldThrow() {
        ImagingRecord record = new ImagingRecord();
        setStatus(record, "PENDING_DOCTOR");
        setDoctor(record, doctorA);
        when(imagingRecordRepository.findById(22L)).thenReturn(Optional.of(record));

        assertThrows(IllegalArgumentException.class,
                () -> imagingRecordService.updateRecordVisibility(22L, "doctor@mediscan.com", "PUBLIC"));
        verify(imagingRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("Benh nhan khach vang lai: khong the doi sang PUBLIC tu thu vien")
    void test_UpdateRecordVisibility_GuestPatient_ShouldThrow() {
        User guestPatient = new User();
        guestPatient.setEmail("walkin_qq11ww22@mediscan.local");

        ImagingRecord record = new ImagingRecord();
        setStatus(record, "COMPLETED");
        setDoctor(record, doctorA);
        record.setPatient(guestPatient);
        when(imagingRecordRepository.findById(23L)).thenReturn(Optional.of(record));

        assertThrows(IllegalArgumentException.class,
                () -> imagingRecordService.updateRecordVisibility(23L, "doctor@mediscan.com", "PUBLIC"));
        verify(imagingRecordRepository, never()).save(any());
    }
}
