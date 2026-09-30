package demo.tripgo.admin;

import demo.tripgo.entity.AuthProvider;
import demo.tripgo.entity.Role;
import demo.tripgo.entity.User;
import demo.tripgo.entity.UserStatus;
import demo.tripgo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@tripgo.local";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired demo.tripgo.TestDataCleaner cleaner;

    private User admin;
    private User customer;
    private User facebookUser;

    @BeforeEach
    void setUp() {
        cleaner.clean();
        admin = users.save(account(ADMIN_EMAIL, "Quản trị viên", Role.ADMIN));
        customer = users.save(account("khach@example.com", "Nguyễn Văn Khách", Role.USER));
        facebookUser = account("facebook.fb-9@facebook.local", "Người dùng Facebook", Role.USER);
        facebookUser.setPassword(null);
        facebookUser.setProvider(AuthProvider.FACEBOOK);
        facebookUser.setProviderId("fb-9");
        facebookUser = users.save(facebookUser);
    }

    private User account(String email, String name, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setFullName(name);
        user.setPassword("{noop}x");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    // ---- Danh sách ----

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void listsAllUsers() throws Exception {
        mvc.perform(get("/admin/users"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/users/list"))
            .andExpect(model().attribute("users", hasSize(3)))
            .andExpect(content().string(containsString("khach@example.com")))
            .andExpect(content().string(containsString("Facebook")));
    }

    // Tìm theo tên có dấu phải khớp: cột full_name lưu nguyên dấu.
    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void searchesByAccentedName() throws Exception {
        mvc.perform(get("/admin/users").param("q", "văn khách"))
            .andExpect(model().attribute("users", hasSize(1)))
            .andExpect(content().string(containsString("khach@example.com")));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void filtersByRole() throws Exception {
        mvc.perform(get("/admin/users").param("role", "ADMIN"))
            .andExpect(model().attribute("users", hasSize(1)))
            .andExpect(content().string(not(containsString("khach@example.com"))));
    }

    @Test
    @WithMockUser(username = "khach@example.com", roles = "USER")
    void normalUserCannotOpenUserManagement() throws Exception {
        mvc.perform(get("/admin/users"))
            .andExpect(redirectedUrl("/admin/login?denied"));
    }

    // ---- Đổi role ----

    // Đúng kịch bản đăng nhập Facebook: tài khoản vừa được tạo với role USER, admin nâng quyền.
    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void promotesFacebookAccountToAdmin() throws Exception {
        mvc.perform(post("/admin/users/{id}/role", facebookUser.getId())
                .param("newRole", "ADMIN")
                .param("role", "USER")
                .param("page", "1")
                .with(csrf()))
            .andExpect(redirectedUrl("/admin/users?role=USER"))
            .andExpect(flash().attribute("flashType", "success"));

        assertThat(users.findById(facebookUser.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void demotesAnotherAdmin() throws Exception {
        customer.setRole(Role.ADMIN);
        users.save(customer);

        mvc.perform(post("/admin/users/{id}/role", customer.getId()).param("newRole", "USER").with(csrf()))
            .andExpect(redirectedUrl("/admin/users"))
            .andExpect(flash().attribute("flashType", "success"));

        assertThat(users.findById(customer.getId()).orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void cannotDemoteSelf() throws Exception {
        customer.setRole(Role.ADMIN);
        users.save(customer);

        mvc.perform(post("/admin/users/{id}/role", admin.getId()).param("newRole", "USER").with(csrf()))
            .andExpect(flash().attribute("flashMessage", "Bạn không thể tự hạ quyền của chính mình"));

        assertThat(users.findById(admin.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
    }

    // Người thao tác không phải chính admin đó (vd: tài khoản admin khác chưa lưu trong DB), vẫn
    // không được hạ admin cuối cùng.
    @Test
    @WithMockUser(username = "admin-khac@tripgo.local", roles = "ADMIN")
    void cannotDemoteLastAdmin() throws Exception {
        mvc.perform(post("/admin/users/{id}/role", admin.getId()).param("newRole", "USER").with(csrf()))
            .andExpect(flash().attribute("flashMessage", "Hệ thống phải còn ít nhất một admin"));

        assertThat(users.findById(admin.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void unknownRoleValueIsRejected() throws Exception {
        mvc.perform(post("/admin/users/{id}/role", customer.getId()).param("newRole", "SUPERADMIN").with(csrf()))
            .andExpect(flash().attribute("flashType", "error"));

        assertThat(users.findById(customer.getId()).orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void unknownUserIsNotFound() throws Exception {
        mvc.perform(post("/admin/users/{id}/role", 999_999L).param("newRole", "ADMIN").with(csrf()))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void changeRoleWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/admin/users/{id}/role", customer.getId()).param("newRole", "ADMIN"))
            .andExpect(status().isForbidden());

        assertThat(users.findById(customer.getId()).orElseThrow().getRole()).isEqualTo(Role.USER);
    }
}
