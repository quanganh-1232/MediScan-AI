package com.example.mediscanauth.repository;

import com.example.mediscanauth.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByStatus(String status);

    List<User> findByRoleRoleNameInAndStatusOrderByFullNameAsc(List<String> roleNames, String status);

    List<User> findByRoleRoleNameInOrderByFullNameAsc(List<String> roleNames);

    /**
     * Locks the doctor's row for the rest of the caller's transaction, so a
     * concurrent request checking/booking the same doctor's schedule has to
     * wait rather than both reading a clean state and both double-booking.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userId = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
