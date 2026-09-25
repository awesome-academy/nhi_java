package demo.tripgo.admin;

import demo.tripgo.entity.Role;
import demo.tripgo.entity.User;
import demo.tripgo.entity.UserStatus;
import demo.tripgo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

// Bám sát tiêu chí chấp nhận của khu quản trị: chưa đăng nhập -> về /admin/login;
// user thường không vào được; admin vào được.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSecurityIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@tripgo.local";
    private static final String USER_EMAIL = "khach@tripgo.local";
    private static final String PASSWORD = "MatKhau123";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        users.deleteAll();
        users.save(account(ADMIN_EMAIL, Role.ADMIN));
        users.save(account(USER_EMAIL, Role.USER));
    }

    private User account(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setFullName("Tài khoản " + role);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    // ---- AC: chưa đăng nhập admin -> chuyển tới /admin/login ----

    @Test
    void anonymousIsRedirectedToLogin() throws Exception {
        mvc.perform(get("/admin"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/login"));
    }

    @Test
    void loginPageIsPublic() throws Exception {
        mvc.perform(get("/admin/login"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/login"))
            .andExpect(content().string(containsString("TripGo Admin")));
    }

    // ---- AC: chỉ tài khoản role=admin vào được ----

    @Test
    void adminLogsInWithEmailAndPassword() throws Exception {
        mvc.perform(formLogin("/admin/login").user("email", ADMIN_EMAIL).password("password", PASSWORD))
            .andExpect(authenticated().withRoles("ADMIN"))
            .andExpect(redirectedUrl("/admin"));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void adminReachesDashboard() throws Exception {
        mvc.perform(get("/admin"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/dashboard"))
            .andExpect(content().string(containsString("Dashboard")));
    }

    // User thường xác thực được (đúng mật khẩu) nhưng không có quyền vào khu quản trị.
    @Test
    void normalUserAuthenticatesButIsDeniedAdminArea() throws Exception {
        mvc.perform(formLogin("/admin/login").user("email", USER_EMAIL).password("password", PASSWORD))
            .andExpect(authenticated().withRoles("USER"));
    }

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "USER")
    void normalUserCannotOpenAdminArea() throws Exception {
        mvc.perform(get("/admin"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/login?denied"));
    }

    @Test
    void wrongPasswordGoesBackToLoginWithError() throws Exception {
        mvc.perform(formLogin("/admin/login").user("email", ADMIN_EMAIL).password("password", "sai-roi"))
            .andExpect(unauthenticated())
            .andExpect(redirectedUrl("/admin/login?error"));
    }

    // ---- CSRF: lý do khu quản trị bật CSRF còn API thì không ----

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void postWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/admin/logout"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void postWithCsrfTokenIsAccepted() throws Exception {
        mvc.perform(post("/admin/logout").with(csrf()))
            .andExpect(redirectedUrl("/admin/login?logout"));
    }

    @Test
    void logoutClearsSession() throws Exception {
        mvc.perform(logout("/admin/logout"))
            .andExpect(unauthenticated())
            .andExpect(redirectedUrl("/admin/login?logout"));
    }

    // ---- Khu quản trị không được ảnh hưởng tới API ----

    @Test
    void apiChainIsUnaffected() throws Exception {
        mvc.perform(get("/api/v1/tours"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/wishlist"))
            .andExpect(status().isUnauthorized());
    }
}
