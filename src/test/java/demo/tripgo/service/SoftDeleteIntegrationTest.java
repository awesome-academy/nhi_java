package demo.tripgo.service;

import demo.tripgo.entity.Category;
import demo.tripgo.entity.Destination;
import demo.tripgo.entity.Role;
import demo.tripgo.entity.Tour;
import demo.tripgo.entity.User;
import demo.tripgo.repository.CategoryRepository;
import demo.tripgo.repository.DestinationRepository;
import demo.tripgo.repository.TourRepository;
import demo.tripgo.repository.UserRepository;
import demo.tripgo.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Xoá mềm phải "vô hình" với khách ở MỌI luồng công khai, nhưng hàng vẫn còn trong DB.
// Mỗi test ở đây tương ứng một điểm chạm đã rà trong mã nguồn.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SoftDeleteIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TourRepository tours;
    @Autowired DestinationRepository destinations;
    @Autowired CategoryRepository categories;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;

    private Destination destination;
    private Tour live;
    private Tour deleted;

    @BeforeEach
    void setUp() {
        users.deleteAll();
        tours.deleteAll();
        destinations.deleteAll();

        destination = new Destination();
        destination.setName("Da Nang");
        destination.setSlug("da-nang");
        destination = destinations.save(destination);

        live = saveTour("Tour còn bán");
        deleted = saveTour("Tour đã xoá");
        deleted.setDeletedAt(LocalDateTime.now());
        deleted = tours.save(deleted);
    }

    // ---- Danh sách & chi tiết ----

    @Test
    void deletedTourDisappearsFromPublicList() throws Exception {
        mvc.perform(get("/api/v1/tours"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].id").value(live.getId()));
    }

    @Test
    void deletedTourIsNotFoundByIdOrSlug() throws Exception {
        mvc.perform(get("/api/v1/tours/" + deleted.getId()))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/tours/" + live.getId()))
            .andExpect(status().isOk());
    }

    @Test
    void deletedTourHidesItsAvailabilityAndReviews() throws Exception {
        mvc.perform(get("/api/v1/tours/" + deleted.getId() + "/availability"))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/tours/" + deleted.getId() + "/reviews"))
            .andExpect(status().isNotFound());
    }

    // ---- Không đặt/đánh giá được tour đã xoá ----

    @Test
    void cannotBookDeletedTour() throws Exception {
        mvc.perform(post("/api/v1/bookings")
                .header("Authorization", tokenFor())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tourId":%d,"date":"2030-01-01","adults":2,"children":0,
                     "contact":{"fullName":"Khach","email":"k@example.com","phone":"0900000000"}}
                    """.formatted(deleted.getId())))
            .andExpect(status().isNotFound());
    }

    @Test
    void cannotAddDeletedTourToWishlist() throws Exception {
        mvc.perform(post("/api/v1/wishlist")
                .header("Authorization", tokenFor())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tourId\":%d}".formatted(deleted.getId())))
            .andExpect(status().isNotFound());
    }

    // Tour đã lưu rồi mới bị xoá: biến khỏi wishlist nhưng hàng nối vẫn còn để khôi phục được.
    @Test
    void tourDeletedAfterBeingSavedDropsOutOfWishlist() throws Exception {
        String token = tokenFor();

        mvc.perform(post("/api/v1/wishlist")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tourId\":%d}".formatted(live.getId())))
            .andExpect(jsonPath("$.tourIds.length()").value(1));

        live.setDeletedAt(LocalDateTime.now());
        tours.saveAndFlush(live);

        mvc.perform(get("/api/v1/wishlist").header("Authorization", token))
            .andExpect(jsonPath("$.tourIds").isEmpty());

        // Khôi phục -> hiện lại.
        live.setDeletedAt(null);
        tours.saveAndFlush(live);

        mvc.perform(get("/api/v1/wishlist").header("Authorization", token))
            .andExpect(jsonPath("$.tourIds.length()").value(1));
    }

    // ---- Điểm đến ----

    @Test
    void destinationTourCountIgnoresDeletedTours() throws Exception {
        mvc.perform(get("/api/v1/destinations"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].tourCount").value(1));
    }

    @Test
    void deletedDestinationDisappearsButZeroTourOneStays() throws Exception {
        Destination empty = new Destination();
        empty.setName("Sapa");
        empty.setSlug("sapa");
        destinations.save(empty);

        // Điểm đến chưa có tour nào vẫn phải xuất hiện (left join, không bị biến thành inner join).
        mvc.perform(get("/api/v1/destinations"))
            .andExpect(jsonPath("$.data.length()").value(2));

        empty.setDeletedAt(LocalDateTime.now());
        destinations.saveAndFlush(empty);

        mvc.perform(get("/api/v1/destinations"))
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].slug").value("da-nang"));
    }

    // ---- Dữ liệu vẫn còn: đây là điểm khác biệt với xoá cứng ----

    @Test
    void deletedRowIsStillInDatabase() {
        assertThat(tours.findById(deleted.getId())).isPresent();
        assertThat(tours.findActiveById(deleted.getId())).isEmpty();
        assertThat(tours.count()).isEqualTo(2);
    }

    private Tour saveTour(String title) {
        Tour tour = new Tour();
        tour.setTitle(title);
        tour.setSlug(UUID.randomUUID().toString());
        tour.setDestination(destination);
        tour.setCategory(category());
        tour.setDurationDays(3);
        tour.setPrice(new BigDecimal("1000000"));
        tour.setMaxGuests(20);
        tour.setDescription("desc");
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

    private String tokenFor() {
        User user = new User();
        user.setFullName("Khach");
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPassword(encoder.encode("password123"));
        user.setRole(Role.USER);
        return "Bearer " + jwt.generateToken(users.save(user));
    }
}
