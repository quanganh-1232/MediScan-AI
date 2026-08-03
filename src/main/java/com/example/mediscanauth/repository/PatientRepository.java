package com.example.mediscanauth.repository;

import com.example.mediscanauth.model.Patient;
import com.example.mediscanauth.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long>, JpaSpecificationExecutor<Patient> {

    Optional<Patient> findByUser(User user);

    List<Patient> findAllByOrderByCreatedAtDesc();

    Optional<Patient> findFirstByPhoneOrderByPatientIdDesc(String phone);

    /**
     * Patients this doctor actually has a case for — used to scope the
     * doctor's own patient list instead of showing every patient in the
     * system (a doctor may only view/handle patients assigned to them).
     */
    @Query("select distinct p from Patient p where p.user is not null and p.user.userId in " +
           "(select r.patient.userId from ImagingRecord r where r.doctor.userId = :doctorId)")
    List<Patient> findByAssignedDoctor(@Param("doctorId") Long doctorId);
}
