package com.example.mediscanauth.service;

import com.example.mediscanauth.model.User;
import com.example.mediscanauth.repository.UserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountService {

    private final UserRepository userRepository;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;

    public UserAccountService(UserRepository userRepository,
                              RoleService roleService,
                              PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleService = roleService;
        this.passwordEncoder = passwordEncoder;
    }

    public User findByEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        return userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản: " + normalizedEmail));
    }

    @Transactional
    public User registerPatient(String fullName, String email, String phone, String password, String confirmPassword) {
        String normalizedEmail = normalizeEmail(email);

        if (!password.equals(confirmPassword)) {
            throw new IllegalArgumentException("Mật khẩu xác nhận không khớp.");
        }

        if (normalizedEmail.isBlank()) {
            throw new IllegalArgumentException("Email không hợp lệ.");
        }

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email đã tồn tại trong hệ thống.");
        }

        User user = new User();
        user.setFullName(fullName);
        user.setEmail(normalizedEmail);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setAuthProvider("LOCAL");
        user.setStatus("ACTIVE");
        user.setRole(roleService.getOrCreatePatientRole());

        return userRepository.save(user);
    }

    @Transactional
    public User updateProfile(String currentEmail, String fullName, String email, String phone) {
        User user = findByEmail(currentEmail);
        String normalizedEmail = normalizeEmail(email);

        if (normalizedEmail.isBlank()) {
            throw new IllegalArgumentException("Email không hợp lệ.");
        }

        if (!user.getEmail().equals(normalizedEmail) && userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email mới đã tồn tại trong hệ thống.");
        }

        user.setFullName(fullName);
        user.setEmail(normalizedEmail);
        user.setPhone(phone);
        return userRepository.save(user);
    }

    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword, String confirmPassword) {
        User user = findByEmail(email);

        if (!"LOCAL".equalsIgnoreCase(user.getAuthProvider())) {
            throw new IllegalArgumentException("Tài khoản Google không đổi mật khẩu tại hệ thống.");
        }

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu hiện tại không đúng.");
        }

        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Mật khẩu xác nhận không khớp.");
        }

        if (newPassword.length() < 6) {
            throw new IllegalArgumentException("Mật khẩu mới cần tối thiểu 6 ký tự.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
