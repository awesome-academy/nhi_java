package demo.tripgo.controller;

import demo.tripgo.entity.Category;
import demo.tripgo.repository.CategoryRepository;
import demo.tripgo.repository.TourRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CategoryControllerIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired CategoryRepository categories;
    @Autowired TourRepository tours;

    private static final List<String[]> SEED = List.of(
        new String[]{"beach", "Biển đảo"},
        new String[]{"mountain", "Núi rừng"},
        new String[]{"city", "Thành phố"},
        new String[]{"trekking", "Trekking"},
        new String[]{"cruise", "Du thuyền"},
        new String[]{"cultural", "Văn hoá"}
    );

    @BeforeEach
    void setUp() {
        // Xoá tour trước vì tours.category_id là khoá ngoại trỏ sang categories.
        tours.deleteAll();
        categories.deleteAll();
        SEED.forEach(row -> {
            Category c = new Category();
            c.setSlug(row[0]);
            c.setName(row[1]);
            categories.save(c);
        });
    }

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
        for (String[] row : SEED) {
            mvc.perform(get("/api/v1/tours").contextPath("/api/v1").servletPath("/tours")
                    .param("category", row[0]))
                .andExpect(status().isOk());
        }
    }

    // slug không có trong bảng -> 400, không lặng lẽ trả danh sách rỗng.
    @Test
    void unknownCategorySlugReturns400() throws Exception {
        mvc.perform(get("/api/v1/tours").contextPath("/api/v1").servletPath("/tours")
                .param("category", "khong-ton-tai"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }
}
