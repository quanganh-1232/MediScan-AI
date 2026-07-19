package com.example.mediscanauth.service;

import com.example.mediscanauth.repository.*;
import com.example.mediscanauth.service.impl.ImagingRecordServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
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

    @InjectMocks
    private ImagingRecordServiceImpl imagingRecordService;
}
