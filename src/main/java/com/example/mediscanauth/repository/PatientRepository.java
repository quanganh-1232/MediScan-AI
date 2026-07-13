package com.example.mediscanauth.repository;

import com.example.mediscanauth.model.Patient;
import com.example.mediscanauth.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface PatientRepository extends JpaRepository<Patient, Long> {
    Optional<Patient> findByUser(User user);

    List<Patient> findAllByOrderByCreatedAtDesc();
}
