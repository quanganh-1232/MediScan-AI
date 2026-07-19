package com.example.mediscanauth.service;

import com.example.mediscanauth.model.Notification;
import com.example.mediscanauth.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface NotificationService {

    List<Notification> findForUser(User user);

    Page<Notification> findForUser(User user, Pageable pageable);

    List<Notification> findRecentForUser(User user);

    long countUnread(User user);

    Notification markAsRead(Long notificationId);
}