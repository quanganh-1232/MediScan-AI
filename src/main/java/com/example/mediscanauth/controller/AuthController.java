package com.example.mediscanauth.controller;

import com.example.mediscanauth.model.Role;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.repository.RoleRepository;
import com.example.mediscanauth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuthController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository,
                          RoleRepository roleRepository,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/")
    public String index() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String showRegistrationForm() {
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(@RequestParam String fullName,
                               @RequestParam String email,
                               @RequestParam(required = false) String phone,
                               @RequestParam String password,
                               @RequestParam String confirmPassword,
                               Model model) {

        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Mật khẩu xác nhận không khớp.");
            return "register";
        }

        if (userRepository.existsByEmail(email)) {
            model.addAttribute("error", "Email đã tồn tại trong hệ thống.");
            return "register";
        }

        Role patientRole = roleRepository.findByRoleName("PATIENT")
                .orElseGet(() -> roleRepository.save(
                        new Role("PATIENT", "Patient or external user")
                ));

        User user = new User();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus("ACTIVE");
        user.setRole(patientRole);

        userRepository.save(user);

        return "redirect:/login?success";
    }

    @GetMapping("/home")
    public String home() {
        return "home";
    }
}