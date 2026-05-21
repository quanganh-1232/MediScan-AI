package com.example.mediscanauth.service;

import com.example.mediscanauth.model.Role;
import com.example.mediscanauth.repository.RoleRepository;
import org.springframework.stereotype.Service;

@Service
public class RoleService {

    private final RoleRepository roleRepository;

    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public Role getOrCreatePatientRole() {
        return getOrCreateRole("PATIENT", "Patient account");
    }

    public Role getOrCreateRole(String roleName, String description) {
        return roleRepository.findByRoleName(roleName)
                .orElseGet(() -> roleRepository.save(new Role(roleName, description)));
    }
}
