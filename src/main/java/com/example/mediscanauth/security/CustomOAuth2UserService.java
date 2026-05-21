package com.example.mediscanauth.security;

import com.example.mediscanauth.model.User;
import com.example.mediscanauth.repository.UserRepository;
import com.example.mediscanauth.service.RoleService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.UUID;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;

    public CustomOAuth2UserService(UserRepository userRepository,
                                   RoleService roleService,
                                   PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleService = roleService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauthUser = super.loadUser(userRequest);

        String email = normalize(oauthUser.getAttribute("email"));
        if (email.isBlank()) {
            throw new OAuth2AuthenticationException("Google account does not expose an email.");
        }

        User user = userRepository.findByEmail(email).orElseGet(User::new);
        if (user.getUserId() == null) {
            user.setEmail(email);
            user.setRole(roleService.getOrCreatePatientRole());
            user.setStatus("ACTIVE");
            user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        }

        user.setFullName(defaultString(oauthUser.getAttribute("name"), email));
        user.setAuthProvider("GOOGLE");
        user.setProviderId(oauthUser.getAttribute("sub"));
        user.setAvatarUrl(oauthUser.getAttribute("picture"));
        userRepository.save(user);

        String roleName = user.getRole().getRoleName();
        if (!roleName.startsWith("ROLE_")) {
            roleName = "ROLE_" + roleName;
        }

        return new DefaultOAuth2User(
                Collections.singletonList(new SimpleGrantedAuthority(roleName)),
                oauthUser.getAttributes(),
                "email"
        );
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
