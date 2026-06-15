package com.example.mediscanauth.service.impl;

import com.example.mediscanauth.model.Notification;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.repository.NotificationRepository;
import com.example.mediscanauth.service.NotificationService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public List<Notification> findForUser(User user) {
        return notificationRepository.findByUserOrderByCreatedAtDesc(user);
    }

    @Override
    public long countUnread(User user) {
        return notificationRepository.countByUserAndReadFalse(user);
    }

    @Override
    @Transactional
    public Notification markAsRead(Long notificationId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Không thấy thông báo"));
        n.setRead(true);
        return notificationRepository.save(n);
    }
}