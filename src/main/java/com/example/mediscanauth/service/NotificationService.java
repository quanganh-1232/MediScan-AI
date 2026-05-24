package com.example.mediscanauth.service;

import com.example.mediscanauth.model.Notification;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.repository.NotificationRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public List<Notification> findForUser(User user) {
        return notificationRepository.findByUserOrderByCreatedAtDesc(user);
    }

    public long countUnread(User user) {
        return notificationRepository.countByUserAndReadFalse(user);
    }

    @Transactional
    public Notification markAsRead(Long notificationId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Không thấy thông báo"));
        n.setRead(true);
        return notificationRepository.save(n);
    }
}
