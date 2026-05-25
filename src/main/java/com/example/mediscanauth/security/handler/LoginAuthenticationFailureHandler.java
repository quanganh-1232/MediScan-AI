package com.example.mediscanauth.security.handler;

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

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String email = request.getParameter("email");
        String password = request.getParameter("password");

        if (isBlank(email) || isBlank(password)) {
            response.sendRedirect("/login?error=missing");
            return;
        }

        if (exception instanceof DisabledException) {
            response.sendRedirect("/login?error=disabled");
            return;
        }

        if (exception instanceof LockedException) {
            response.sendRedirect("/login?error=locked");
            return;
        }

        if (exception instanceof BadCredentialsException) {
            response.sendRedirect("/login?error=bad_credentials");
            return;
        }

        response.sendRedirect("/login?error=unknown");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}