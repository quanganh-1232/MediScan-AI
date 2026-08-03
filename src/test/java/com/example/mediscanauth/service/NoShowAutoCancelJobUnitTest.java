package com.example.mediscanauth.service;

import com.example.mediscanauth.constant.OperationalConfig;
import com.example.mediscanauth.model.Appointment;
import com.example.mediscanauth.model.AppointmentStatusHistory;
import com.example.mediscanauth.repository.AppointmentRepository;
import com.example.mediscanauth.repository.AppointmentStatusHistoryRepository;
import com.example.mediscanauth.service.impl.NoShowAutoCancelJob;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * LOGIC LAYER — kiểm thử {@link NoShowAutoCancelJob}: job nền tự động hủy các
 * lịch hẹn PENDING/CONFIRMED mà lễ tân chưa xử lý (chưa xác nhận/check-in) và
 * đã quá {@link OperationalConfig#NO_SHOW_GRACE_MINUTES} phút so với giờ hẹn.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NoShowAutoCancelJobUnitTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentStatusHistoryRepository historyRepository;

    @InjectMocks
    private NoShowAutoCancelJob job;

    private static void setAppointmentId(Appointment appointment, Long id) {
        ReflectionTestUtils.setField(appointment, "appointmentId", id);
    }

    @Test
    @DisplayName("Lich PENDING/CONFIRMED qua gio hen + grace period ma chua check-in -> tu dong CANCELLED")
    void test_CancelNoShowAppointments_OverdueUnhandled_ShouldBeCancelled() {
        Appointment overduePending = new Appointment();
        setAppointmentId(overduePending, 1L);
        overduePending.setStatus("PENDING");
        overduePending.setScheduledTime(LocalDateTime.now().minusMinutes(20));

        Appointment overdueConfirmed = new Appointment();
        setAppointmentId(overdueConfirmed, 2L);
        overdueConfirmed.setStatus("CONFIRMED");
        overdueConfirmed.setScheduledTime(LocalDateTime.now().minusMinutes(15));

        when(appointmentRepository.findByStatusInAndScheduledTimeBefore(
                eq(List.of("PENDING", "CONFIRMED")), any(LocalDateTime.class)))
                .thenReturn(List.of(overduePending, overdueConfirmed));

        job.cancelNoShowAppointments();

        assertEquals("CANCELLED", overduePending.getStatus());
        assertEquals("CANCELLED", overdueConfirmed.getStatus());
        verify(appointmentRepository).save(overduePending);
        verify(appointmentRepository).save(overdueConfirmed);

        ArgumentCaptor<AppointmentStatusHistory> captor = ArgumentCaptor.forClass(AppointmentStatusHistory.class);
        verify(historyRepository, times(2)).save(captor.capture());
        for (AppointmentStatusHistory h : captor.getAllValues()) {
            assertEquals("CANCELLED", h.getStatus());
            assertNull(h.getActor(), "Hanh dong tu dong khong gan cho mot lễ tân cụ thể nao");
            assertTrue(h.getNote().contains("Tự động hủy"));
        }
    }

    @Test
    @DisplayName("Truy van repository dung cutoff = now - NO_SHOW_GRACE_MINUTES")
    void test_CancelNoShowAppointments_ShouldQueryWithCorrectGraceCutoff() {
        when(appointmentRepository.findByStatusInAndScheduledTimeBefore(anyList(), any(LocalDateTime.class)))
                .thenReturn(List.of());

        LocalDateTime before = LocalDateTime.now().minusMinutes(OperationalConfig.NO_SHOW_GRACE_MINUTES);
        job.cancelNoShowAppointments();
        LocalDateTime after = LocalDateTime.now().minusMinutes(OperationalConfig.NO_SHOW_GRACE_MINUTES);

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(appointmentRepository).findByStatusInAndScheduledTimeBefore(eq(List.of("PENDING", "CONFIRMED")),
                cutoffCaptor.capture());
        LocalDateTime usedCutoff = cutoffCaptor.getValue();

        assertFalse(usedCutoff.isBefore(before));
        assertFalse(usedCutoff.isAfter(after));
    }

    @Test
    @DisplayName("Khong co lich hen nao qua han -> khong luu/khong ghi lich su gi ca")
    void test_CancelNoShowAppointments_NoneOverdue_ShouldDoNothing() {
        when(appointmentRepository.findByStatusInAndScheduledTimeBefore(anyList(), any(LocalDateTime.class)))
                .thenReturn(List.of());

        job.cancelNoShowAppointments();

        verify(appointmentRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }
}
