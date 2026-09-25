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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminTourFormIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TourRepository tours;
    @Autowired DestinationRepository destinations;
    @Autowired CategoryRepository categories;
    @Autowired BookingRepository bookings;
    @Autowired DepartureRepository departures;
    @Autowired demo.tripgo.TestDataCleaner cleaner;

    private Destination destination;
    private Category category;

    @BeforeEach
    void setUp() {
        cleaner.clean();

        destination = new Destination();
        destination.setName("Đà Nẵng");
        destination.setSlug("da-nang");
        destination = destinations.save(destination);

        category = categories.findBySlug("beach").orElseGet(() -> {
            Category c = new Category();
            c.setSlug("beach");
            c.setName("Biển đảo");
            return categories.save(c);
        });
    }

    // ---- Hiển thị form ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void createFormShowsEmptyFieldsAndDropdowns() throws Exception {
        mvc.perform(get("/admin/tours/new"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/tours/form"))
            .andExpect(content().string(containsString("Đà Nẵng")))
            .andExpect(content().string(containsString("Biển đảo")));
    }

    // Kiểm qua model chứ không so chuỗi HTML: Thymeleaf escape ký tự có named entity khi render
    // attribute ("à" -> "&agrave;"), nên containsString trên value= sẽ trượt dù dữ liệu vẫn đúng.
    @Test
    @WithMockUser(roles = "ADMIN")
    void editFormIsPrefilled() throws Exception {
        Tour tour = saveTour("Đà Nẵng 3N2Đ", "da-nang-3n2d");

        mvc.perform(get("/admin/tours/{id}/edit", tour.getId()))
            .andExpect(status().isOk())
            .andExpect(model().attribute("form", allOf(
                hasProperty("id", is(tour.getId())),
                hasProperty("title", is("Đà Nẵng 3N2Đ")),
                hasProperty("slug", is("da-nang-3n2d")),
                hasProperty("destinationId", is(destination.getId())),
                hasProperty("categoryId", is(category.getId())),
                hasProperty("durationDays", is(3)))))
            .andExpect(content().string(containsString("da-nang-3n2d")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void editFormForDeletedTourReturns404() throws Exception {
        Tour tour = saveTour("Tour đã xoá", "tour-da-xoa");
        tour.setDeletedAt(LocalDateTime.now());
        tours.saveAndFlush(tour);

        mvc.perform(get("/admin/tours/{id}/edit", tour.getId()))
            .andExpect(status().isNotFound());
    }

    // ---- Tạo ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void createsTourAndGeneratesSlugFromTitle() throws Exception {
        mvc.perform(post("/admin/tours").with(csrf())
                .param("title", "Đà Nẵng 3N2Đ")
                .param("destinationId", destination.getId().toString())
                .param("categoryId", category.getId().toString())
                .param("price", "4500000")
                .param("durationDays", "3")
                .param("maxGuests", "20"))
            .andExpect(redirectedUrl("/admin/tours"));

        Tour saved = tours.findAll().getFirst();
        assertThat(saved.getTitle()).isEqualTo("Đà Nẵng 3N2Đ");
        assertThat(saved.getSlug()).isEqualTo("da-nang-3n2d");
        assertThat(saved.getPrice()).isEqualByComparingTo("4500000");
    }

    // Slug trùng (kể cả với tour trong thùng rác) phải tự nối hậu tố, không được ném lỗi unique.
    @Test
    @WithMockUser(roles = "ADMIN")
    void duplicateSlugGetsNumericSuffix() throws Exception {
        saveTour("Đà Nẵng 3N2Đ", "da-nang-3n2d");

        mvc.perform(post("/admin/tours").with(csrf())
                .param("title", "Đà Nẵng 3N2Đ")
                .param("destinationId", destination.getId().toString())
                .param("categoryId", category.getId().toString())
                .param("price", "5000000")
                .param("durationDays", "3")
                .param("maxGuests", "20"))
            .andExpect(redirectedUrl("/admin/tours"));

        assertThat(tours.findAll()).extracting(Tour::getSlug)
            .containsExactlyInAnyOrder("da-nang-3n2d", "da-nang-3n2d-2");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void newTourAppearsInPublicApi() throws Exception {
        mvc.perform(post("/admin/tours").with(csrf())
                .param("title", "Tour mới toanh")
                .param("destinationId", destination.getId().toString())
                .param("categoryId", category.getId().toString())
                .param("price", "1000000")
                .param("durationDays", "2")
                .param("maxGuests", "10"));

        // AC: thay đổi qua giao diện phản ánh ngay ở API công khai.
        mvc.perform(get("/api/v1/tours"))
            .andExpect(content().string(containsString("Tour mới toanh")));
    }

    // ---- Validation ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void emptyFormShowsFieldErrorsAndDoesNotSave() throws Exception {
        mvc.perform(post("/admin/tours").with(csrf())
                .param("title", "")
                .param("price", "")
                .param("durationDays", "")
                .param("maxGuests", ""))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/tours/form"))
            .andExpect(model().attributeHasFieldErrors("form",
                "title", "destinationId", "categoryId", "price", "durationDays", "maxGuests"))
            .andExpect(content().string(containsString("Tên tour không được để trống")))
            .andExpect(content().string(containsString("Vui lòng chọn điểm đến")));

        assertThat(tours.count()).isZero();
    }

    // Ràng buộc giữa hai trường: annotation trên một trường không diễn đạt được.
    @Test
    @WithMockUser(roles = "ADMIN")
    void discountMustBeLowerThanPrice() throws Exception {
        mvc.perform(post("/admin/tours").with(csrf())
                .param("title", "Tour A")
                .param("destinationId", destination.getId().toString())
                .param("categoryId", category.getId().toString())
                .param("price", "1000000")
                .param("discountPrice", "1000000")
                .param("durationDays", "3")
                .param("maxGuests", "20"))
            .andExpect(view().name("admin/tours/form"))
            .andExpect(model().attributeHasFieldErrors("form", "discountPrice"))
            .andExpect(content().string(containsString("Giá khuyến mãi phải nhỏ hơn giá gốc")));

        assertThat(tours.count()).isZero();
    }

    // Form lỗi vẫn phải còn dropdown, nếu không admin không sửa lại được.
    @Test
    @WithMockUser(roles = "ADMIN")
    void invalidFormKeepsDropdownsAndTypedValues() throws Exception {
        mvc.perform(post("/admin/tours").with(csrf())
                .param("title", "")
                .param("price", "123456")
                .param("destinationId", destination.getId().toString()))
            .andExpect(model().attributeExists("destinations", "categories"))
            .andExpect(model().attribute("form", hasProperty("price", is(new BigDecimal("123456")))))
            .andExpect(content().string(containsString("123456")));
    }

    // ---- Sửa ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void updatesExistingTour() throws Exception {
        Tour tour = saveTour("Tên cũ", "ten-cu");

        mvc.perform(post("/admin/tours/{id}", tour.getId()).with(csrf())
                .param("title", "Tên mới")
                .param("slug", "ten-cu")
                .param("destinationId", destination.getId().toString())
                .param("categoryId", category.getId().toString())
                .param("price", "7000000")
                .param("durationDays", "5")
                .param("maxGuests", "30"))
            .andExpect(redirectedUrl("/admin/tours"));

        Tour updated = tours.findById(tour.getId()).orElseThrow();
        assertThat(updated.getTitle()).isEqualTo("Tên mới");
        assertThat(updated.getSlug()).isEqualTo("ten-cu");
        assertThat(updated.getDurationDays()).isEqualTo(5);
    }

    // Sửa mà giữ nguyên slug của chính nó thì không được coi là trùng với chính mình.
    @Test
    @WithMockUser(roles = "ADMIN")
    void keepingOwnSlugDoesNotAddSuffix() throws Exception {
        Tour tour = saveTour("Tour A", "tour-a");

        mvc.perform(post("/admin/tours/{id}", tour.getId()).with(csrf())
                .param("title", "Tour A")
                .param("slug", "tour-a")
                .param("destinationId", destination.getId().toString())
                .param("categoryId", category.getId().toString())
                .param("price", "1000000")
                .param("durationDays", "3")
                .param("maxGuests", "20"));

        assertThat(tours.findById(tour.getId()).orElseThrow().getSlug()).isEqualTo("tour-a");
    }

    // Đánh giá/lượt đánh giá là dữ liệu hệ thống, form không được đụng vào.
    @Test
    @WithMockUser(roles = "ADMIN")
    void updateDoesNotResetRating() throws Exception {
        Tour tour = saveTour("Tour A", "tour-a");
        tour.setRatingAvg(4.5);
        tour.setReviewCount(12);
        tours.saveAndFlush(tour);

        mvc.perform(post("/admin/tours/{id}", tour.getId()).with(csrf())
                .param("title", "Tour A đổi tên")
                .param("slug", "tour-a")
                .param("destinationId", destination.getId().toString())
                .param("categoryId", category.getId().toString())
                .param("price", "1000000")
                .param("durationDays", "3")
                .param("maxGuests", "20"));

        Tour updated = tours.findById(tour.getId()).orElseThrow();
        assertThat(updated.getRatingAvg()).isEqualTo(4.5);
        assertThat(updated.getReviewCount()).isEqualTo(12);
    }

    // ---- Bảo vệ ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void createWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/admin/tours")
                .param("title", "Tour A")
                .param("destinationId", destination.getId().toString())
                .param("categoryId", category.getId().toString())
                .param("price", "1000000")
                .param("durationDays", "3")
                .param("maxGuests", "20"))
            .andExpect(status().isForbidden());
        assertThat(tours.count()).isZero();
    }

    @Test
    @WithMockUser(roles = "USER")
    void normalUserCannotOpenForm() throws Exception {
        mvc.perform(get("/admin/tours/new"))
            .andExpect(redirectedUrl("/admin/login?denied"));
    }

    private Tour saveTour(String title, String slug) {
        Tour tour = new Tour();
        tour.setTitle(title);
        tour.setSlug(slug);
        tour.setDestination(destination);
        tour.setCategory(category);
        tour.setDurationDays(3);
        tour.setPrice(new BigDecimal("4500000"));
        tour.setMaxGuests(20);
        tour.setDescription("mô tả");
        return tours.save(tour);
    }
}
