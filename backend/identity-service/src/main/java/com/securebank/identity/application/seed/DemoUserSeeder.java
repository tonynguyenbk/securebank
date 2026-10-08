package com.securebank.identity.application.seed;

import com.securebank.common.security.Role;
import com.securebank.identity.domain.RoleEntity;
import com.securebank.identity.domain.User;
import com.securebank.identity.repository.RoleRepository;
import com.securebank.identity.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Inserts the demo users of api.md §7 with fixed UUIDs (so banking-core can seed matching customers
 * independently). Insert-if-absent, runs only with {@code securebank.demo.seed=true}. No UserRegisteredEvent
 * is emitted: banking-core seeds its own side.
 */
@Component
@ConditionalOnProperty(name = "securebank.demo.seed", havingValue = "true")
public class DemoUserSeeder implements ApplicationRunner {

    public static final List<DemoUser> DEMO_USERS = List.of(
            new DemoUser(UUID.fromString("00000000-0000-4000-8000-000000000101"), "customer1", "Customer@123",
                    "Nguyễn Văn An", Role.CUSTOMER),
            new DemoUser(UUID.fromString("00000000-0000-4000-8000-000000000102"), "customer2", "Customer@123",
                    "Trần Thị Bình", Role.CUSTOMER),
            new DemoUser(UUID.fromString("00000000-0000-4000-8000-000000000201"), "staff1", "Staff@123",
                    "Lê Minh Châu", Role.BANK_STAFF),
            new DemoUser(UUID.fromString("00000000-0000-4000-8000-000000000301"), "auditor1", "Auditor@123",
                    "Phạm Quốc Dũng", Role.AUDITOR),
            new DemoUser(UUID.fromString("00000000-0000-4000-8000-000000000401"), "admin1", "Admin@123",
                    "Hoàng Thu Hà", Role.ADMIN));

    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate tx;
    private final Clock clock;

    public DemoUserSeeder(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
                          PlatformTransactionManager transactionManager, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        int created = 0;
        for (DemoUser demo : DEMO_USERS) {
            Boolean inserted = tx.execute(status -> insertIfAbsent(demo));
            if (Boolean.TRUE.equals(inserted)) {
                created++;
            }
        }
        log.info("Demo seed: {} demo user(s) created, {} already present", created, DEMO_USERS.size() - created);
    }

    private boolean insertIfAbsent(DemoUser demo) {
        if (users.existsById(demo.id()) || users.existsByUsername(demo.username())) {
            return false;
        }
        RoleEntity role = roles.findByName(demo.role())
                .orElseThrow(() -> new IllegalStateException("Role " + demo.role() + " missing"));
        users.save(new User(demo.id(), demo.username(), passwordEncoder.encode(demo.password()), demo.fullName(),
                demo.username() + "@demo.securebank.local", null, Set.of(role), clock.instant()));
        return true;
    }

    public record DemoUser(UUID id, String username, String password, String fullName, Role role) {
        @Override
        public String toString() {
            return "DemoUser[id=" + id + ", username=" + username + ", role=" + role + "]";
        }
    }
}
