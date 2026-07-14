package com.example.mediscanauth.repository;

import com.example.mediscanauth.model.Patient;
import com.example.mediscanauth.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long>, JpaSpecificationExecutor<Patient> {

    Optional<Patient> findByUser(User user);

    List<Patient> findAllByOrderByCreatedAtDesc();
}