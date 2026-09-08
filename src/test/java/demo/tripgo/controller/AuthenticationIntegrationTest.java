package demo.tripgo.controller;

import demo.tripgo.entity.Role;
import demo.tripgo.entity.User;
import demo.tripgo.entity.UserStatus;
import demo.tripgo.repository.UserRepository;
import demo.tripgo.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper mapper;
    @Value("${jwt.secret}") String secret;

    private User createUser() {
        return createUser(UserStatus.ACTIVE);
    }

    private User createUser(UserStatus status) {
        User user = new User();
        user.setFullName("Test User");
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPassword(encoder.encode("password123"));
        user.setRole(Role.USER);
        user.setStatus(status);
        return users.save(user);
    }

    @Test
    void loginAndMeReturnUserWithoutPassword() throws Exception {
        User user = createUser();
        String body = mvc.perform(post("/api/v1/auth/login")
                .contextPath("/api/v1").servletPath("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"password123"}
                    """.formatted(user.getEmail())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.user.id").value(user.getId()))
            .andExpect(jsonPath("$.user.password").doesNotHaveJsonPath())
            .andReturn().getResponse().getContentAsString();
        String token = mapper.readTree(body).get("accessToken").asText();
        assertThat(body).doesNotContain("password123", user.getPassword());
        String me = mvc.perform(get("/api/v1/auth/me")
                .contextPath("/api/v1").servletPath("/auth/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(user.getId()))
            .andExpect(jsonPath("$.email").value(user.getEmail()))
            .andExpect(jsonPath("$.fullName").value("Test User"))
            .andExpect(jsonPath("$.role").value("USER"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.password").doesNotHaveJsonPath())
            .andReturn().getResponse().getContentAsString();
        assertThat(me).doesNotContain("password123", user.getPassword());
    }

    @Test
    void wrongPasswordAndUnknownEmailReturn401() throws Exception {
        User user = createUser();
        for (String email : new String[]{user.getEmail(), "unknown@example.com"}) {
            mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"email":"%s","password":"wrongpassword"}
                        """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(jsonPath("$.accessToken").doesNotHaveJsonPath());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/auth/me", "/bookings"})
    void protectedEndpointsRejectMissingMalformedExpiredAndForgedTokens(String path) throws Exception {
        User user = createUser();
        String expired = new JwtService(secret, -1000).generateToken(user);
        String forged = new JwtService("a-different-secret-key-with-at-least-32-bytes", 60000).generateToken(user);
        String missingId = Jwts.builder().subject(user.getEmail())
            .expiration(new Date(System.currentTimeMillis() + 60000))
            .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
        for (String header : new String[]{"", "Basic abc", "Bearer garbage", "Bearer " + expired,
                "Bearer " + forged, "Bearer " + missingId}) {
            mvc.perform(get("/api/v1" + path).contextPath("/api/v1").servletPath(path)
                    .header("Authorization", header))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotHaveJsonPath());
        }
    }

    @Test
    void deletedUserTokenReturns401() throws Exception {
        User user = createUser();
        String token = jwt.generateToken(user);
        users.delete(user);
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void registerCreatesUserWithoutExposingPassword() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String body = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fullName":"New User","email":"%s","password":"password123"}
                    """.formatted(email)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.user.email").value(email))
            .andExpect(jsonPath("$.user.fullName").value("New User"))
            .andExpect(jsonPath("$.user.role").value("USER"))
            .andExpect(jsonPath("$.user.status").value("ACTIVE"))
            .andExpect(jsonPath("$.user.password").doesNotHaveJsonPath())
            .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("password123");
        assertThat(users.existsByEmail(email)).isTrue();
    }

    @Test
    void registerWithDuplicateEmailReturns409() throws Exception {
        User existing = createUser();
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fullName":"Dup User","email":"%s","password":"password123"}
                    """.formatted(existing.getEmail())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void registerNormalizesEmailBeforeDuplicateCheck() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fullName":"First","email":"%s","password":"password123"}
                    """.formatted(email)))
            .andExpect(status().isCreated());
        // Cùng email nhưng viết hoa phải bị coi là trùng (service chuẩn hoá về chữ thường).
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fullName":"Second","email":"%s","password":"password123"}
                    """.formatted(email.toUpperCase())))
            .andExpect(status().isConflict());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"fullName\":\"\",\"email\":\"a@b.com\",\"password\":\"password123\"}",
        "{\"fullName\":\"Ok\",\"email\":\"not-an-email\",\"password\":\"password123\"}",
        "{\"fullName\":\"Ok\",\"email\":\"a@b.com\",\"password\":\"short\"}"
    })
    void registerWithInvalidBodyReturns400(String requestBody) throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"email\":\"\",\"password\":\"password123\"}",
        "{\"email\":\"not-an-email\",\"password\":\"password123\"}",
        "{\"email\":\"a@b.com\",\"password\":\"\"}"
    })
    void loginWithInvalidBodyReturns400(String requestBody) throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @Test
    void loginNormalizesEmailCasing() throws Exception {
        User user = createUser();
        // Email viết hoa vẫn đăng nhập được vì service chuẩn hoá về chữ thường trước khi tra cứu.
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"password123"}
                    """.formatted(user.getEmail().toUpperCase())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"INACTIVE", "BLOCKED"})
    void loginWithNonActiveUserReturns401(UserStatus status) throws Exception {
        User user = createUser(status);
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"password123"}
                    """.formatted(user.getEmail())))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("User account is not active"));
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"INACTIVE", "BLOCKED"})
    void validTokenForNonActiveUserReturns401(UserStatus status) throws Exception {
        User user = createUser(status);
        String token = jwt.generateToken(user);
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @ParameterizedTest
    @CsvSource({
        "POST,/admin/tours",
        "PUT,/admin/tours/1",
        "DELETE,/admin/tours/1"
    })
    void nonAdminUserGetsForbiddenOnAdminEndpoints(String method, String path) throws Exception {
        User user = createUser();
        String token = jwt.generateToken(user);
        mvc.perform(request(HttpMethod.valueOf(method), path)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.message").isNotEmpty());
    }
}
