package com.example.mediscanauth.service;

import com.example.mediscanauth.model.User;

public interface UserAccountService {

    User findByEmail(String email);

    User registerPatient(String fullName, String email, String phone, String password, String confirmPassword);

    User updateProfile(String currentEmail, String fullName, String email, String phone);

    void changePassword(String email, String currentPassword, String newPassword, String confirmPassword);
}
