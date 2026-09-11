package demo.tripgo.controller;

import demo.tripgo.entity.Destination;
import demo.tripgo.entity.Role;
import demo.tripgo.entity.Tour;
import demo.tripgo.entity.TourCategory;
import demo.tripgo.entity.User;
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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WishlistIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TourRepository tours;
    @Autowired DestinationRepository destinations;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;

    private Tour tourA;
    private Tour tourB;

    @BeforeEach
    void setUp() {
        users.deleteAll();
        tours.deleteAll();
        destinations.deleteAll();

        Destination d = new Destination();
        d.setName("Da Nang");
        d.setSlug("da-nang");
        d = destinations.save(d);

        tourA = saveTour("Tour A", d);
        tourB = saveTour("Tour B", d);
    }

    private Tour saveTour(String title, Destination destination) {
        Tour tour = new Tour();
        tour.setTitle(title);
        tour.setDestination(destination);
        tour.setCategory(TourCategory.BEACH);
        tour.setDurationDays(3);
        tour.setPrice(new BigDecimal("1000000"));
        tour.setMaxGuests(20);
        tour.setDescription("desc");
        return tours.save(tour);
    }

    private String tokenFor() {
        User user = new User();
        user.setFullName("Wishlist User");
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPassword(encoder.encode("password123"));
        user.setRole(Role.USER);
        return "Bearer " + jwt.generateToken(users.save(user));
    }

    private String body(Long tourId) {
        return "{\"tourId\":%d}".formatted(tourId);
    }

    // ---- Quyền truy cập ----

    @Test
    void allWishlistEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/wishlist").contextPath("/api/v1").servletPath("/wishlist"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/wishlist").contextPath("/api/v1").servletPath("/wishlist")
                .contentType(MediaType.APPLICATION_JSON).content(body(tourA.getId())))
            .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/wishlist/" + tourA.getId())
                .contextPath("/api/v1").servletPath("/wishlist/" + tourA.getId()))
            .andExpect(status().isUnauthorized());
    }

    // ---- Luồng chính ----

    @Test
    void wishlistStartsEmpty() throws Exception {
        mvc.perform(get("/api/v1/wishlist").contextPath("/api/v1").servletPath("/wishlist")
                .header("Authorization", tokenFor()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tourIds").isArray())
            .andExpect(jsonPath("$.tourIds").isEmpty());
    }

    @Test
    void addThenGetThenRemoveReturnsCurrentList() throws Exception {
        String token = tokenFor();

        mvc.perform(post("/api/v1/wishlist").contextPath("/api/v1").servletPath("/wishlist")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body(tourA.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tourIds.length()").value(1))
            .andExpect(jsonPath("$.tourIds[0]").value(tourA.getId()));

        mvc.perform(post("/api/v1/wishlist").contextPath("/api/v1").servletPath("/wishlist")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body(tourB.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tourIds.length()").value(2));

        mvc.perform(get("/api/v1/wishlist").contextPath("/api/v1").servletPath("/wishlist")
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tourIds.length()").value(2));

        mvc.perform(delete("/api/v1/wishlist/" + tourA.getId())
                .contextPath("/api/v1").servletPath("/wishlist/" + tourA.getId())
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tourIds.length()").value(1))
            .andExpect(jsonPath("$.tourIds[0]").value(tourB.getId()));
    }

    // ---- AC 1: thêm trùng không tạo bản ghi lặp ----

    @Test
    void addingSameTourTwiceDoesNotDuplicate() throws Exception {
        String token = tokenFor();

        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/wishlist").contextPath("/api/v1").servletPath("/wishlist")
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON).content(body(tourA.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tourIds.length()").value(1));
        }

        mvc.perform(get("/api/v1/wishlist").contextPath("/api/v1").servletPath("/wishlist")
                .header("Authorization", token))
            .andExpect(jsonPath("$.tourIds.length()").value(1));
    }

    // ---- AC 2: chỉ ảnh hưởng wishlist của chính user ----

    @Test
    void wishlistIsIsolatedBetweenUsers() throws Exception {
        String tokenA = tokenFor();
        String tokenB = tokenFor();

        mvc.perform(post("/api/v1/wishlist").contextPath("/api/v1").servletPath("/wishlist")
                .header("Authorization", tokenA)
                .contentType(MediaType.APPLICATION_JSON).content(body(tourA.getId())))
            .andExpect(status().isOk());

        // User B không thấy tour của A.
        mvc.perform(get("/api/v1/wishlist").contextPath("/api/v1").servletPath("/wishlist")
                .header("Authorization", tokenB))
            .andExpect(jsonPath("$.tourIds").isEmpty());

        // B xoá đúng tourId đó cũng không đụng được wishlist của A.
        mvc.perform(delete("/api/v1/wishlist/" + tourA.getId())
                .contextPath("/api/v1").servletPath("/wishlist/" + tourA.getId())
                .header("Authorization", tokenB))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tourIds").isEmpty());

        mvc.perform(get("/api/v1/wishlist").contextPath("/api/v1").servletPath("/wishlist")
                .header("Authorization", tokenA))
            .andExpect(jsonPath("$.tourIds.length()").value(1));
    }

    // ---- Trường hợp biên ----

    @Test
    void addingUnknownTourReturns404() throws Exception {
        mvc.perform(post("/api/v1/wishlist").contextPath("/api/v1").servletPath("/wishlist")
                .header("Authorization", tokenFor())
                .contentType(MediaType.APPLICATION_JSON).content(body(999999L)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.message").value("Không tìm thấy tour"));
    }

    @Test
    void addingWithoutTourIdReturns422() throws Exception {
        mvc.perform(post("/api/v1/wishlist").contextPath("/api/v1").servletPath("/wishlist")
                .header("Authorization", tokenFor())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.fields.tourId").isNotEmpty());
    }

    // Body sai kiểu (mảng thay vì số) phải ra lỗi validate, KHÔNG được ra 401.
    // Từng là bug: exception lọt ra /error, filter không chạy lại -> SecurityContext rỗng -> 401.
    @Test
    void addingWithWrongTypeReturns422NotUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/wishlist").contextPath("/api/v1").servletPath("/wishlist")
                .header("Authorization", tokenFor())
                .contentType(MediaType.APPLICATION_JSON).content("{\"tourId\":[\"3\",\"4\"]}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("VALIDATION"))
            .andExpect(jsonPath("$.error.fields.tourId").isNotEmpty());
    }

    // JSON hỏng hẳn (không xác định được trường nào) -> 400, vẫn không phải 401.
    @Test
    void addingWithBrokenJsonReturns400NotUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/wishlist").contextPath("/api/v1").servletPath("/wishlist")
                .header("Authorization", tokenFor())
                .contentType(MediaType.APPLICATION_JSON).content("{\"tourId\": "))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    // DELETE phải idempotent: xoá thứ không có trong wishlist vẫn 200.
    @Test
    void removingTourNotInWishlistIsNoOp() throws Exception {
        mvc.perform(delete("/api/v1/wishlist/" + tourA.getId())
                .contextPath("/api/v1").servletPath("/wishlist/" + tourA.getId())
                .header("Authorization", tokenFor()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tourIds").isEmpty());
    }
}
