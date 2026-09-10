package demo.tripgo.controller;

import demo.tripgo.entity.Departure;
import demo.tripgo.entity.Destination;
import demo.tripgo.entity.Role;
import demo.tripgo.entity.Tour;
import demo.tripgo.entity.TourCategory;
import demo.tripgo.entity.User;
import demo.tripgo.repository.BookingRepository;
import demo.tripgo.repository.DepartureRepository;
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
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TourRepository tours;
    @Autowired DestinationRepository destinations;
    @Autowired DepartureRepository departures;
    @Autowired BookingRepository bookings;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;

    private Tour tour;
    private LocalDate date;

    @BeforeEach
    void setUp() {
        bookings.deleteAll();
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
        tour = tours.save(tour);

        date = LocalDate.now().plusDays(10);
        saveDeparture(date, 10, 0);
    }

    private void saveDeparture(LocalDate day, int total, int booked) {
        Departure dep = new Departure();
        dep.setTour(tour);
        dep.setDepartureDate(day);
        dep.setTotalSeats(total);
        dep.setBookedSeats(booked);
        departures.save(dep);
    }

    private User saveUser() {
        User user = new User();
        user.setFullName("Booker");
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPassword(encoder.encode("password123"));
        user.setRole(Role.USER);
        return users.save(user);
    }

    private String tokenFor(User user) {
        return "Bearer " + jwt.generateToken(user);
    }

    // Finder không khoá (khỏi cần transaction) để kiểm tra số chỗ còn trong test.
    private int remainingSeatsOn(LocalDate day) {
        return departures.findByTourIdAndDepartureDateBetweenOrderByDepartureDateAsc(tour.getId(), day, day)
            .get(0).getRemainingSeats();
    }

    private String body(Long tourId, LocalDate day, int adults, int children) {
        return """
            {"tourId":%s,"date":"%s","adults":%d,"children":%d,
             "contact":{"fullName":"Nguyen A","email":"a@example.com","phone":"0900000000"}}
            """.formatted(tourId, day, adults, children);
    }

    // ---- POST /bookings ----

    @Test
    void createBookingGeneratesCodeComputesPriceAndDecrementsSeats() throws Exception {
        String token = tokenFor(saveUser());

        mvc.perform(post("/api/v1/bookings").contextPath("/api/v1").servletPath("/bookings")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body(tour.getId(), date, 2, 1)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.message").value("Booking created successfully"))
            .andExpect(jsonPath("$.booking.code").value(org.hamcrest.Matchers.matchesPattern("TG-\\d{4}-\\d{6}")))
            .andExpect(jsonPath("$.booking.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.booking.adults").value(2))
            .andExpect(jsonPath("$.booking.children").value(1))
            // 3 khách * 1,000,000 = 3,000,000 (server tự tính).
            .andExpect(jsonPath("$.booking.totalPrice").value(3000000.0))
            .andExpect(jsonPath("$.booking.contact.email").value("a@example.com"));

        // Trừ chỗ: 10 - 3 = 7 còn lại.
        assertThat(remainingSeatsOn(date)).isEqualTo(7);

        // Đơn xuất hiện ở GET /bookings của user.
        mvc.perform(get("/api/v1/bookings").contextPath("/api/v1").servletPath("/bookings")
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].code").value(org.hamcrest.Matchers.startsWith("TG-")));
    }

    @Test
    void createBookingWithoutTokenReturns401() throws Exception {
        mvc.perform(post("/api/v1/bookings").contextPath("/api/v1").servletPath("/bookings")
                .contentType(MediaType.APPLICATION_JSON).content(body(tour.getId(), date, 2, 0)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void createBookingWithInvalidBodyReturns422WithFieldErrors() throws Exception {
        String token = tokenFor(saveUser());
        // thiếu tourId, adults=0, email sai, date quá khứ, thiếu phone.
        String bad = """
            {"date":"2000-01-01","adults":0,"children":-1,
             "contact":{"fullName":"","email":"not-an-email"}}
            """;
        mvc.perform(post("/api/v1/bookings").contextPath("/api/v1").servletPath("/bookings")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(bad))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.status").value(422))
            .andExpect(jsonPath("$.message").value("Validation failed"))
            .andExpect(jsonPath("$.errors.tourId").isNotEmpty())
            .andExpect(jsonPath("$.errors.adults").isNotEmpty())
            .andExpect(jsonPath("$.errors.date").isNotEmpty());

        // Không tạo đơn nào.
        assertThat(bookings.count()).isZero();
    }

    @Test
    void createBookingForUnknownTourReturns404() throws Exception {
        mvc.perform(post("/api/v1/bookings").contextPath("/api/v1").servletPath("/bookings")
                .header("Authorization", tokenFor(saveUser()))
                .contentType(MediaType.APPLICATION_JSON).content(body(999999L, date, 1, 0)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void createBookingOnDateWithoutDepartureReturns422() throws Exception {
        mvc.perform(post("/api/v1/bookings").contextPath("/api/v1").servletPath("/bookings")
                .header("Authorization", tokenFor(saveUser()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(tour.getId(), date.plusDays(1), 1, 0)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void createBookingWhenSoldOutReturns409() throws Exception {
        saveDeparture(date.plusDays(2), 2, 0); // chỉ 2 chỗ
        mvc.perform(post("/api/v1/bookings").contextPath("/api/v1").servletPath("/bookings")
                .header("Authorization", tokenFor(saveUser()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(tour.getId(), date.plusDays(2), 2, 1))) // xin 3 chỗ
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));
    }

    // ---- GET /bookings/{id} & cross-user ----

    @Test
    void userCannotSeeOthersBooking() throws Exception {
        String tokenA = tokenFor(saveUser());
        String location = mvc.perform(post("/api/v1/bookings").contextPath("/api/v1").servletPath("/bookings")
                .header("Authorization", tokenA)
                .contentType(MediaType.APPLICATION_JSON).content(body(tour.getId(), date, 1, 0)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        long bookingId = Long.parseLong(location.replaceAll(".*\"id\":(\\d+).*", "$1"));

        // User A xem được đơn của mình.
        mvc.perform(get("/api/v1/bookings/" + bookingId).contextPath("/api/v1").servletPath("/bookings/" + bookingId)
                .header("Authorization", tokenA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(bookingId));

        // User B không thấy → 404 (không lộ tồn tại).
        mvc.perform(get("/api/v1/bookings/" + bookingId).contextPath("/api/v1").servletPath("/bookings/" + bookingId)
                .header("Authorization", tokenFor(saveUser())))
            .andExpect(status().isNotFound());

        // GET /bookings của B rỗng.
        mvc.perform(get("/api/v1/bookings").contextPath("/api/v1").servletPath("/bookings")
                .header("Authorization", tokenFor(saveUser())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isEmpty());
    }

    // ---- PATCH /bookings/{id}/cancel ----

    @Test
    void cancelBookingRestoresSeatsAndIsIdempotentlyGuarded() throws Exception {
        String token = tokenFor(saveUser());
        String created = mvc.perform(post("/api/v1/bookings").contextPath("/api/v1").servletPath("/bookings")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body(tour.getId(), date, 2, 0)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        long bookingId = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mvc.perform(patch("/api/v1/bookings/" + bookingId + "/cancel")
                .contextPath("/api/v1").servletPath("/bookings/" + bookingId + "/cancel")
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Booking cancelled successfully"))
            .andExpect(jsonPath("$.booking.status").value("CANCELLED"));

        // Hoàn chỗ: về lại 10.
        assertThat(remainingSeatsOn(date)).isEqualTo(10);

        // Huỷ lần nữa → 409.
        mvc.perform(patch("/api/v1/bookings/" + bookingId + "/cancel")
                .contextPath("/api/v1").servletPath("/bookings/" + bookingId + "/cancel")
                .header("Authorization", token))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void cancelOthersBookingReturns404() throws Exception {
        String created = mvc.perform(post("/api/v1/bookings").contextPath("/api/v1").servletPath("/bookings")
                .header("Authorization", tokenFor(saveUser()))
                .contentType(MediaType.APPLICATION_JSON).content(body(tour.getId(), date, 1, 0)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        long bookingId = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mvc.perform(patch("/api/v1/bookings/" + bookingId + "/cancel")
                .contextPath("/api/v1").servletPath("/bookings/" + bookingId + "/cancel")
                .header("Authorization", tokenFor(saveUser())))
            .andExpect(status().isNotFound());
    }
}
