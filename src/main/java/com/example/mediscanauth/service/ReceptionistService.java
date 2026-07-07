package com.example.mediscanauth.service;

import com.example.mediscanauth.model.Appointment;

import java.time.LocalTime;

public interface ReceptionistService {

    Appointment confirmAppointment(Long appointmentId, String receptionistEmail);

    Appointment checkInAppointment(Long appointmentId, String receptionistEmail);

    Appointment assignDoctor(Long appointmentId, Long doctorId, String note, String receptionistEmail);

    Appointment createWalkInAppointment(String fullName,
                                        String phone,
                                        String symptom,
                                        Long doctorId,
                                        LocalTime scheduledTime,
                                        String receptionistEmail);

    Appointment cancelAppointment(Long appointmentId, String reason, String receptionistEmail);

    Appointment markMissed(Long appointmentId, String receptionistEmail);

    Appointment callNextPatient(String receptionistEmail);
}
