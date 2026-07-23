package com.example.mediscanauth.security.handler;

import com.example.mediscanauth.service.impl.AuditLogService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LoginAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final AuditLogService auditLogService;

    public LoginAuthenticationFailureHandler(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String email = request.getParameter("email");
        String password = request.getParameter("password");

        if (isBlank(email) || isBlank(password)) {
            logFailure(email, request, "Thiếu email hoặc mật khẩu.");
            response.sendRedirect("/login?error=missing");
            return;
        }

        if (exception instanceof DisabledException) {
            logFailure(email, request, "Tài khoản bị vô hiệu hóa.");
            response.sendRedirect("/login?error=disabled");
            return;
        }

        if (exception instanceof LockedException) {
            logFailure(email, request, "Tài khoản đang bị khóa.");
            response.sendRedirect("/login?error=locked");
            return;
        }

        if (exception instanceof BadCredentialsException) {
            logFailure(email, request, "Sai email hoặc mật khẩu.");
            response.sendRedirect("/login?error=bad_credentials");
            return;
        }

        logFailure(email, request, "Lỗi đăng nhập không xác định: " + exception.getClass().getSimpleName());
        response.sendRedirect("/login?error=unknown");
    }

    private void logFailure(String attemptedEmail, HttpServletRequest request, String description) {
        String note = (isBlank(attemptedEmail) ? "" : "Email: " + attemptedEmail + ". ") + description;
        auditLogService.log(attemptedEmail, "LOGIN_FAILED", "User", null, note, request.getRemoteAddr());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}