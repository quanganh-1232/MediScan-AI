package com.example.mediscanauth.repository;

import com.example.mediscanauth.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByStatus(String status);

    List<User> findByRoleRoleNameInAndStatusOrderByFullNameAsc(List<String> roleNames, String status);

    List<User> findByRoleRoleNameInOrderByFullNameAsc(List<String> roleNames);
}
