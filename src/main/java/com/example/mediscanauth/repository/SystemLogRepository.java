package com.example.mediscanauth.repository;

import com.example.mediscanauth.model.SystemLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SystemLogRepository extends JpaRepository<SystemLog, Long>, JpaSpecificationExecutor<SystemLog> {
}
