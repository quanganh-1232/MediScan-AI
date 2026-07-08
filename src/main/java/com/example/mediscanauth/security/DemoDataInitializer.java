package com.example.mediscanauth.security;

import com.example.mediscanauth.model.Role;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.repository.UserRepository;
import com.example.mediscanauth.service.RoleService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DemoDataInitializer implements CommandLineRunner {

    private final boolean enabled;
    private final UserRepository userRepository;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;

    public DemoDataInitializer(@Value("${app.seed.demo-users:true}") boolean enabled,
                               UserRepository userRepository,
                               RoleService roleService,
                               PasswordEncoder passwordEncoder) {
        this.enabled = enabled;
        this.userRepository = userRepository;
        this.roleService = roleService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            return;
        }

        Role adminRole = roleService.getOrCreateRole("ADMIN", "System administrator");
        Role doctorRole = roleService.getOrCreateRole("DOCTOR", "Doctor who reviews AI results");
        Role technicianRole = roleService.getOrCreateRole("TECHNICIAN", "Technician who creates imaging records");
        Role patientRole = roleService.getOrCreateRole("PATIENT", "Patient who views medical records");
        Role receptionistRole = roleService.getOrCreateRole("RECEPTIONIST", "Receptionist who confirms bookings and routes patients to a doctor");

        List<DemoUser> demoUsers = List.of(
                new DemoUser("admin@mediscan.com", "System Admin", adminRole),
                new DemoUser("doctor@mediscan.com", "Doctor Nguyen Van A", doctorRole),
                new DemoUser("tech@mediscan.com", "Technician Tran Van B", technicianRole),
                new DemoUser("patient@mediscan.com", "Patient Le Van C", patientRole),
                new DemoUser("reception@mediscan.com", "Receptionist Pham Thi D", receptionistRole)
        );

        for (DemoUser demoUser : demoUsers) {
            User user = userRepository.findByEmail(demoUser.email()).orElseGet(User::new);
            user.setEmail(demoUser.email());
            user.setFullName(demoUser.fullName());
            user.setRole(demoUser.role());
            user.setStatus("ACTIVE");
            user.setAuthProvider("LOCAL");
            user.setPasswordHash(passwordEncoder.encode("123456"));
            userRepository.save(user);
        }
    }

    private record DemoUser(String email, String fullName, Role role) {
    }
}
