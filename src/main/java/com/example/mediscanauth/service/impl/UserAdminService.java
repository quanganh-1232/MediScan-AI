package com.example.mediscanauth.service.impl;

import com.example.mediscanauth.constant.enums.FilterLogicType;
import com.example.mediscanauth.constant.enums.FilterOperation;
import com.example.mediscanauth.constant.enums.SortDirection;
import com.example.mediscanauth.dto.request.BaseFilterRequest;
import com.example.mediscanauth.dto.request.FilterCriteria;
import com.example.mediscanauth.dto.request.SortCriteria;
import com.example.mediscanauth.model.Role;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.repository.RoleRepository;
import com.example.mediscanauth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class UserAdminService extends BaseServiceImpl<User, Long> {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_LOCKED = "LOCKED";

    private final RoleRepository roleRepository;
    private final UserRepository userRepository = (UserRepository) this.repository;
    private final PasswordEncoder passwordEncoder;

    public UserAdminService(UserRepository repository, RoleRepository roleRepository) {
        super(repository);
        this.roleRepository = roleRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Autowired
    private EntityManager entityManager;

    @Override
    public EntityManager getEntityManager() {
        return entityManager;
    }

    @Transactional(readOnly = true)
    public Page<User> filterUsers(String keyword, String roleName, String status, Integer page, Integer size) {
        return filter(buildUserFilterRequest(keyword, roleName, status, page, size));
    }

    @Transactional(readOnly = true)
    public List<User> findLatestUsers(int size) {
        return filterUsers(null, null, null, 0, size).getContent();
    }

    @Transactional(readOnly = true)
    public User getUserDetail(Long userId) {
        return getOne(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    @Transactional(readOnly = true)
    public List<Role> getAssignableRoles() {
        return roleRepository.findAll(Sort.by(Sort.Direction.ASC, "roleName"));
    }

    @Transactional(readOnly = true)
    public long countByStatusUsingFilter(String status) {
        return filter(buildStatusFilterRequest(status)).getTotalElements();
    }

    @Transactional
    public User lockAccount(Long userId, String currentAdminEmail) {
        User user = getUserDetail(userId);
        preventSelfLock(user, currentAdminEmail);
        user.setStatus(STATUS_LOCKED);
        return update(user);
    }

    @Transactional
    public User unlockAccount(Long userId) {
        User user = getUserDetail(userId);
        user.setStatus(STATUS_ACTIVE);
        return update(user);
    }

    @Transactional
    public User assignUserRole(Long userId, String roleName, String currentAdminEmail) {
        User user = getUserDetail(userId);
        String normalizedRoleName = normalizeRoleName(roleName);
        preventSelfDemotion(user, normalizedRoleName, currentAdminEmail);

        Role role = roleRepository.findByRoleName(normalizedRoleName)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + normalizedRoleName));
        user.setRole(role);
        return update(user);
    }

    @Transactional
    public User createStaff(String fullName, String email, String phone, String rawPassword, String roleName) {
        String normalizedEmail = normalizeText(email).toLowerCase(Locale.ROOT);
        validateStaffInput(fullName, normalizedEmail, rawPassword);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email đã tồn tại trong hệ thống.");
        }

        String normalizedRole = requireStaffRole(roleName);
        Role role = roleRepository.findByRoleName(normalizedRole)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vai trò " + normalizedRole));
        User user = new User();
        user.setFullName(normalizeText(fullName));
        user.setEmail(normalizedEmail);
        user.setPhone(normalizeText(phone));
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setStatus(STATUS_ACTIVE);
        user.setAuthProvider("LOCAL");
        return userRepository.save(user);
    }

    @Transactional
    public User updateStaff(Long userId, String fullName, String email, String phone, String expectedRole) {
        User user = getUserDetail(userId);
        requireUserRole(user, expectedRole);
        String normalizedEmail = normalizeText(email).toLowerCase(Locale.ROOT);
        if (normalizeText(fullName).isBlank() || normalizedEmail.isBlank()) {
            throw new IllegalArgumentException("Họ tên và email không được để trống.");
        }
        userRepository.findByEmail(normalizedEmail)
                .filter(existing -> !existing.getUserId().equals(userId))
                .ifPresent(existing -> { throw new IllegalArgumentException("Email đã tồn tại trong hệ thống."); });
        user.setFullName(normalizeText(fullName));
        user.setEmail(normalizedEmail);
        user.setPhone(normalizeText(phone));
        return userRepository.save(user);
    }

    @Transactional
    public User updateStaffStatus(Long userId, String status, String expectedRole) {
        User user = getUserDetail(userId);
        requireUserRole(user, expectedRole);
        String normalizedStatus = normalizeText(status).toUpperCase(Locale.ROOT);
        if (!List.of(STATUS_ACTIVE, STATUS_LOCKED).contains(normalizedStatus)) {
            throw new IllegalArgumentException("Trạng thái không hợp lệ.");
        }
        user.setStatus(normalizedStatus);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<User> findStaffByRole(String roleName, String keyword) {
        return filterUsers(keyword, roleName, null, 0, 100).getContent();
    }

    @Transactional
    public void deleteStaff(Long userId, String expectedRole) {
        User user = getUserDetail(userId);
        requireUserRole(user, expectedRole);
        userRepository.delete(user);
    }

    private BaseFilterRequest buildUserFilterRequest(String keyword,
                                                     String roleName,
                                                     String status,
                                                     Integer page,
                                                     Integer size) {
        List<FilterCriteria> filters = new ArrayList<>();
        String normalizedKeyword = normalizeText(keyword).toLowerCase(Locale.ROOT);
        String normalizedRoleName = normalizeRoleName(roleName);
        String normalizedStatus = normalizeText(status).toUpperCase(Locale.ROOT);

        if (!normalizedKeyword.isBlank()) {
            filters.add(keywordFilter("fullName", normalizedKeyword));
            filters.add(keywordFilter("email", normalizedKeyword));
            filters.add(keywordFilter("phone", normalizedKeyword));
        }

        if (!normalizedRoleName.isBlank()) {
            filters.add(FilterCriteria.builder()
                    .fieldName("role.roleName")
                    .operation(FilterOperation.EQUALS)
                    .value(normalizedRoleName)
                    .build());
        }

        if (!normalizedStatus.isBlank()) {
            filters.add(FilterCriteria.builder()
                    .fieldName("status")
                    .operation(FilterOperation.EQUALS)
                    .value(normalizedStatus)
                    .build());
        }

        return BaseFilterRequest.builder()
                .filters(filters)
                .sorts(List.of(SortCriteria.builder()
                        .fieldName("userId")
                        .direction(SortDirection.DESC)
                        .build()))
                .page(safePage(page))
                .size(safeSize(size))
                .build();
    }

    private BaseFilterRequest buildStatusFilterRequest(String status) {
        String normalizedStatus = normalizeText(status).toUpperCase(Locale.ROOT);
        List<FilterCriteria> filters = normalizedStatus.isBlank()
                ? List.of()
                : List.of(FilterCriteria.builder()
                        .fieldName("status")
                        .operation(FilterOperation.EQUALS)
                        .value(normalizedStatus)
                        .build());

        return BaseFilterRequest.builder()
                .filters(filters)
                .page(0)
                .size(1)
                .build();
    }

    private FilterCriteria keywordFilter(String fieldName, String keyword) {
        return FilterCriteria.builder()
                .fieldName(fieldName)
                .operation(FilterOperation.ILIKE)
                .value(keyword)
                .logicType(FilterLogicType.OR)
                .build();
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeRoleName(String roleName) {
        return normalizeText(roleName).toUpperCase(Locale.ROOT);
    }

    private void validateStaffInput(String fullName, String email, String rawPassword) {
        if (normalizeText(fullName).isBlank() || email.isBlank()) {
            throw new IllegalArgumentException("Họ tên và email không được để trống.");
        }
        if (rawPassword == null || rawPassword.length() < 6) {
            throw new IllegalArgumentException("Mật khẩu phải có ít nhất 6 ký tự.");
        }
    }

    private String requireStaffRole(String roleName) {
        String normalizedRole = normalizeRoleName(roleName);
        if (!List.of("DOCTOR", "TECHNICIAN").contains(normalizedRole)) {
            throw new IllegalArgumentException("Vai trò nhân sự không hợp lệ.");
        }
        return normalizedRole;
    }

    private void requireUserRole(User user, String expectedRole) {
        String normalizedRole = requireStaffRole(expectedRole);
        if (user.getRole() == null || !normalizedRole.equals(user.getRole().getRoleName())) {
            throw new IllegalArgumentException("Người dùng không thuộc nhóm " + normalizedRole + ".");
        }
    }

    private int safePage(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    private int safeSize(Integer size) {
        if (size == null) {
            return 20;
        }
        if (size < 1) {
            return 20;
        }
        return Math.min(size, 100);
    }

    private void preventSelfLock(User user, String currentAdminEmail) {
        if (isSameUser(user, currentAdminEmail)) {
            throw new IllegalArgumentException("You cannot lock the account that is currently signed in.");
        }
    }

    private void preventSelfDemotion(User user, String newRoleName, String currentAdminEmail) {
        if (isSameUser(user, currentAdminEmail) && !"ADMIN".equals(newRoleName)) {
            throw new IllegalArgumentException("You cannot remove ADMIN role from the account that is currently signed in.");
        }
    }

    private boolean isSameUser(User user, String email) {
        return user.getEmail() != null && user.getEmail().equalsIgnoreCase(normalizeText(email));
    }

    public User createUserFromImport(String fullName, String email, String phone,
                                     String rawPassword, String roleName, String status) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists: " + email);
        }
        Role role = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Invalid role: " + roleName));

        User user = new User();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setStatus(status);
        user.setAuthProvider("LOCAL");
        return userRepository.save(user);
    }
}
