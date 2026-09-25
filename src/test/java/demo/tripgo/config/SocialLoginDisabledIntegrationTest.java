package demo.tripgo.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Không khai FACEBOOK_CLIENT_ID (mặc định của profile test) thì cả cụm social login không được tạo,
// và phần còn lại của ứng dụng vẫn chạy bình thường.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SocialLoginDisabledIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;

    @Test
    void socialLoginBeansAreNotCreated() {
        assertThat(context.getBeanNamesForType(ClientRegistrationRepository.class)).isEmpty();
        assertThat(context.containsBean("oauth2LoginFilterChain")).isFalse();
    }

    @Test
    void emailPasswordRegistrationStillWorks() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nhi\",\"email\":\"khong-social@example.com\",\"password\":\"MatKhau123\"}"))
            .andExpect(status().isCreated());
    }
}
