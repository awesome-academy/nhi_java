package demo.tripgo.controller;

import demo.tripgo.entity.Departure;
import demo.tripgo.entity.Destination;
import demo.tripgo.entity.Role;
import demo.tripgo.entity.Tour;
import demo.tripgo.entity.TourCategory;
import demo.tripgo.entity.User;
import demo.tripgo.repository.DepartureRepository;
import demo.tripgo.repository.DestinationRepository;
import demo.tripgo.repository.ReviewRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TourReviewAvailabilityIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TourRepository tours;
    @Autowired DestinationRepository destinations;
    @Autowired DepartureRepository departures;
    @MockitoSpyBean ReviewRepository reviews;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;

    private Tour tour;

    @BeforeEach
    void setUp() {
        reviews.deleteAll();
        departures.deleteAll();
        tours.deleteAll();
        destinations.deleteAll();
        users.deleteAll();

        Destination d = new Destination();
        d.setName("Da Nang");
        d.setSlug("da-nang");
        d = destinations.save(d);

        tour = new Tour();
        tour.setTitle("Da Nang Tour");
        tour.setDestination(d);
        tour.setCategory(TourCategory.BEACH);
        tour.setDurationDays(3);
        tour.setPrice(new BigDecimal("1000000"));
        tour.setMaxGuests(20);
        tour.setDescription("desc");
        tour = tours.save(tour);
    }

    private User saveUser() {
        User user = new User();
        user.setFullName("Reviewer");
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPassword(encoder.encode("password123"));
        user.setRole(Role.USER);
        return users.save(user);
    }

    private String tokenFor(User user) {
        return "Bearer " + jwt.generateToken(user);
    }

    private void saveDeparture(LocalDate date, int total, int booked) {
        Departure dep = new Departure();
        dep.setTour(tour);
        dep.setDepartureDate(date);
        dep.setTotalSeats(total);
        dep.setBookedSeats(booked);
        departures.save(dep);
    }

    private String reviewBody(int rating, String comment) {
        return """
            {"rating":%d,"comment":"%s"}
            """.formatted(rating, comment);
    }

    // ---- GET /tours/{id}/availability ----

    @Test
    void availabilityFilteredByMonthReturnsRemainingSeats() throws Exception {
        YearMonth month = YearMonth.now().plusMonths(1);
        saveDeparture(month.atDay(5), 10, 3);
        saveDeparture(month.atDay(20), 8, 8);
        saveDeparture(month.plusMonths(1).atDay(1), 5, 0); // tháng khác, không được lấy

        mvc.perform(get("/api/v1/tours/" + tour.getId() + "/availability")
                .contextPath("/api/v1").servletPath("/tours/" + tour.getId() + "/availability")
                .param("month", month.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].totalSeats").value(10))
            .andExpect(jsonPath("$.data[0].remainingSeats").value(7))
            .andExpect(jsonPath("$.data[1].remainingSeats").value(0));
    }

    @Test
    void availabilityWithoutMonthReturnsOnlyUpcoming() throws Exception {
        saveDeparture(LocalDate.now().minusDays(5), 10, 0); // quá khứ, bỏ qua
        saveDeparture(LocalDate.now().plusDays(5), 10, 2);
        saveDeparture(LocalDate.now().plusDays(30), 10, 1);

        mvc.perform(get("/api/v1/tours/" + tour.getId() + "/availability")
                .contextPath("/api/v1").servletPath("/tours/" + tour.getId() + "/availability"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void availabilityWithBadMonthReturns400() throws Exception {
        mvc.perform(get("/api/v1/tours/" + tour.getId() + "/availability")
                .contextPath("/api/v1").servletPath("/tours/" + tour.getId() + "/availability")
                .param("month", "2026-13"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void availabilityForUnknownTourReturns404() throws Exception {
        mvc.perform(get("/api/v1/tours/999999/availability")
                .contextPath("/api/v1").servletPath("/tours/999999/availability"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    // ---- GET /tours/{id}/reviews ----

    @Test
    void reviewsEmptyReturnsEmptyDataZeroTotalAndZeroAverage() throws Exception {
        mvc.perform(get("/api/v1/tours/" + tour.getId() + "/reviews")
                .contextPath("/api/v1").servletPath("/tours/" + tour.getId() + "/reviews"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isEmpty())
            .andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.averageRating").value(0.0));
    }

    @Test
    void reviewsReturnPaginatedListWithAverage() throws Exception {
        mvc.perform(post("/api/v1/tours/" + tour.getId() + "/reviews")
                .contextPath("/api/v1").servletPath("/tours/" + tour.getId() + "/reviews")
                .header("Authorization", tokenFor(saveUser()))
                .contentType(MediaType.APPLICATION_JSON).content(reviewBody(4, "good")))
            .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/tours/" + tour.getId() + "/reviews")
                .contextPath("/api/v1").servletPath("/tours/" + tour.getId() + "/reviews")
                .header("Authorization", tokenFor(saveUser()))
                .contentType(MediaType.APPLICATION_JSON).content(reviewBody(2, "meh")))
            .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/tours/" + tour.getId() + "/reviews")
                .contextPath("/api/v1").servletPath("/tours/" + tour.getId() + "/reviews")
                .param("page", "1").param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.averageRating").value(3.0))
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].userFullName").value("Reviewer"))
            .andExpect(jsonPath("$.data[0].rating").isNotEmpty());
    }

    // ---- POST /tours/{id}/reviews ----

    @Test
    void createReviewUpdatesTourRatingAggregate() throws Exception {
        mvc.perform(post("/api/v1/tours/" + tour.getId() + "/reviews")
                .contextPath("/api/v1").servletPath("/tours/" + tour.getId() + "/reviews")
                .header("Authorization", tokenFor(saveUser()))
                .contentType(MediaType.APPLICATION_JSON).content(reviewBody(5, "great")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.message").value("Review created successfully"))
            .andExpect(jsonPath("$.review.rating").value(5))
            .andExpect(jsonPath("$.review.userFullName").value("Reviewer"));

        // Rating denormalized trên tour phải cập nhật để màn chi tiết phản ánh đúng.
        mvc.perform(get("/api/v1/tours/" + tour.getId())
                .contextPath("/api/v1").servletPath("/tours/" + tour.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ratingAvg").value(5.0))
            .andExpect(jsonPath("$.reviewCount").value(1));
    }

    @Test
    void createReviewWithoutAuthReturns401() throws Exception {
        mvc.perform(post("/api/v1/tours/" + tour.getId() + "/reviews")
                .contextPath("/api/v1").servletPath("/tours/" + tour.getId() + "/reviews")
                .contentType(MediaType.APPLICATION_JSON).content(reviewBody(4, "nice")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void createDuplicateReviewBySameUserReturns409() throws Exception {
        String token = tokenFor(saveUser());
        mvc.perform(post("/api/v1/tours/" + tour.getId() + "/reviews")
                .contextPath("/api/v1").servletPath("/tours/" + tour.getId() + "/reviews")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(reviewBody(4, "first")))
            .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/tours/" + tour.getId() + "/reviews")
                .contextPath("/api/v1").servletPath("/tours/" + tour.getId() + "/reviews")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(reviewBody(5, "again")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void createReviewWithInvalidRatingReturns422() throws Exception {
        for (int rating : new int[]{0, 6}) {
            mvc.perform(post("/api/v1/tours/" + tour.getId() + "/reviews")
                    .contextPath("/api/v1").servletPath("/tours/" + tour.getId() + "/reviews")
                    .header("Authorization", tokenFor(saveUser()))
                    .contentType(MediaType.APPLICATION_JSON).content(reviewBody(rating, "x")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.rating").isNotEmpty());
        }
    }

    @Test
    void raceLoserOnDuplicateStillGetsConsistentMessage() throws Exception {
        // Giả lập cửa sổ race: pre-check existsBy luôn báo "chưa có" nên cả hai request đều qua;
        // chỉ unique constraint ở DB chặn request thua. Message vẫn phải rõ ràng, không phải 409 chung.
        doReturn(false).when(reviews).existsByTourIdAndUserId(anyLong(), anyLong());
        String token = tokenFor(saveUser());

        mvc.perform(post("/api/v1/tours/" + tour.getId() + "/reviews")
                .contextPath("/api/v1").servletPath("/tours/" + tour.getId() + "/reviews")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(reviewBody(4, "first")))
            .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/tours/" + tour.getId() + "/reviews")
                .contextPath("/api/v1").servletPath("/tours/" + tour.getId() + "/reviews")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(reviewBody(5, "again")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("already reviewed")));
    }

    @Test
    void createReviewForUnknownTourReturns404() throws Exception {
        mvc.perform(post("/api/v1/tours/999999/reviews")
                .contextPath("/api/v1").servletPath("/tours/999999/reviews")
                .header("Authorization", tokenFor(saveUser()))
                .contentType(MediaType.APPLICATION_JSON).content(reviewBody(4, "x")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }
}
