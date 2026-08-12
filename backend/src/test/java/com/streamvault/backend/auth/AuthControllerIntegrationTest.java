package com.streamvault.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamvault.backend.user.User;
import com.streamvault.backend.user.UserRepository;

/**
 * Full-stack coverage of STORY-001: exercises the real security filter chain
 * (JwtAuthenticationFilter, SecurityConfig) and real persistence, not mocks,
 * so it verifies the acceptance criteria as actually wired together.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DirtiesContext
class AuthControllerIntegrationTest {

    private static final String TEST_JWT_SECRET = "test-secret-key-that-is-at-least-32-bytes-long";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:authcontrollertest;MODE=PostgreSQL");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("app.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("app.jwt.expiration-ms", () -> "86400000");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private String registerJson(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(new com.streamvault.backend.auth.dto.RegisterRequest(email, password));
    }

    private String loginJson(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(new com.streamvault.backend.auth.dto.LoginRequest(email, password));
    }

    private String registerAndLogin(String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, password)))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, password)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    // --- AC: unique email registration ---

    @Test
    void should_createUserAndReturnIdAndEmail_when_registeringWithNewUniqueEmail() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, "Password1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.id").exists());

        assertThat(userRepository.existsByEmail(email)).isTrue();
    }

    @Test
    void should_rejectRegistrationWithConflict_when_emailIsAlreadyRegistered() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, "Password1")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, "AnotherPass1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Email is already registered"));

        assertThat(userRepository.findByEmail(email)).hasValueSatisfying(
                user -> assertThat(passwordEncoder.matches("Password1", user.getPasswordHash())).isTrue());
    }

    // --- AC: password complexity enforced end-to-end, and rejected registrations don't persist a user ---

    @Test
    void should_rejectRegistrationWithBadRequest_when_passwordFailsComplexityRules() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, "short1A")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.password").exists());

        assertThat(userRepository.existsByEmail(email)).isFalse();
    }

    // --- AC: passwords are never stored or exposed in plain text ---

    @Test
    void should_storeOnlyABcryptHash_when_userRegistersWithAPassword() throws Exception {
        String email = uniqueEmail();
        String rawPassword = "Password1";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, rawPassword)))
                .andExpect(status().isCreated());

        User stored = userRepository.findByEmail(email).orElseThrow();
        assertThat(stored.getPasswordHash())
                .isNotEqualTo(rawPassword)
                .doesNotContain(rawPassword)
                .matches("^\\$2[aby]\\$.*");
    }

    @Test
    void should_notExposePasswordInAnyResponseBody_when_registeringOrLoggingIn() throws Exception {
        String email = uniqueEmail();

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, "Password1")))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "Password1")))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(registerResult.getResponse().getContentAsString().toLowerCase()).doesNotContain("password");
        assertThat(loginResult.getResponse().getContentAsString().toLowerCase()).doesNotContain("password");
    }

    // --- AC: successful login returns a JWT ---

    @Test
    void should_returnBearerJwt_when_loggingInWithCorrectCredentials() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, "Password1")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "Password1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.token", org.hamcrest.Matchers.matchesPattern("^[^.]+\\.[^.]+\\.[^.]+$")));
    }

    // --- AC: login fails with a generic error, without indicating which field was wrong ---

    @Test
    void should_rejectLoginWithUnauthorized_when_emailDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(uniqueEmail(), "Password1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid email or password"));
    }

    @Test
    void should_rejectLoginWithUnauthorized_when_passwordIsWrong() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, "Password1")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "WrongPassword1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid email or password"));
    }

    @Test
    void should_returnIdenticalErrorBody_when_loginFailsForUnknownEmailVersusWrongPassword() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, "Password1")))
                .andExpect(status().isCreated());

        MvcResult unknownEmail = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(uniqueEmail(), "Password1")))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult wrongPassword = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "WrongPassword1")))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(unknownEmail.getResponse().getStatus()).isEqualTo(wrongPassword.getResponse().getStatus());
        assertThat(unknownEmail.getResponse().getContentAsString())
                .isEqualTo(wrongPassword.getResponse().getContentAsString());
    }

    // --- AC: authenticated endpoints reject requests without a valid JWT ---

    @Test
    void should_rejectRequestWithUnauthorized_when_noAuthorizationHeaderIsPresent() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication required"));
    }

    @Test
    void should_rejectRequestWithUnauthorized_when_tokenIsMalformed() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_rejectRequestWithUnauthorized_when_tokenIsSignedWithAnUnknownSecret() throws Exception {
        JwtService foreignSigner = new JwtService("a-completely-different-secret-key-32-bytes", 60_000L);
        String foreignToken = foreignSigner.generateToken(1L, "someone@example.com");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + foreignToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_rejectRequestWithUnauthorized_when_tokenIsExpired() throws Exception {
        JwtService expiredTokenSigner = new JwtService(TEST_JWT_SECRET, -1_000L);
        String expiredToken = expiredTokenSigner.generateToken(1L, "someone@example.com");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_allowAccessAndReturnCorrectPrincipal_when_tokenIsValid() throws Exception {
        String email = uniqueEmail();
        String token = registerAndLogin(email, "Password1");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.userId").exists());
    }

    @Test
    void should_notReturnAuthorizationHeaderChallenge_when_alreadyAuthenticatedWithValidToken() throws Exception {
        String email = uniqueEmail();
        String token = registerAndLogin(email, "Password1");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }
}
