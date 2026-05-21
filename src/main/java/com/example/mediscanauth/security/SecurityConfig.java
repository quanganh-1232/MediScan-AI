package com.example.mediscanauth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;

    public SecurityConfig(CustomOAuth2UserService customOAuth2UserService) {
        this.customOAuth2UserService = customOAuth2UserService;
    }

        @Bean
        public AuthenticationSuccessHandler roleBasedSuccessHandler() {
                return new AuthenticationSuccessHandler() {
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
                };
        }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/", "/login", "/register", "/css/**", "/js/**", "/images/**").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/doctor/**").hasRole("DOCTOR")
                        .requestMatchers("/technician/**").hasRole("TECHNICIAN")
                        .requestMatchers("/patient/**").hasRole("PATIENT")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler(roleBasedSuccessHandler())
                        .failureUrl("/login?error")
                        .permitAll()
                )
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(roleBasedSuccessHandler())
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );

        return http.build();
    }
}
