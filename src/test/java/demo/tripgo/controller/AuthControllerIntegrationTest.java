package demo.tripgo.controller;

import demo.tripgo.repository.UserRepository;
import demo.tripgo.entity.Role;
import demo.tripgo.entity.User;
import demo.tripgo.entity.UserStatus;
import demo.tripgo.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.stream.Stream;

import static org.mockito.Mockito.doReturn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Autowired
    private JwtService jwtService;

    private User saveUser(String email) {
        User user = new User();
        user.setFullName("Me Test");
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("password123"));
        user.setRole(Role.USER);
        return userRepository.save(user);
    }

    @Test
    void meReturnsCurrentUserWithoutPassword() throws Exception {
        User user = saveUser("me-success@example.com");
        String token = jwtService.generateToken(user);

        mockMvc.perform(get("/api/v1/auth/me")
                .contextPath("/api/v1").servletPath("/auth/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(user.getId()))
            .andExpect(jsonPath("$.email").value("me-success@example.com"))
            .andExpect(jsonPath("$.role").value("user"))
            .andExpect(jsonPath("$.password").doesNotHaveJsonPath());
    }

    @Test
    void meWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                .contextPath("/api/v1").servletPath("/auth/me"))
            .andExpect(status().isUnauthorized())
            // Thiếu token bị chặn ở entry-point của Spring Security -> code là tên HttpStatus,
            // khác với INVALID_CREDENTIALS (dành cho sai email/mật khẩu ở /auth/login).
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.error.message").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "USER")
    void meWithNonUserPrincipalReturns401NotNpe() throws Exception {
        // Principal của @WithMockUser là UserDetails, không phải entity User, nên
        // @AuthenticationPrincipal User bind về null. Controller phải trả 401 ErrorResponse
        // thay vì để NullPointerException lọt ra format lỗi mặc định của Spring Boot.
        mockMvc.perform(get("/api/v1/auth/me")
                .contextPath("/api/v1").servletPath("/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
            .andExpect(jsonPath("$.error.message").isNotEmpty());
    }

    @Test
    void loginReturnsTokenAndUserWithoutPassword() throws Exception {
        registerLoginUser("login-success@example.com");

        mockMvc.perform(post("/api/v1/auth/login")
                .contextPath("/api/v1").servletPath("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"login-success@example.com","password":"password123"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.user.email").value("login-success@example.com"))
            .andExpect(jsonPath("$.user.password").doesNotHaveJsonPath());
    }

    @Test
    void loginAcceptsDifferentEmailCaseAfterRegistration() throws Exception {
        registerLoginUser("Login.Mixed.Case@Example.com");

        for (String email : new String[]{"login.mixed.case@example.com", "LOGIN.MIXED.CASE@EXAMPLE.COM"}) {
            mockMvc.perform(post("/api/v1/auth/login")
                    .contextPath("/api/v1").servletPath("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"email":"%s","password":"password123"}
                        """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("login.mixed.case@example.com"))
                .andExpect(jsonPath("$.user.password").doesNotHaveJsonPath());
        }
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"BLOCKED", "INACTIVE"})
    void loginRejectsNonActiveAccount(UserStatus accountStatus) throws Exception {
        String email = "login-status-" + accountStatus.name().toLowerCase(java.util.Locale.ROOT) + "@example.com";
        registerLoginUser(email);
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setStatus(accountStatus);
        userRepository.saveAndFlush(user);

        mockMvc.perform(post("/api/v1/auth/login")
                .contextPath("/api/v1").servletPath("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"password123"}
                    """.formatted(email)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
            .andExpect(jsonPath("$.error.message").value("Tài khoản không ở trạng thái hoạt động"))
            .andExpect(jsonPath("$.error.fields").doesNotExist())
            .andExpect(jsonPath("$.token").doesNotHaveJsonPath());
    }

    private void registerLoginUser(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contextPath("/api/v1").servletPath("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Login Test","email":"%s","password":"password123"}
                    """.formatted(email)))
            .andExpect(status().isCreated());
    }

    @Test
    void shortIncorrectPasswordReturns401() throws Exception {
        User user = new User();
        user.setFullName("Login Test");
        user.setEmail("short-login@example.com");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setRole(demo.tripgo.entity.Role.USER);
        userRepository.save(user);

        for (String password : new String[]{"1", "1234567", "wrongpassword"}) {
            mockMvc.perform(post("/api/v1/auth/login")
                    .contextPath("/api/v1").servletPath("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"email":"short-login@example.com","password":"%s"}
                        """.formatted(password)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.error.message").value("Email hoặc mật khẩu không đúng"))
                .andExpect(jsonPath("$.error.fields").doesNotExist())
                .andExpect(jsonPath("$.error.fields").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotHaveJsonPath());
        }
    }

    @Test
    void missingOrBlankLoginPasswordReturns422() throws Exception {
        for (String body : new String[]{
                "{\"email\":\"login@example.com\"}",
                "{\"email\":\"login@example.com\",\"password\":\" \"}"}) {
            mockMvc.perform(post("/api/v1/auth/login")
                    .contextPath("/api/v1").servletPath("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.fields.password").value("Mật khẩu không được để trống"));
        }
    }

    @Test
    void normalizesEmailAndRejectsDuplicateWithDifferentCase() throws Exception {
        String request = """
            {"name":"Nguyen Van An","email":"%s","password":"12345678"}
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
            .andExpect(jsonPath("$.error.message").value("Email đã được sử dụng: mixed.case@example.com"));
    }

    @Test
    void returnsConflictWhenDatabaseDetectsDuplicateAfterApplicationCheck() throws Exception {
        String request = """
            {"name":"Nguyen Van An","email":"race@example.com","password":"12345678"}
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
            .andExpect(jsonPath("$.error.code").value("CONFLICT"))
            .andExpect(jsonPath("$.error.message").value("Dữ liệu bị trùng với bản ghi đã tồn tại"))
            .andExpect(jsonPath("$.error.fields").doesNotExist());
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
                {"name":"Nguyen Van An","email":"oversized@example.com","password":"%s"}
                """.formatted(password)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.fields.password").value("Mật khẩu quá dài, vui lòng chọn mật khẩu ngắn hơn"));
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
                {"name":"Nguyen Van An","email":"boundary-%s@example.com","password":"%s"}
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
                {"name":" ","email":"invalid-email","password":"1234567"}
                """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("VALIDATION"))
            .andExpect(jsonPath("$.error.message").value("Dữ liệu không hợp lệ"))
            .andExpect(jsonPath("$.error.fields.name").value("Họ tên không được để trống"))
            .andExpect(jsonPath("$.error.fields.email").value("Email không hợp lệ"))
            .andExpect(jsonPath("$.error.fields.password").value("Mật khẩu phải có ít nhất 8 ký tự"));

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
                  "name": "Nguyen Van An",
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
