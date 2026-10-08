package com.securebank.identity;

import com.securebank.common.events.Topics;
import com.securebank.identity.application.seed.DemoUserSeeder;
import com.securebank.identity.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "securebank.demo.seed=true")
class DemoSeedIntegrationTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    MockMvc mvc;
    @Autowired
    DemoUserSeeder seeder;

    @Test
    void seedsTheFiveDemoUsersWithFixedIds() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select u.id::text as id, u.username, u.full_name, u.email, r.name as role
                  from users u join user_roles ur on ur.user_id = u.id join roles r on r.id = ur.role_id
                 where u.id::text like '00000000-0000-4000-8000-%'
                 order by u.id""");
        assertThat(rows).extracting(r -> r.get("id")).containsExactly(
                "00000000-0000-4000-8000-000000000101", "00000000-0000-4000-8000-000000000102",
                "00000000-0000-4000-8000-000000000201", "00000000-0000-4000-8000-000000000301",
                "00000000-0000-4000-8000-000000000401");
        assertThat(rows).extracting(r -> r.get("username"))
                .containsExactly("customer1", "customer2", "staff1", "auditor1", "admin1");
        assertThat(rows).extracting(r -> r.get("role"))
                .containsExactly("CUSTOMER", "CUSTOMER", "BANK_STAFF", "AUDITOR", "ADMIN");
        assertThat(rows).extracting(r -> r.get("full_name"))
                .containsExactly("Nguyễn Văn An", "Trần Thị Bình", "Lê Minh Châu", "Phạm Quốc Dũng", "Hoàng Thu Hà");
        assertThat(rows.getFirst().get("email")).isEqualTo("customer1@demo.securebank.local");

        // banking-core seeds its own side: no UserRegistered events for demo users
        Integer events = jdbc.queryForObject("""
                select count(*) from outbox_events
                 where topic = ? and aggregate_id::text like '00000000-0000-4000-8000-%'""",
                Integer.class, Topics.USER_REGISTERED);
        assertThat(events).isZero();
    }

    @Test
    void seedingIsIdempotent() throws Exception {
        seeder.run(null);
        Integer count = jdbc.queryForObject(
                "select count(*) from users where id::text like '00000000-0000-4000-8000-%'", Integer.class);
        assertThat(count).isEqualTo(5);
    }

    @Test
    void demoPasswordsWork() throws Exception {
        for (var demo : DemoUserSeeder.DEMO_USERS) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .header("X-Forwarded-For", "10.250.0." + (demo.username().length() + 10))
                            .content("{\"username\":\"%s\",\"password\":\"%s\"}"
                                    .formatted(demo.username(), demo.password())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.id").value(demo.id().toString()))
                    .andExpect(jsonPath("$.user.roles[0]").value(demo.role().name()));
        }
    }
}
