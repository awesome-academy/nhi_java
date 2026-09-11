package demo.tripgo.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CategoryControllerIntegrationTest {

    @Autowired MockMvc mvc;

    // Public: dropdown lọc phải xem được khi chưa đăng nhập.
    @Test
    void listsAllCategoriesWithVietnameseLabelsWithoutToken() throws Exception {
        mvc.perform(get("/api/v1/categories").contextPath("/api/v1").servletPath("/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(6))
            .andExpect(jsonPath("$.data[0].slug").value("beach"))
            .andExpect(jsonPath("$.data[0].name").value("Biển đảo"))
            .andExpect(jsonPath("$.data[1].slug").value("mountain"))
            .andExpect(jsonPath("$.data[1].name").value("Núi rừng"));
    }

    // slug trả về phải dùng được ngay làm ?category= của GET /tours (không 400).
    @Test
    void everySlugIsAcceptedByTourCategoryFilter() throws Exception {
        for (String slug : new String[]{"beach", "mountain", "city", "trekking", "cruise", "cultural"}) {
            mvc.perform(get("/api/v1/tours").contextPath("/api/v1").servletPath("/tours")
                    .param("category", slug))
                .andExpect(status().isOk());
        }
    }
}
