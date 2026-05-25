package com.example.mediscanauth.security;

import com.example.mediscanauth.security.handler.LoginAuthenticationFailureHandler;
import com.example.mediscanauth.security.handler.RoleBasedAuthenticationSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final RoleBasedAuthenticationSuccessHandler roleBasedAuthenticationSuccessHandler;
    private final LoginAuthenticationFailureHandler loginAuthenticationFailureHandler;

    public SecurityConfig(CustomOAuth2UserService customOAuth2UserService,
                          RoleBasedAuthenticationSuccessHandler roleBasedAuthenticationSuccessHandler,
                          LoginAuthenticationFailureHandler loginAuthenticationFailureHandler) {
        this.customOAuth2UserService = customOAuth2UserService;
        this.roleBasedAuthenticationSuccessHandler = roleBasedAuthenticationSuccessHandler;
        this.loginAuthenticationFailureHandler = loginAuthenticationFailureHandler;
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
                        .successHandler(roleBasedAuthenticationSuccessHandler)
                        .failureHandler(loginAuthenticationFailureHandler)
                        .permitAll()
                )
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(roleBasedAuthenticationSuccessHandler)
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );

        return http.build();
    }
}
