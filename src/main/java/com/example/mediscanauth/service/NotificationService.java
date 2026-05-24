package com.example.mediscanauth.service;

import com.example.mediscanauth.model.Notification;
import com.example.mediscanauth.model.User;

import java.util.List;

public interface NotificationService {

    List<Notification> findForUser(User user);

    long countUnread(User user);
}
