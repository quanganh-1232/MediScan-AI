package com.example.mediscanauth.service.impl;

import com.example.mediscanauth.model.Role;
import com.example.mediscanauth.repository.RoleRepository;
import com.example.mediscanauth.service.RoleService;
import org.springframework.stereotype.Service;

@Service
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;

    public RoleServiceImpl(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    public Role getOrCreatePatientRole() {
        return getOrCreateRole("PATIENT", "Patient account");
    }

    @Override
    public Role getOrCreateRole(String roleName, String description) {
        return roleRepository.findByRoleName(roleName)
                .orElseGet(() -> roleRepository.save(new Role(null, roleName, description)));
    }
}