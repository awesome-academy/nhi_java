package demo.tripgo.controller;

import demo.tripgo.repository.UserRepository;
import demo.tripgo.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.stream.Stream;

import static org.mockito.Mockito.doReturn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    // Successful registrations use distinct emails because the database is shared across tests.
    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void normalizesEmailAndRejectsDuplicateWithDifferentCase() throws Exception {
        String request = """
            {"fullName":"Nguyen Van An","email":"%s","password":"12345678"}
            """;

        mockMvc.perform(post("/api/v1/auth/register")
            .contextPath("/api/v1")
            .servletPath("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request.formatted("Mixed.Case@Example.com")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.user.email").value("mixed.case@example.com"));

        User savedUser = userRepository.findByEmail("mixed.case@example.com").orElseThrow();
        assertThat(savedUser.getEmail()).isEqualTo("mixed.case@example.com");
        assertThat(savedUser.getPassword()).isNotEqualTo("12345678");
        assertThat(passwordEncoder.matches("12345678", savedUser.getPassword())).isTrue();

        mockMvc.perform(post("/api/v1/auth/register")
            .contextPath("/api/v1")
            .servletPath("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request.formatted("MIXED.CASE@EXAMPLE.COM")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Email already exists: mixed.case@example.com"));
    }

    @Test
    void returnsConflictWhenDatabaseDetectsDuplicateAfterApplicationCheck() throws Exception {
        String request = """
            {"fullName":"Nguyen Van An","email":"race@example.com","password":"12345678"}
            """;

        mockMvc.perform(post("/api/v1/auth/register")
            .contextPath("/api/v1")
            .servletPath("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isCreated());

        // Simulate a stale pre-check while keeping the actual database unique constraint.
        doReturn(false).when(userRepository).existsByEmail("race@example.com");

        mockMvc.perform(post("/api/v1/auth/register")
            .contextPath("/api/v1")
            .servletPath("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.message").value("Data conflicts with existing records or database constraints"))
            .andExpect(jsonPath("$.errors").isEmpty());
    }

    static Stream<String> oversizedPasswords() {
        return Stream.of("a".repeat(73), "mậtkhẩu".repeat(7), "ậ".repeat(24) + "a", "😀".repeat(19));
    }

    @ParameterizedTest
    @MethodSource("oversizedPasswords")
    void rejectsPasswordsExceeding72Utf8Bytes(String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
            .contextPath("/api/v1")
            .servletPath("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"fullName":"Nguyen Van An","email":"oversized@example.com","password":"%s"}
                """.formatted(password)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.password").value("Password must not exceed 72 UTF-8 bytes"));
    }

    static Stream<String> boundaryPasswords() {
        return Stream.of("a".repeat(72), "ậ".repeat(24), "😀".repeat(18));
    }

    @ParameterizedTest
    @MethodSource("boundaryPasswords")
    void acceptsPasswordsAt72Utf8Bytes(String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
            .contextPath("/api/v1")
            .servletPath("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"fullName":"Nguyen Van An","email":"boundary-%s@example.com","password":"%s"}
                """.formatted(password.length(), password)))
            .andExpect(status().isCreated());
    }

    @Test
    void rejectsInvalidFieldsWithoutSavingUser() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
            .contextPath("/api/v1")
            .servletPath("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"fullName":" ","email":"invalid-email","password":"1234567"}
                """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("Validation failed"))
            .andExpect(jsonPath("$.errors.fullName").value("Full name is required"))
            .andExpect(jsonPath("$.errors.email").value("Email is invalid"))
            .andExpect(jsonPath("$.errors.password").value("Password must contain at least 8 characters"));

        assertThat(userRepository.findByEmail("invalid-email")).isEmpty();
    }

    @Test
    void registerIsPublicUnderApiV1BasePath() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
            .contextPath("/api/v1")
            .servletPath("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "fullName": "Nguyen Van An",
                  "email": "an.nguyen@example.com",
                  "password": "12345678"
                }
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.user.email").value("an.nguyen@example.com"))
        .andExpect(jsonPath("$.user.password").doesNotHaveJsonPath());

        User saved = userRepository.findByEmail("an.nguyen@example.com").orElseThrow();
        assertThat(saved.getPassword()).isNotEqualTo("12345678");
        assertThat(passwordEncoder.matches("12345678", saved.getPassword())).isTrue();
    }
}
