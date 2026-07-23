package com.example.mediscanauth.security.handler;

import com.example.mediscanauth.service.impl.AuditLogService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RoleBasedAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final AuditLogService auditLogService;

    public RoleBasedAuthenticationSuccessHandler(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        auditLogService.log(authentication.getName(), "LOGIN_SUCCESS", "User", null,
                "Đăng nhập thành công.", request.getRemoteAddr());

        // Redirect all roles to the unified dashboard
        String targetUrl = "/home";

        response.sendRedirect(targetUrl);
    }
}