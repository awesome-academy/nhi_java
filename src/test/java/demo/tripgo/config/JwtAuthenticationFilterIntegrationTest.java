package demo.tripgo.config;

import demo.tripgo.entity.Role;
import demo.tripgo.entity.User;
import demo.tripgo.entity.UserStatus;
import demo.tripgo.repository.UserRepository;
import demo.tripgo.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SecurityConfigIntegrationTest.UnlistedController.class)
class JwtAuthenticationFilterIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;
    @Value("${jwt.secret}") String secret;

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"BLOCKED", "INACTIVE"})
    void existingTokenLosesAccessWhenAccountIsDisabled(UserStatus status) throws Exception {
        User user = new User();
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setFullName("Test User");
        user.setPassword(encoder.encode("password123"));
        user.setRole(Role.USER);
        user = users.save(user);
        String token = jwt.generateToken(user);

        mvc.perform(get("/api/v1/test/unlisted")
                .contextPath("/api/v1").servletPath("/test/unlisted")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        user.setStatus(status);
        users.saveAndFlush(user);

        mvc.perform(get("/api/v1/test/unlisted")
                .contextPath("/api/v1").servletPath("/test/unlisted")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error.message").isNotEmpty());

        // The public login handler still runs and reports the account status.
        mvc.perform(post("/api/v1/auth/login")
                .contextPath("/api/v1").servletPath("/auth/login")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"password123"}
                    """.formatted(user.getEmail())))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.message").value("User account is not active"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"malformed", "expired", "deleted-user", "missing-user-id", "invalid-user-id", "missing-exp", "valid"})
    void tokenDoesNotBlockLoginAndOnlyValidTokenGrantsProtectedAccess(String kind) throws Exception {
        User user = new User();
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setFullName("Test User");
        user.setPassword(encoder.encode("password123"));
        user.setRole(Role.USER);
        user = users.save(user);

        String token = switch (kind) {
            case "malformed" -> "abc";
            case "expired" -> new JwtService(secret, -1000).generateToken(user);
            case "missing-user-id" -> Jwts.builder()
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
            case "invalid-user-id" -> Jwts.builder().claim("userId", "not-a-number")
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
            case "missing-exp" -> Jwts.builder().claim("userId", user.getId())
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
            default -> jwt.generateToken(user);
        };
        if (kind.equals("deleted-user")) {
            users.delete(user);
            // Login still uses an existing account, but the old token points to a deleted ID.
            User replacement = new User();
            replacement.setEmail(user.getEmail());
            replacement.setFullName(user.getFullName());
            replacement.setPassword(user.getPassword());
            replacement.setRole(Role.USER);
            users.save(replacement);
        }

        mvc.perform(post("/api/v1/auth/login")
                .contextPath("/api/v1").servletPath("/auth/login")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"password123"}
                    """.formatted(user.getEmail())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty());

        mvc.perform(get("/api/v1/test/unlisted")
                .contextPath("/api/v1").servletPath("/test/unlisted")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().is(kind.equals("valid") ? 200 : 401));
    }
}
