package com.example.mediscanauth.security;

import com.example.mediscanauth.model.User;
import com.example.mediscanauth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class LegacyPasswordMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyPasswordMigrationRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public LegacyPasswordMigrationRunner(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        int migratedCount = 0;

        for (User user : userRepository.findAll()) {
            String storedPassword = user.getPasswordHash();
            if (storedPassword == null || storedPassword.isBlank() || isBcryptHash(storedPassword)) {
                continue;
            }

            user.setPasswordHash(passwordEncoder.encode(storedPassword));
            userRepository.save(user);
            migratedCount++;
        }

        if (migratedCount > 0) {
            log.info("Migrated {} legacy plaintext password(s) to BCrypt.", migratedCount);
        }
    }

    private boolean isBcryptHash(String value) {
        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
    }
}
