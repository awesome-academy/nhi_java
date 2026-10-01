package demo.tripgo.admin;

import demo.tripgo.entity.Category;
import demo.tripgo.entity.Destination;
import demo.tripgo.entity.Tour;
import demo.tripgo.repository.BookingRepository;
import demo.tripgo.repository.CategoryRepository;
import demo.tripgo.repository.DepartureRepository;
import demo.tripgo.repository.DestinationRepository;
import demo.tripgo.repository.TourRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminTourListIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TourRepository tours;
    @Autowired DestinationRepository destinations;
    @Autowired CategoryRepository categories;
    @Autowired BookingRepository bookings;
    @Autowired DepartureRepository departures;
    @Autowired demo.tripgo.TestDataCleaner cleaner;

    private Destination destination;

    @BeforeEach
    void setUp() {
        cleaner.clean();

        destination = new Destination();
        destination.setName("Đà Nẵng");
        destination.setSlug("da-nang");
        destination = destinations.save(destination);
    }

    @Test
    void requiresAdminRole() throws Exception {
        mvc.perform(get("/admin/tours"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/login"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void normalUserIsDenied() throws Exception {
        mvc.perform(get("/admin/tours"))
            .andExpect(redirectedUrl("/admin/login?denied"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listsToursWithDestinationAndPrice() throws Exception {
        saveTour("Đà Nẵng 3N2Đ");

        mvc.perform(get("/admin/tours"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/tours/list"))
            .andExpect(model().attribute("total", 1L))
            .andExpect(content().string(containsString("Đà Nẵng 3N2Đ")))
            .andExpect(content().string(containsString("+ Thêm tour")));
    }

    // Bảng chỉ còn 6 cột; ID và ảnh đã bỏ (ảnh vẫn còn ở form sửa tour).
    @Test
    @WithMockUser(roles = "ADMIN")
    void tableShowsOnlyAgreedColumns() throws Exception {
        Tour withImage = saveTour("Tour có ảnh");
        withImage.setThumbnailUrl("/uploads/dulich.jpg");
        tours.saveAndFlush(withImage);

        mvc.perform(get("/admin/tours"))
            .andExpect(content().string(containsString("<th>Tên tour</th>")))
            .andExpect(content().string(containsString("<th class=\"num\">Thời lượng</th>")))
            .andExpect(content().string(containsString("<th class=\"num\">Đánh giá</th>")))
            .andExpect(content().string(not(containsString("<th>ID</th>"))))
            .andExpect(content().string(not(containsString("<th>Ảnh</th>"))))
            .andExpect(content().string(not(containsString("/uploads/dulich.jpg"))))
            .andExpect(content().string(containsString("3 ngày")));
    }

    // Có khuyến mãi: giá khuyến mãi đứng trên, giá gốc gạch ngang bên dưới.
    @Test
    @WithMockUser(roles = "ADMIN")
    void showsDiscountPriceWithOriginalStruckThrough() throws Exception {
        Tour tour = saveTour("Tour khuyến mãi");
        tour.setDiscountPrice(new BigDecimal("3990000"));
        tours.saveAndFlush(tour);

        mvc.perform(get("/admin/tours"))
            .andExpect(content().string(containsString("<span>3.990.000</span>")))
            .andExpect(content().string(containsString("<span class=\"sub price-old\">")));
    }

    // Điểm làm tròn 1 chữ số; tour chưa ai đánh giá hiện "—" chứ không phải 0.0.
    @Test
    @WithMockUser(roles = "ADMIN")
    void showsRatingOrDashWhenNoReviews() throws Exception {
        Tour rated = saveTour("Tour có đánh giá");
        rated.setRatingAvg(4.5);
        rated.setReviewCount(12);
        tours.saveAndFlush(rated);
        saveTour("Tour chưa đánh giá");

        mvc.perform(get("/admin/tours"))
            .andExpect(content().string(containsString(">4.5</td>")))
            .andExpect(content().string(containsString(">—</td>")))
            .andExpect(content().string(not(containsString(">0.0</td>"))));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void emptyListShowsPlaceholderRow() throws Exception {
        mvc.perform(get("/admin/tours"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Chưa có tour nào")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void searchFiltersByTitle() throws Exception {
        saveTour("Sapa săn mây");
        saveTour("Đà Nẵng 3N2Đ");

        mvc.perform(get("/admin/tours").param("q", "sapa"))
            .andExpect(model().attribute("total", 1L))
            .andExpect(content().string(containsString("Sapa săn mây")))
            .andExpect(content().string(not(containsString("Đà Nẵng 3N2Đ"))));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void searchWithNoMatchExplainsWhy() throws Exception {
        saveTour("Sapa săn mây");

        mvc.perform(get("/admin/tours").param("q", "khong-co-gi"))
            .andExpect(content().string(containsString("Không tìm thấy tour nào khớp từ khoá")));
    }

    // Tour đã xoá mềm không hiện ở danh sách chính (thùng rác là màn riêng, làm sau).
    @Test
    @WithMockUser(roles = "ADMIN")
    void deletedTourIsNotListed() throws Exception {
        saveTour("Tour còn bán");
        Tour gone = saveTour("Tour đã xoá");
        gone.setDeletedAt(LocalDateTime.now());
        tours.saveAndFlush(gone);

        mvc.perform(get("/admin/tours"))
            .andExpect(model().attribute("total", 1L))
            .andExpect(content().string(not(containsString("Tour đã xoá"))));
    }

    // 21 tour / 20 dòng mỗi trang -> đúng 2 trang.
    @Test
    @WithMockUser(roles = "ADMIN")
    void paginatesBeyondOnePage() throws Exception {
        for (int i = 1; i <= 21; i++) {
            saveTour("Tour số " + i);
        }

        mvc.perform(get("/admin/tours"))
            .andExpect(model().attribute("totalPages", 2))
            .andExpect(model().attribute("currentPage", 1));

        mvc.perform(get("/admin/tours").param("page", "2"))
            .andExpect(model().attribute("currentPage", 2));
    }

    // page=0 sẽ làm PageRequest ném lỗi nếu không chặn; sửa tay URL không được làm vỡ trang.
    @Test
    @WithMockUser(roles = "ADMIN")
    void invalidPageNumberFallsBackToFirstPage() throws Exception {
        saveTour("Tour A");

        mvc.perform(get("/admin/tours").param("page", "0"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("currentPage", 1));
    }

    private Tour saveTour(String title) {
        Tour tour = new Tour();
        tour.setTitle(title);
        tour.setSlug(UUID.randomUUID().toString());
        tour.setDestination(destination);
        tour.setCategory(category());
        tour.setDurationDays(3);
        tour.setPrice(new BigDecimal("4500000"));
        tour.setMaxGuests(20);
        tour.setDescription("mô tả");
        return tours.save(tour);
    }

    private Category category() {
        return categories.findBySlug("beach").orElseGet(() -> {
            Category c = new Category();
            c.setSlug("beach");
            c.setName("Biển đảo");
            return categories.save(c);
        });
    }
}
