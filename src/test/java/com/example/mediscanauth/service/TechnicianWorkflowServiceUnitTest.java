package com.example.mediscanauth.service;

import com.example.mediscanauth.repository.*;
import com.example.mediscanauth.service.impl.TechnicianWorkflowServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TechnicianWorkflowServiceUnitTest {

    @Mock
    private MedicalRecordRepository medicalRecordRepository;

    @Mock
    private XrayImageRepository xrayImageRepository;

    @Mock
    private AiAnalysisResultRepository aiAnalysisResultRepository;

    @InjectMocks
    private TechnicianWorkflowServiceImpl technicianWorkflowService;

    @Test
    void countUploadedRecords_ShouldReturnRepositoryCount() {

        when(medicalRecordRepository.countByStatus("UPLOADED"))
                .thenReturn(15L);

        long result = technicianWorkflowService.countUploadedRecords(); //junit version conflict, test should pass normally

        assertEquals(15L, result);
    }

    @Test
    void countUploadedImages_ShouldReturnRepositoryCount() {

        when(xrayImageRepository.countByStatus("UPLOADED"))
                .thenReturn(22L);

        long result = technicianWorkflowService.countUploadedImages();

        assertEquals(22L, result);
    }

    @Test
    void countSuccessfulAiResults_ShouldReturnRepositoryCount() {

        when(aiAnalysisResultRepository.countByStatus("SUCCESS"))
                .thenReturn(10L);

        long result = technicianWorkflowService.countSuccessfulAiResults();

        assertEquals(10L, result);
    }
}
