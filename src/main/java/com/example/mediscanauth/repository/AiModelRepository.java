package com.example.mediscanauth.repository;

import com.example.mediscanauth.model.AiModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiModelRepository extends JpaRepository<AiModel, Long> {
    List<AiModel> findAllByOrderByCreatedAtDesc();
}
