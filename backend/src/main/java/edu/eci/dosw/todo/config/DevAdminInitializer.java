package edu.eci.dosw.todo.config;

import edu.eci.dosw.todo.entity.Role;
import edu.eci.dosw.todo.entity.UserEntity;
import edu.eci.dosw.todo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * LOCAL DEVELOPMENT ONLY. Creates an ADMIN account at startup when both ADMIN_EMAIL and ADMIN_PASSWORD
 * are defined in the environment. Without them nothing happens, and there is no public way to obtain
 * the ADMIN role.
 */
@Component
public class DevAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevAdminInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public DevAdminInitializer(UserRepository userRepository,
                               PasswordEncoder passwordEncoder,
                               @Value("${app.admin.email:}") String adminEmail,
                               @Value("${app.admin.password:}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            return;
        }
        String email = adminEmail.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            return;
        }
        UserEntity admin = new UserEntity();
        admin.setName("Administrator");
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(Role.ADMIN);
        admin.setCreatedAt(LocalDateTime.now());
        userRepository.save(admin);
        log.info("Development ADMIN account created for {}", email);
    }
}
