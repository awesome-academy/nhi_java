package demo.tripgo.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({SecurityConfigIntegrationTest.UnlistedController.class, SecurityConfigIntegrationTest.AdminToursController.class})
class SecurityConfigIntegrationTest {
    @Autowired
    private MockMvc mvc;

    // Exists only in tests and deliberately has no matcher in SecurityConfig.
    @RestController
    static class UnlistedController {
        @GetMapping("/test/unlisted")
        String unlisted() {
            return "protected";
        }
    }

    // Test-only handlers verify requests actually reach a controller for ADMIN.
    @RestController
    static class AdminToursController {
        @PostMapping("/admin/tours")
        String create() { return "admin"; }

        @PutMapping("/admin/tours/{id}")
        String update() { return "admin"; }

        @DeleteMapping("/admin/tours/{id}")
        String delete() { return "admin"; }
    }

    @ParameterizedTest
    @CsvSource({"POST,/admin/tours", "PUT,/admin/tours/1", "DELETE,/admin/tours/1"})
    void adminToursRequireAuthentication(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), "/api/v1" + path)
                .contextPath("/api/v1").servletPath(path))
            .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @CsvSource({"POST,/admin/tours", "PUT,/admin/tours/1", "DELETE,/admin/tours/1"})
    @WithMockUser(roles = "USER")
    void userCannotManageAdminTours(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), "/api/v1" + path)
                .contextPath("/api/v1").servletPath(path))
            .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({"POST,/admin/tours", "PUT,/admin/tours/1", "DELETE,/admin/tours/1"})
    @WithMockUser(roles = "ADMIN")
    void adminCanManageAdminTours(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), "/api/v1" + path)
                .contextPath("/api/v1").servletPath(path))
            .andExpect(status().isOk())
            .andExpect(content().string("admin"));
    }

    @Test
    void unlistedEndpointRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/test/unlisted")
                .contextPath("/api/v1").servletPath("/test/unlisted"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.message").isNotEmpty());
    }

    @Test
    @WithMockUser
    void authenticatedUserCanReachUnlistedEndpoint() throws Exception {
        mvc.perform(get("/api/v1/test/unlisted")
                .contextPath("/api/v1").servletPath("/test/unlisted"))
            .andExpect(status().isOk())
            .andExpect(content().string("protected"));
    }

    // Preflight CORS phải được chấp nhận (không cần auth) và trả header Access-Control-Allow-Origin.
    @Test
    void corsPreflightIsAllowed() throws Exception {
        mvc.perform(options("/api/v1/tours")
                .contextPath("/api/v1").servletPath("/tours")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "*"))
            .andExpect(header().exists("Access-Control-Allow-Methods"));
    }
}
