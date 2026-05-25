package com.example.mediscanauth.security.handler;

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

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String targetUrl = "/home";

        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String role = authority.getAuthority();
            if ("ROLE_ADMIN".equals(role)) {
                targetUrl = "/admin/dashboard";
                break;
            }
            if ("ROLE_DOCTOR".equals(role)) {
                targetUrl = "/doctor/dashboard";
                break;
            }
            if ("ROLE_TECHNICIAN".equals(role)) {
                targetUrl = "/technician/dashboard";
                break;
            }
            if ("ROLE_PATIENT".equals(role)) {
                targetUrl = "/patient/dashboard";
                break;
            }
        }

        response.sendRedirect(targetUrl);
    }
}