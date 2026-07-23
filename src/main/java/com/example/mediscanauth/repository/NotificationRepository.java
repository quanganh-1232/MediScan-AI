package com.example.mediscanauth.repository;

import com.example.mediscanauth.model.Notification;
import com.example.mediscanauth.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserOrderByCreatedAtDesc(User user);

    Page<Notification> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    List<Notification> findTop5ByUserOrderByCreatedAtDesc(User user);

    long countByUserAndReadFalse(User user);
}
