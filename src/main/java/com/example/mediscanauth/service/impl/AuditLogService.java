package com.example.mediscanauth.service.impl;

import com.example.mediscanauth.constant.enums.FilterLogicType;
import com.example.mediscanauth.constant.enums.FilterOperation;
import com.example.mediscanauth.constant.enums.SortDirection;
import com.example.mediscanauth.dto.request.BaseFilterRequest;
import com.example.mediscanauth.dto.request.FilterCriteria;
import com.example.mediscanauth.dto.request.SortCriteria;
import com.example.mediscanauth.model.SystemLog;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.repository.SystemLogRepository;
import com.example.mediscanauth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Backs the Admin "Audit Log" screen. Reuses the generic BaseServiceImpl
 * filter/pagination framework already used by UserAdminService.
 */
@Service
public class AuditLogService extends BaseServiceImpl<SystemLog, Long> {

    private static final int DESCRIPTION_MAX_LENGTH = 2000;

    private final SystemLogRepository systemLogRepository;
    private final UserRepository userRepository;

    public AuditLogService(SystemLogRepository repository, UserRepository userRepository) {
        super(repository);
        this.systemLogRepository = repository;
        this.userRepository = userRepository;
    }

    @Autowired
    private EntityManager entityManager;

    @Override
    public EntityManager getEntityManager() {
        return entityManager;
    }

    /**
     * Writes one audit entry. Never throws — audit logging must not break the
     * primary action it is recording (e.g. login, staff creation).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String actorEmail, String action, String entityName, String entityId,
                     String description, String ipAddress) {
        try {
            User actor = (actorEmail == null || actorEmail.isBlank())
                    ? null
                    : userRepository.findByEmail(actorEmail.trim().toLowerCase(Locale.ROOT)).orElse(null);

            SystemLog entry = SystemLog.builder()
                    .user(actor)
                    .action(action)
                    .entityName(entityName)
                    .entityId(entityId)
                    .description(truncate(description))
                    .ipAddress(ipAddress)
                    .createdAt(Instant.now())
                    .build();
            systemLogRepository.save(entry);
        } catch (Exception ex) {
            System.err.println("[AuditLog] Failed to write audit entry (" + action + "): " + ex.getMessage());
        }
    }

    public void log(String actorEmail, String action, String entityName, String entityId, String description) {
        log(actorEmail, action, entityName, entityId, description, null);
    }

    @Transactional(readOnly = true)
    public Page<SystemLog> filterLogs(String keyword, String action, String entityName,
                                       LocalDate fromDate, LocalDate toDate,
                                       Integer page, Integer size) {
        return filter(buildFilterRequest(keyword, action, entityName, fromDate, toDate, page, size));
    }

    @Transactional(readOnly = true)
    public List<String> distinctActions() {
        return systemLogRepository.findAll().stream()
                .map(SystemLog::getAction)
                .filter(a -> a != null && !a.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private BaseFilterRequest buildFilterRequest(String keyword, String action, String entityName,
                                                  LocalDate fromDate, LocalDate toDate,
                                                  Integer page, Integer size) {
        List<FilterCriteria> filters = new ArrayList<>();
        String normalizedKeyword = normalize(keyword);
        String normalizedAction = normalize(action);
        String normalizedEntity = normalize(entityName);

        if (!normalizedKeyword.isBlank()) {
            filters.add(keywordFilter("user.fullName", normalizedKeyword));
            filters.add(keywordFilter("user.email", normalizedKeyword));
            filters.add(keywordFilter("description", normalizedKeyword));
            filters.add(keywordFilter("entityId", normalizedKeyword));
        }

        if (!normalizedAction.isBlank()) {
            filters.add(FilterCriteria.builder()
                    .fieldName("action")
                    .operation(FilterOperation.EQUALS)
                    .value(normalizedAction)
                    .build());
        }

        if (!normalizedEntity.isBlank()) {
            filters.add(FilterCriteria.builder()
                    .fieldName("entityName")
                    .operation(FilterOperation.EQUALS)
                    .value(normalizedEntity)
                    .build());
        }

        if (fromDate != null) {
            filters.add(FilterCriteria.builder()
                    .fieldName("createdAt")
                    .operation(FilterOperation.GREATER_THAN_OR_EQUAL)
                    .value(fromDate.atStartOfDay(ZoneId.systemDefault()).toInstant())
                    .build());
        }

        if (toDate != null) {
            filters.add(FilterCriteria.builder()
                    .fieldName("createdAt")
                    .operation(FilterOperation.LESS_THAN_OR_EQUAL)
                    .value(toDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant())
                    .build());
        }

        return BaseFilterRequest.builder()
                .filters(filters)
                .sorts(List.of(SortCriteria.builder()
                        .fieldName("createdAt")
                        .direction(SortDirection.DESC)
                        .build()))
                .page(safePage(page))
                .size(safeSize(size))
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

    private String truncate(String value) {
        if (value == null) return null;
        return value.length() > DESCRIPTION_MAX_LENGTH ? value.substring(0, DESCRIPTION_MAX_LENGTH) : value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private int safePage(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    private int safeSize(Integer size) {
        if (size == null || size < 1) return 20;
        return Math.min(size, 100);
    }
}
