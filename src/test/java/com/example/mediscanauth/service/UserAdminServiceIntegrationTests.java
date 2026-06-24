package com.example.mediscanauth.service;

import com.example.mediscanauth.model.User;
import com.example.mediscanauth.repository.UserRepository;
import com.example.mediscanauth.security.CustomUserDetailsService;
import com.example.mediscanauth.service.impl.UserAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.security.authentication.DisabledException;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class UserAdminServiceIntegrationTests {

    @Autowired
    private UserAdminService userAdminService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void filtersUsersByKeywordRoleAndStatus() {
        Page<User> result = userAdminService.filterUsers("doctor", "doctor", "active", -1, 20);

        assertEquals(1, result.getTotalElements());
        assertTrue(result.getContent().stream().allMatch(user ->
                "DOCTOR".equals(user.getRole().getRoleName())
                        && "ACTIVE".equals(user.getStatus())));
    }

    @Test
    void lockAndUnlockControlsLocalLogin() {
        User patient = requireUser("patient@mediscan.com");

        userAdminService.lockAccount(patient.getUserId(), "admin@mediscan.com");
        assertEquals("LOCKED", requireUser(patient.getEmail()).getStatus());
        assertThrows(DisabledException.class,
                () -> customUserDetailsService.loadUserByUsername(patient.getEmail()));

        userAdminService.unlockAccount(patient.getUserId());
        assertDoesNotThrow(() -> customUserDetailsService.loadUserByUsername(patient.getEmail()));
    }

    @Test
    void preventsAdminFromLockingOwnAccount() {
        User admin = requireUser("admin@mediscan.com");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userAdminService.lockAccount(admin.getUserId(), admin.getEmail()));

        assertTrue(exception.getMessage().contains("cannot lock"));
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }
}
