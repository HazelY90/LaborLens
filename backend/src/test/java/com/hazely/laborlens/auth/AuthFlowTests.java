package com.hazely.laborlens.auth;

import com.hazely.laborlens.config.SecurityConfig;
import com.hazely.laborlens.controllers.*;
import com.hazely.laborlens.entities.User;
import com.hazely.laborlens.exceptions.AuthErrors;
import com.hazely.laborlens.repositories.UserRepository;
import com.hazely.laborlens.security.*;
import com.hazely.laborlens.services.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real JWT signing, password hashing, cookie handling and security; only persistence is simulated. */
@WebMvcTest(controllers = {AuthController.class, DataController.class}, properties = {
        "app.auth.jwt-secret=test-only-signing-secret-at-least-32-bytes",
        "app.auth.cookie-secure=true", "app.auth.allowed-origins=https://app.example.test"})
@Import({SecurityConfig.class, AuthService.class, JwtTokens.class, RefreshCookie.class, AuthErrors.class})
class AuthFlowTests {
    @Autowired MockMvc mvc;
    @MockitoBean UserRepository users;
    @MockitoBean MetricService metrics;
    @MockitoBean PolicyService policies;
    private final Map<Long, User> stored = new HashMap<>();
    private final JsonMapper json = JsonMapper.builder().build();
    private static final String ORIGIN = "https://app.example.test";
    private record Session(String access, String refresh) {}

    @BeforeEach
    void persistence() {
        when(users.existsByEmail(any())).thenAnswer(c -> stored.values().stream().anyMatch(u -> u.getEmail().equals(c.getArgument(0))));
        when(users.saveAndFlush(any())).thenAnswer(c -> {
            User user = c.getArgument(0);
            long id = stored.size() + 1L;
            ReflectionTestUtils.setField(user, "id", id); stored.put(id, user); return user;
        });
        when(users.lockEmail(any())).thenAnswer(c -> stored.values().stream().filter(u -> u.getEmail().equals(c.getArgument(0))).findFirst());
        when(users.findById(any())).thenAnswer(c -> Optional.ofNullable(stored.get(c.getArgument(0))));
        when(users.lockId(anyLong())).thenAnswer(c -> Optional.ofNullable(stored.get(c.getArgument(0))));
        doAnswer(c -> { stored.remove(((User)c.getArgument(0)).getId()); return null; }).when(users).delete(any());
    }

    @Test
    void registersLogsInAndRenamesWithoutRevoking() throws Exception {
        register();
        assertNotEquals("password123", stored.get(1L).getPasswordHash());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"person@example.test\",\"username\":\"Other\",\"password\":\"password123\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_EXISTS"));
        Session session = login("password123");
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + session.access()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("person@example.test"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(patch("/api/auth/me/username").header("Authorization", "Bearer " + session.access())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"New name\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("New name"));
        assertEquals(0, stored.get(1L).getTokenVersion());
        mvc.perform(get("/api/data/metadata").header("Authorization", "Bearer " + session.access()))
                .andExpect(status().isOk());
    }

    @Test
    void refreshesWithCookieAndEnforcesOriginAndTokenType() throws Exception {
        register(); Session session = login("password123");
        mvc.perform(post("/api/auth/refresh").cookie(cookie(session.refresh())).header("Origin", ORIGIN)
                        .header("Authorization", "Bearer expired-access-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").doesNotExist()).andExpect(header().exists("Set-Cookie"));
        mvc.perform(post("/api/auth/refresh").cookie(cookie(session.refresh()))).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/refresh").cookie(cookie(session.refresh())).header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/refresh").cookie(cookie(session.access())).header("Origin", ORIGIN))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/data/metadata").header("Authorization", "Bearer " + session.refresh()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/data/metadata").cookie(cookie(session.refresh()))).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordChangeRevokesAllDevicesAndRequiresCurrentPassword() throws Exception {
        register(); Session first = login("password123"); Session second = login("password123");
        mvc.perform(patch("/api/auth/me/password").header("Authorization", "Bearer " + first.access())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"wrong\",\"newPassword\":\"replacement123\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(0, stored.get(1L).getTokenVersion());
        mvc.perform(patch("/api/auth/me/password").header("Authorization", "Bearer " + first.access())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"password123\",\"newPassword\":\"replacement123\"}"))
                .andExpect(status().isNoContent()).andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        assertEquals(1, stored.get(1L).getTokenVersion());
        rejected(first); rejected(second);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"person@example.test\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized());
        login("replacement123");
    }

    @Test
    void logoutRevokesAllDevicesAndDeletionRemovesAccount() throws Exception {
        register(); Session first = login("password123"); Session second = login("password123");
        mvc.perform(post("/api/auth/logout").cookie(cookie(first.refresh())).header("Origin", ORIGIN))
                .andExpect(status().isNoContent()).andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        rejected(first); rejected(second);
        Session fresh = login("password123");
        mvc.perform(delete("/api/auth/me").header("Authorization", "Bearer " + fresh.access())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"wrong\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(1, stored.size());
        mvc.perform(delete("/api/auth/me").header("Authorization", "Bearer " + fresh.access())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"password123\"}"))
                .andExpect(status().isNoContent());
        assertTrue(stored.isEmpty()); rejected(fresh);
    }

    @Test
    void rejectsInvalidRequestsAndNeverReturnsPasswordValues() throws Exception {
        for (String body : List.of("{}", "{broken", "{\"email\":\"not-email\",\"username\":\"Name\",\"password\":\"password123\"}",
                "{\"email\":\"a@example.test\",\"username\":\" \",\"password\":\"password123\"}",
                "{\"email\":\"a@example.test\",\"username\":\"Name\",\"password\":\"short\"}",
                "{\"email\":\"a@example.test\",\"username\":\"Name\",\"password\":\"" + "界".repeat(30) + "\"}")) {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("password123"))));
        }
        assertTrue(stored.isEmpty());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing@example.test\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }
    private void register() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Person@Example.Test\",\"username\":\"Display Name\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value("person@example.test"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
    private Session login(String password) throws Exception {
        var response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"PERSON@EXAMPLE.TEST\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("HttpOnly"), org.hamcrest.Matchers.containsString("Secure"),
                        org.hamcrest.Matchers.containsString("SameSite=Lax"), org.hamcrest.Matchers.containsString("Path=/api/auth"))))
                .andReturn().getResponse();
        String token = json.readTree(response.getContentAsString()).path("accessToken").asString();
        String refresh = response.getHeader("Set-Cookie").split(";", 2)[0].substring("refresh_token=".length());
        return new Session(token, refresh);
    }
    private Cookie cookie(String value) { return new Cookie(RefreshCookie.NAME, value); }
    private void rejected(Session session) throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + session.access())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(cookie(session.refresh())).header("Origin", ORIGIN)).andExpect(status().isUnauthorized());
    }
}
