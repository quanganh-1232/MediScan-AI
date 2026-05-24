package com.example.mediscanauth.service;

import com.example.mediscanauth.model.Role;

public interface RoleService {

    Role getOrCreatePatientRole();

    Role getOrCreateRole(String roleName, String description);
}
