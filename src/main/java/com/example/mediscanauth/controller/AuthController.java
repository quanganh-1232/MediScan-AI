package com.example.mediscanauth.controller;

import com.example.mediscanauth.model.Role;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.repository.RoleRepository;
import com.example.mediscanauth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Collections;

@Controller
public class AuthController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        model.addAttribute("user", new User());
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // Mặc định gán quyền Bệnh nhân (ROLE_PATIENT) cho người đăng ký mới
        Role userRole = roleRepository.findByName("ROLE_PATIENT");
        if (userRole == null) {
            userRole = new Role();
            userRole.setName("ROLE_PATIENT");
            roleRepository.save(userRole);
        }
        user.setRoles(Collections.singletonList(userRole));

        userRepository.save(user);
        return "redirect:/login?success";
    }

    @GetMapping("/home")
    public String home() {
        return "home";
    }
}