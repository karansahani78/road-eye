package com.roadeye.auth.config;

import com.roadeye.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aligns seeded default users with documented credentials (admin123 / operator123).
 * Only updates accounts that still use the legacy seed password hash for "password".
 */
@Component
public class DefaultUserPasswordBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultUserPasswordBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DefaultUserPasswordBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        migrateLegacyPassword("admin", "admin123");
        migrateLegacyPassword("operator", "operator123");
    }

    private void migrateLegacyPassword(String username, String newPlainPassword) {
        userRepository.findByUsername(username).ifPresent(user -> {
            if (passwordEncoder.matches("password", user.getPassword())) {
                user.setPassword(passwordEncoder.encode(newPlainPassword));
                userRepository.save(user);
                log.info("Updated default password for user '{}'", username);
            }
        });
    }
}
