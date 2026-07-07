package com.example.mediscanauth.service;

import com.example.mediscanauth.model.Appointment;

public interface ReceptionistService {

    Appointment confirmAppointment(Long appointmentId, String receptionistEmail);

    Appointment checkInAppointment(Long appointmentId, String receptionistEmail);
}
