package com.example.mediscanauth.service.impl;

import com.example.mediscanauth.constant.enums.FilterLogicType;
import com.example.mediscanauth.constant.enums.FilterOperation;
import com.example.mediscanauth.constant.enums.SortDirection;
import com.example.mediscanauth.dto.request.BaseFilterRequest;
import com.example.mediscanauth.dto.request.FilterCriteria;
import com.example.mediscanauth.dto.request.SortCriteria;
import com.example.mediscanauth.model.SupportRequest;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.repository.SupportRequestRepository;
import com.example.mediscanauth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Backs both the patient-facing "Support" submission form and the Admin
 * "Support / Feedback Management" screen. The admin list reuses the generic
 * BaseServiceImpl filter/pagination framework, same as UserAdminService.
 */
@Service
public class SupportRequestService extends BaseServiceImpl<SupportRequest, Long> {

    public static final List<String> STATUSES = List.of("OPEN", "IN_PROGRESS", "RESOLVED");

    private final SupportRequestRepository supportRequestRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public SupportRequestService(SupportRequestRepository repository, UserRepository userRepository,
                                  AuditLogService auditLogService) {
        super(repository);
        this.supportRequestRepository = repository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @Autowired
    private EntityManager entityManager;

    @Override
    public EntityManager getEntityManager() {
        return entityManager;
    }

    @Transactional
    public SupportRequest createRequest(User patient, String subject, String message) {
        if (isBlank(subject) || isBlank(message)) {
            throw new IllegalArgumentException("Vui lòng nhập đầy đủ tiêu đề và nội dung yêu cầu.");
        }
        SupportRequest request = SupportRequest.builder()
                .patient(patient)
                .subject(subject.trim())
                .message(message.trim())
                .status("OPEN")
                .build();
        return supportRequestRepository.save(request);
    }

    @Transactional(readOnly = true)
    public List<SupportRequest> findForPatient(User patient) {
        return supportRequestRepository.findByPatientOrderByCreatedAtDesc(patient);
    }

    @Transactional(readOnly = true)
    public Page<SupportRequest> filterRequests(String keyword, String status, Integer page, Integer size) {
        return filter(buildFilterRequest(keyword, status, page, size));
    }

    @Transactional(readOnly = true)
    public long countByStatus(String status) {
        return supportRequestRepository.countByStatus(status);
    }

    @Transactional
    public SupportRequest respond(Long requestId, String adminEmail, String responseText, String newStatus) {
        SupportRequest request = getOne(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu hỗ trợ."));
        String normalizedStatus = normalize(newStatus).toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException("Trạng thái không hợp lệ.");
        }
        User admin = userRepository.findByEmail(adminEmail).orElse(null);
        request.setAdminResponse(isBlank(responseText) ? request.getAdminResponse() : responseText.trim());
        request.setRespondedBy(admin);
        request.setRespondedAt(LocalDateTime.now());
        request.setStatus(normalizedStatus);
        SupportRequest saved = update(request);

        auditLogService.log(adminEmail, "SUPPORT_RESPONDED", "SupportRequest", String.valueOf(requestId),
                "Phản hồi yêu cầu hỗ trợ \"" + saved.getSubject() + "\", trạng thái: " + normalizedStatus + ".");
        return saved;
    }

    private BaseFilterRequest buildFilterRequest(String keyword, String status, Integer page, Integer size) {
        List<FilterCriteria> filters = new ArrayList<>();
        String normalizedKeyword = normalize(keyword);
        String normalizedStatus = normalize(status).toUpperCase(Locale.ROOT);

        if (!normalizedKeyword.isBlank()) {
            filters.add(keywordFilter("subject", normalizedKeyword));
            filters.add(keywordFilter("message", normalizedKeyword));
            filters.add(keywordFilter("patient.fullName", normalizedKeyword));
            filters.add(keywordFilter("patient.email", normalizedKeyword));
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

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private int safePage(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    private int safeSize(Integer size) {
        if (size == null || size < 1) return 20;
        return Math.min(size, 100);
    }
}
