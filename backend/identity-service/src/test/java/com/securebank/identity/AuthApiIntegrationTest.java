package com.securebank.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.securebank.common.events.Topics;
import com.securebank.common.security.JwtTokenValidator;
import com.securebank.common.security.TokenRevocationChecker;
import com.securebank.identity.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiIntegrationTest extends IntegrationTest {

    private static final String PASSWORD = "Str0ng!Pass";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    JwtTokenValidator validator;

    // Each test uses its own client IP so the per-IP login counter of one test never affects another.
    private final String clientIp = "10." + ThreadLocalRandom.current().nextInt(256) + "."
            + ThreadLocalRandom.current().nextInt(256) + "." + ThreadLocalRandom.current().nextInt(1, 255);

    @Test
    void registerLoginAndMe() throws Exception {
        String username = uniqueUsername();
        JsonNode registered = body(register(username, PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.fullName").value("Test Người Dùng"))
                .andExpect(jsonPath("$.email").value(username + "@example.com"))
                .andExpect(jsonPath("$.phone").value("+84901234567"))
                .andExpect(jsonPath("$.roles", containsInAnyOrder("CUSTOMER")))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.passwordHash").doesNotExist()));
        String userId = registered.get("id").asText();

        JsonNode tokens = body(login(username, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshExpiresIn").value(604800))
                .andExpect(jsonPath("$.user.id").value(userId)));
        String accessToken = tokens.get("accessToken").asText();
        assertThat(tokens.get("refreshToken").asText()).matches("[A-Za-z0-9_-]{43}");

        var principal = validator.validate(accessToken).orElseThrow();
        assertThat(principal.userId()).hasToString(userId);
        assertThat(principal.username()).isEqualTo(username);

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"));

        // the refresh token is stored only as a SHA-256 hash
        Integer plainStored = jdbc.queryForObject("select count(*) from refresh_tokens where token_hash = ?",
                Integer.class, tokens.get("refreshToken").asText());
        assertThat(plainStored).isZero();
        // and the password only as a BCrypt hash
        String hash = jdbc.queryForObject("select password_hash from users where id = ?::uuid", String.class, userId);
        assertThat(hash).startsWith("$2").doesNotContain(PASSWORD);
    }

    @Test
    void meWithoutTokenIs401ApiError() throws Exception {
        mvc.perform(get("/api/v1/auth/me").header("X-Correlation-Id", "test-corr-0001"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-Id", "test-corr-0001"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/me"))
                .andExpect(jsonPath("$.correlationId").value("test-corr-0001"));
    }

    @Test
    void wrongPasswordIs401AndAuditsFailureWithActor() throws Exception {
        String username = uniqueUsername();
        String userId = body(register(username, PASSWORD).andExpect(status().isCreated())).get("id").asText();

        login(username, "Wrong!Pass1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));

        List<JsonNode> failures = auditEvents(userId, "LOGIN_FAILURE");
        assertThat(failures).hasSize(1);
        JsonNode failure = failures.getFirst();
        assertThat(failure.get("actorUserId").asText()).isEqualTo(userId);
        assertThat(failure.get("actorUsername").asText()).isEqualTo(username);
        assertThat(failure.get("actorRole").asText()).isEqualTo("CUSTOMER");
        assertThat(failure.get("outcome").asText()).isEqualTo("FAILURE");
        assertThat(failure.get("resourceType").asText()).isEqualTo("USER");
        assertThat(failure.get("ipAddress").asText()).isEqualTo(clientIp);
        assertThat(failure.get("sourceService").asText()).isEqualTo("identity-service");
        assertThat(failure.toString()).doesNotContain("Wrong!Pass1");
    }

    @Test
    void unknownUsernameIs401AndAuditsFailureWithoutActor() throws Exception {
        String username = uniqueUsername();
        login(username, PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));

        List<Map<String, Object>> rows = jdbc.queryForList("""
                select payload::text as payload from outbox_events
                 where topic = ? and payload->>'action' = 'LOGIN_FAILURE'
                   and payload->'after'->>'attemptedUsername' = ?""", Topics.AUDIT_EVENT, username);
        assertThat(rows).hasSize(1);
        JsonNode event = json.readTree((String) rows.getFirst().get("payload"));
        assertThat(event.get("actorUserId").isNull()).isTrue();
        assertThat(event.get("resourceId").isNull()).isTrue();
    }

    @Test
    void validationErrorsAre400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"Bad User!","password":"weakpass","fullName":"",
                                 "email":"not-an-email","phone":"12ab"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field",
                        hasItem("username")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("password")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("fullName")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("phone")))
                .andExpect(jsonPath("$.fieldErrors[*].message", not(hasItem(nullValue()))));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void duplicateUsernameIs409() throws Exception {
        String username = uniqueUsername();
        register(username, PASSWORD).andExpect(status().isCreated());
        register(username, PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_USERNAME_TAKEN"));
    }

    @Test
    void passwordLongerThan72BytesWorks() throws Exception {
        String username = uniqueUsername();
        String longPassword = "Aa1!" + "x".repeat(90); // 94 chars, allowed by the contract (8–100)
        register(username, longPassword).andExpect(status().isCreated());
        login(username, longPassword).andExpect(status().isOk());
        login(username, longPassword.substring(0, 80)).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesAndReuseRevokesTheFamily() throws Exception {
        String username = uniqueUsername();
        register(username, PASSWORD).andExpect(status().isCreated());
        String r1 = body(login(username, PASSWORD).andExpect(status().isOk())).get("refreshToken").asText();

        JsonNode second = body(refresh(r1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value(username)));
        String r2 = second.get("refreshToken").asText();
        assertThat(r2).isNotEqualTo(r1);
        assertThat(validator.validate(second.get("accessToken").asText())).isPresent();

        // r1 was consumed: presenting it again is reuse -> rejected, and r2 is revoked as well
        refresh(r1).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_TOKEN_INVALID"));
        refresh(r2).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_TOKEN_INVALID"));

        refresh("not-a-real-token").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_TOKEN_INVALID"));
    }

    @Test
    void logoutRejectsAccessAndRefreshToken() throws Exception {
        String username = uniqueUsername();
        String userId = body(register(username, PASSWORD).andExpect(status().isCreated())).get("id").asText();
        JsonNode tokens = body(login(username, PASSWORD).andExpect(status().isOk()));
        String access = tokens.get("accessToken").asText();
        String refreshToken = tokens.get("refreshToken").asText();

        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + access)
                        .header("X-Forwarded-For", clientIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isNoContent());

        String jti = validator.validate(access).orElseThrow().tokenId();
        Long ttl = redis.getExpire(TokenRevocationChecker.DENYLIST_PREFIX + jti);
        assertThat(ttl).isBetween(1L, 901L);

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        refresh(refreshToken).andExpect(status().isUnauthorized());

        assertThat(auditEvents(userId, "LOGOUT")).hasSize(1);
    }

    @Test
    void logoutRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"abc\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void sixthFailedLoginIsRateLimited() throws Exception {
        String username = uniqueUsername();
        register(username, PASSWORD).andExpect(status().isCreated());
        for (int i = 0; i < 5; i++) {
            login(username, "Wrong!Pass" + i).andExpect(status().isUnauthorized());
        }
        login(username, "Wrong!Pass6")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AUTH_LOGIN_RATE_LIMITED"))
                .andExpect(jsonPath("$.status").value(429));
        // even the right password is refused while the window lasts
        login(username, PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void perIpLimitAppliesAcrossUsernames() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(uniqueUsername(), PASSWORD).andExpect(status().isUnauthorized());
        }
        login(uniqueUsername(), PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void successfulLoginClearsUsernameCounter() throws Exception {
        String username = uniqueUsername();
        register(username, PASSWORD).andExpect(status().isCreated());
        for (int i = 0; i < 4; i++) {
            login(username, "Wrong!Pass" + i).andExpect(status().isUnauthorized());
        }
        login(username, PASSWORD).andExpect(status().isOk());
        assertThat(redis.hasKey("auth:login:failures:user:" + username)).isFalse();
    }

    @Test
    void outboxContainsRegistrationAndLoginEvents() throws Exception {
        String username = uniqueUsername();
        String userId = body(register(username, PASSWORD)
                .andExpect(status().isCreated())).get("id").asText();
        login(username, "Wrong!Pass1").andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", clientIp + ", 172.18.0.5")
                        .header("X-Correlation-Id", "corr-login-" + username)
                        .content(json.writeValueAsString(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "corr-login-" + username));

        // UserRegisteredEvent for banking-core / notification
        List<Map<String, Object>> registered = jdbc.queryForList("""
                select event_type, event_key, status, payload::text as payload from outbox_events
                 where topic = ? and aggregate_id = ?::uuid""", Topics.USER_REGISTERED, userId);
        assertThat(registered).hasSize(1);
        assertThat(registered.getFirst()).containsEntry("event_type", "USER_REGISTERED")
                .containsEntry("event_key", userId).containsEntry("status", "NEW");
        JsonNode event = json.readTree((String) registered.getFirst().get("payload"));
        assertThat(event.get("username").asText()).isEqualTo(username);
        assertThat(event.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(event.get("eventId").asText()).isNotBlank();

        assertThat(auditEvents(userId, "USER_REGISTERED")).hasSize(1);
        assertThat(auditEvents(userId, "LOGIN_FAILURE")).hasSize(1);
        List<JsonNode> success = auditEvents(userId, "LOGIN_SUCCESS");
        assertThat(success).hasSize(1);
        assertThat(success.getFirst().get("correlationId").asText()).isEqualTo("corr-login-" + username);
        assertThat(success.getFirst().get("ipAddress").asText()).isEqualTo(clientIp);
        assertThat(success.getFirst().get("eventType").asText()).isEqualTo("AUDIT");

        String correlation = jdbc.queryForObject("""
                select correlation_id from outbox_events
                 where topic = ? and aggregate_id = ?::uuid and payload->>'action' = 'LOGIN_SUCCESS'""",
                String.class, Topics.AUDIT_EVENT, userId);
        assertThat(correlation).isEqualTo("corr-login-" + username);
    }

    @Test
    void openApiDocsArePublicAndDescribeBearerAuth() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.responses['429']").exists());
    }

    @Test
    void unknownEndpointsAreDenied() throws Exception {
        mvc.perform(get("/api/v1/auth/users")).andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code", is("UNAUTHENTICATED")));
    }

    // ---- helpers ----

    private ResultActions register(String username, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", clientIp)
                .content(json.writeValueAsString(Map.of(
                        "username", username, "password", password, "fullName", "Test Người Dùng",
                        "email", username + "@example.com", "phone", "+84901234567"))));
    }

    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", clientIp)
                .content(json.writeValueAsString(Map.of("username", username, "password", password))));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", clientIp)
                .content(json.writeValueAsString(Map.of("refreshToken", refreshToken))));
    }

    private JsonNode body(ResultActions actions) throws Exception {
        MvcResult result = actions.andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private List<JsonNode> auditEvents(String userId, String action) {
        return jdbc.queryForList("""
                        select payload::text from outbox_events
                         where topic = ? and aggregate_id = ?::uuid and payload->>'action' = ?""",
                String.class, Topics.AUDIT_EVENT, userId, action).stream().map(this::readTree).toList();
    }

    private JsonNode readTree(String value) {
        try {
            return json.readTree(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String uniqueUsername() {
        return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
