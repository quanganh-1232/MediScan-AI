package com.example.mediscanauth.service.impl;

import com.example.mediscanauth.model.Patient;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.repository.PatientRepository;
import com.example.mediscanauth.repository.UserRepository;
import com.example.mediscanauth.service.RoleService;
import com.example.mediscanauth.service.UserAccountService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountServiceImpl implements UserAccountService {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;

    public UserAccountServiceImpl(UserRepository userRepository,
                                  PatientRepository patientRepository,
                                  RoleService roleService,
                                  PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.roleService = roleService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public User findByEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        return userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản: " + normalizedEmail));
    }

    @Override
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

        User savedUser = userRepository.save(user);
        createPatientProfile(savedUser);
        return savedUser;
    }

    @Override
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
        User savedUser = userRepository.save(user);
        patientRepository.findByUser(savedUser).ifPresent(patient -> {
            patient.setFullName(savedUser.getFullName());
            patient.setPhone(savedUser.getPhone());
            patientRepository.save(patient);
        });
        return savedUser;
    }

    @Override
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

    private Patient createPatientProfile(User user) {
        return patientRepository.findByUser(user).orElseGet(() -> {
            Patient patient = new Patient();
            patient.setUser(user);
            patient.setFullName(user.getFullName());
            patient.setPhone(user.getPhone());
            patient.setGender("OTHER");
            return patientRepository.save(patient);
        });
    }
}
