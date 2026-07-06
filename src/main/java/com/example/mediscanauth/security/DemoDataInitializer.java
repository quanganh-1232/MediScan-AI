package com.example.mediscanauth.security;

import com.example.mediscanauth.model.Department;
import com.example.mediscanauth.model.Role;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.repository.DepartmentRepository;
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
    private final DepartmentRepository departmentRepository;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;

    public DemoDataInitializer(@Value("${app.seed.demo-users:true}") boolean enabled,
                               UserRepository userRepository,
                               DepartmentRepository departmentRepository,
                               RoleService roleService,
                               PasswordEncoder passwordEncoder) {
        this.enabled = enabled;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
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
        Role receptionistRole = roleService.getOrCreateRole("RECEPTIONIST", "Receptionist who confirms bookings and routes patients");

        Department generalDept = getOrCreateDepartment("Khám tổng quát", "Khám sơ bộ, tiếp nhận ban đầu");
        getOrCreateDepartment("Chấn thương chỉnh hình", "Khoa xương khớp, chấn thương");
        getOrCreateDepartment("Nội tổng quát", "Khoa nội");
        getOrCreateDepartment("Nhi khoa", "Khoa nhi");

        List<DemoUser> demoUsers = List.of(
                new DemoUser("admin@mediscan.com", "System Admin", adminRole, null),
                new DemoUser("doctor@mediscan.com", "Doctor Nguyen Van A", doctorRole, generalDept),
                new DemoUser("tech@mediscan.com", "Technician Tran Van B", technicianRole, null),
                new DemoUser("patient@mediscan.com", "Patient Le Van C", patientRole, null),
                new DemoUser("reception@mediscan.com", "Receptionist Pham Thi D", receptionistRole, null)
        );

        for (DemoUser demoUser : demoUsers) {
            User user = userRepository.findByEmail(demoUser.email()).orElseGet(User::new);
            user.setEmail(demoUser.email());
            user.setFullName(demoUser.fullName());
            user.setRole(demoUser.role());
            user.setDepartment(demoUser.department());
            user.setStatus("ACTIVE");
            user.setAuthProvider("LOCAL");
            user.setPasswordHash(passwordEncoder.encode("123456"));
            userRepository.save(user);
        }
    }

    private Department getOrCreateDepartment(String name, String description) {
        return departmentRepository.findByName(name).orElseGet(() -> {
            Department department = new Department();
            department.setName(name);
            department.setDescription(description);
            return departmentRepository.save(department);
        });
    }

    private record DemoUser(String email, String fullName, Role role, Department department) {
    }
}
