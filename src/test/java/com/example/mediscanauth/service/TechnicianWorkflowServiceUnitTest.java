package com.example.mediscanauth.service;

import com.example.mediscanauth.repository.*;
import com.example.mediscanauth.service.impl.TechnicianWorkflowServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Cac stat cua Trạm chụp X-quang (technician dashboard) trước đây đọc từ
 * MedicalRecord/XrayImage/AiAnalysisResult — những bảng "song song" không còn
 * được luồng chụp thật (ImagingRecordServiceImpl.captureAndAnalyzeFromTechnician)
 * ghi vào nữa, nên luôn hiển thị sai/luôn 0. Test này khoá lại hành vi đúng:
 * mọi số liệu phải đọc từ ImagingRecordRepository, lọc theo đúng kỹ thuật viên
 * đang đăng nhập và ngày hôm nay.
 */
@ExtendWith(MockitoExtension.class)
class TechnicianWorkflowServiceUnitTest {

    @Mock
    private MedicalRecordRepository medicalRecordRepository;

    @Mock
    private XrayImageRepository xrayImageRepository;

    @Mock
    private AiAnalysisResultRepository aiAnalysisResultRepository;

    @Mock
    private ImagingRecordRepository imagingRecordRepository;

    @InjectMocks
    private TechnicianWorkflowServiceImpl technicianWorkflowService;

    private static final String TECH_EMAIL = "tech@mediscan.com";

    @Test
    @DisplayName("countCapturedToday: dem theo dung ky thuat vien + ngay hom nay, tu ImagingRecordRepository")
    void countCapturedToday_ShouldQueryImagingRecordsForThisTechnicianToday() {
        when(imagingRecordRepository.countByTechnicianEmailAndCapturedAt(TECH_EMAIL, LocalDate.now()))
                .thenReturn(5L);

        long result = technicianWorkflowService.countCapturedToday(TECH_EMAIL);

        assertEquals(5L, result);
    }

    @Test
    @DisplayName("countPendingAiToday: chi dem phim dang PENDING_AI cua ky thuat vien nay hom nay")
    void countPendingAiToday_ShouldFilterByPendingAiStatus() {
        when(imagingRecordRepository.countByTechnicianEmailAndStatusAndCapturedAt(
                TECH_EMAIL, "PENDING_AI", LocalDate.now())).thenReturn(1L);

        long result = technicianWorkflowService.countPendingAiToday(TECH_EMAIL);

        assertEquals(1L, result);
    }

    @Test
    @DisplayName("countPendingProcessingToday: gom ca PENDING_AI va PENDING_DOCTOR (chua co ket luan cuoi)")
    void countPendingProcessingToday_ShouldIncludePendingAiAndPendingDoctor() {
        when(imagingRecordRepository.countByTechnicianEmailAndStatusInAndCapturedAt(
                eq(TECH_EMAIL), eq(List.of("PENDING_AI", "PENDING_DOCTOR")), eq(LocalDate.now())))
                .thenReturn(4L);

        long result = technicianWorkflowService.countPendingProcessingToday(TECH_EMAIL);

        assertEquals(4L, result);
    }

    @Test
    @DisplayName("countAiProcessedToday: gom PENDING_DOCTOR/COMPLETED/DOCTOR_REJECTED (AI da xu ly xong)")
    void countAiProcessedToday_ShouldIncludeAllPostAiStatuses() {
        when(imagingRecordRepository.countByTechnicianEmailAndStatusInAndCapturedAt(
                eq(TECH_EMAIL), eq(List.of("PENDING_DOCTOR", "COMPLETED", "DOCTOR_REJECTED")), eq(LocalDate.now())))
                .thenReturn(10L);

        long result = technicianWorkflowService.countAiProcessedToday(TECH_EMAIL);

        assertEquals(10L, result);
    }

    @Test
    @DisplayName("countDoctorApprovedToday: chi dem phim da COMPLETED (bac si da xac nhan)")
    void countDoctorApprovedToday_ShouldFilterByCompletedStatus() {
        when(imagingRecordRepository.countByTechnicianEmailAndStatusAndCapturedAt(
                TECH_EMAIL, "COMPLETED", LocalDate.now())).thenReturn(3L);

        long result = technicianWorkflowService.countDoctorApprovedToday(TECH_EMAIL);

        assertEquals(3L, result);
    }
}
