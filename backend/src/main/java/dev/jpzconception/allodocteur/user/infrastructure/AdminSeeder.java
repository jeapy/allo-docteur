package dev.jpzconception.allodocteur.user.infrastructure;

import dev.jpzconception.allodocteur.shared.config.AppProperties;
import dev.jpzconception.allodocteur.user.domain.AdminLevel;
import dev.jpzconception.allodocteur.user.domain.Role;
import dev.jpzconception.allodocteur.user.domain.User;
import dev.jpzconception.allodocteur.user.domain.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Crée le compte administrateur initial si APP_ADMIN_EMAIL / APP_ADMIN_PASSWORD sont fournis. */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public AdminSeeder(UserRepository users, PasswordEncoder encoder, AppProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = props.admin().email();
        String password = props.admin().password();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.warn("Aucun compte admin initial : APP_ADMIN_EMAIL / APP_ADMIN_PASSWORD non renseignés.");
            return;
        }
        if (users.existsByEmailIgnoreCase(email)) {
            return;
        }
        User admin = new User();
        admin.setEmail(email.trim().toLowerCase());
        admin.setPasswordHash(encoder.encode(password));
        admin.setRole(Role.ADMIN);
        admin.setAdminLevel(AdminLevel.SUPER);
        admin.setFirstName("Admin");
        admin.setLastName("Allo Docteur");
        admin.setStatus(UserStatus.ACTIVE);
        users.save(admin);
        log.info("Compte administrateur initial créé : {}", admin.getEmail());
    }
}
