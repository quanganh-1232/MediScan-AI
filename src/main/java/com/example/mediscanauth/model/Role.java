package com.example.mediscanauth.model;

import jakarta.persistence.*;

@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Long roleId;

    @Column(name = "role_name", nullable = false, unique = true)
    private String roleName;

    @Column(name = "description")
    private String description;

    public Role() {
    }

    public Role(String roleName, String description) {
        this.roleName = roleName;
        this.description = description;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDisplayName() {
        if (roleName == null) {
            return "Người dùng";
        }

        return switch (roleName) {
            case "ADMIN", "ROLE_ADMIN" -> "Quản trị viên";
            case "DOCTOR", "ROLE_DOCTOR" -> "Bác sĩ";
            case "TECHNICIAN", "ROLE_TECHNICIAN" -> "Kỹ thuật viên";
            case "PATIENT", "ROLE_PATIENT" -> "Bệnh nhân";
            default -> "Người dùng";
        };
    }
}
