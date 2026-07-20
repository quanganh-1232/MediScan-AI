package com.example.mediscanauth.service.impl;

import com.example.mediscanauth.model.Notification;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.repository.NotificationRepository;
import com.example.mediscanauth.service.NotificationService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

import com.example.mediscanauth.repository.UserRepository;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                   UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Override
    public List<Notification> findForUser(User user) {
        return notificationRepository.findByUserOrderByCreatedAtDesc(user);
    }

    @Override
    public List<Notification> findRecentForUser(User user) {
        return notificationRepository.findTop5ByUserOrderByCreatedAtDesc(user);
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

    @Override
    @Transactional
    public void sendNotification(User recipient, String title, String message, Long recordId) {
        if (recipient == null) return;
        Notification n = new Notification();
        n.setUser(recipient);
        n.setTitle(title);
        n.setMessage(message);
        n.setRecordId(recordId);
        n.setRead(false);
        notificationRepository.save(n);
    }

    @Override
    @Transactional
    public void notifyRoleUsers(List<String> roleNames, String title, String message, Long recordId) {
        List<User> users = userRepository.findByRoleRoleNameInAndStatusOrderByFullNameAsc(roleNames, "ACTIVE");
        for (User u : users) {
            sendNotification(u, title, message, recordId);
        }
    }
}
