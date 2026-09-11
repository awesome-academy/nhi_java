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
            // Trả thẳng booking object (6.5), status chữ thường, đơn mới = pending.
            .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern("TG-\\d{4}-\\d{6}")))
            .andExpect(jsonPath("$.status").value("pending"))
            .andExpect(jsonPath("$.tourId").value(tour.getId()))
            .andExpect(jsonPath("$.adults").value(2))
            .andExpect(jsonPath("$.children").value(1))
            // 3 khách * 1,000,000 = 3,000,000 (server tự tính).
            .andExpect(jsonPath("$.totalPrice").value(3000000.0));

        // Trừ chỗ: 10 - 3 = 7 còn lại.
        assertThat(remainingSeatsOn(date)).isEqualTo(7);

        // Đơn xuất hiện ở GET /bookings (paginated) với tour gọn { title, thumbnail }.
        mvc.perform(get("/api/v1/bookings").contextPath("/api/v1").servletPath("/bookings")
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].code").value(org.hamcrest.Matchers.startsWith("TG-")))
            .andExpect(jsonPath("$.data[0].status").value("pending"))
            .andExpect(jsonPath("$.data[0].tour.title").value("Da Nang Tour"));
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
            .andExpect(jsonPath("$.error.code").value("UNPROCESSABLE_ENTITY"))
            .andExpect(jsonPath("$.error.message").value("Validation failed"))
            .andExpect(jsonPath("$.error.fields.tourId").isNotEmpty())
            .andExpect(jsonPath("$.error.fields.adults").isNotEmpty())
            .andExpect(jsonPath("$.error.fields.date").isNotEmpty());

        // Không tạo đơn nào.
        assertThat(bookings.count()).isZero();
    }

    @Test
    void createBookingWithInvalidPhoneReturns422() throws Exception {
        String bad = """
            {"tourId":%s,"date":"%s","adults":1,"children":0,
             "contact":{"fullName":"Nguyen A","email":"a@example.com","phone":"abc123","note":"gọi trước"}}
            """.formatted(tour.getId(), date);
        mvc.perform(post("/api/v1/bookings").contextPath("/api/v1").servletPath("/bookings")
                .header("Authorization", tokenFor(saveUser()))
                .contentType(MediaType.APPLICATION_JSON).content(bad))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.fields['contact.phone']").value("Invalid phone number"));
    }

    @Test
    void createBookingForUnknownTourReturns404() throws Exception {
        mvc.perform(post("/api/v1/bookings").contextPath("/api/v1").servletPath("/bookings")
                .header("Authorization", tokenFor(saveUser()))
                .contentType(MediaType.APPLICATION_JSON).content(body(999999L, date, 1, 0)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void createBookingOnDateWithoutDepartureReturns422() throws Exception {
        mvc.perform(post("/api/v1/bookings").contextPath("/api/v1").servletPath("/bookings")
                .header("Authorization", tokenFor(saveUser()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(tour.getId(), date.plusDays(1), 1, 0)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("UNPROCESSABLE_ENTITY"));
    }

    @Test
    void createBookingWhenSoldOutReturns409() throws Exception {
        saveDeparture(date.plusDays(2), 2, 0); // chỉ 2 chỗ
        mvc.perform(post("/api/v1/bookings").contextPath("/api/v1").servletPath("/bookings")
                .header("Authorization", tokenFor(saveUser()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(tour.getId(), date.plusDays(2), 2, 1))) // xin 3 chỗ
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("CONFLICT"));
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
            .andExpect(jsonPath("$.status").value("cancelled"));

        // Hoàn chỗ: về lại 10.
        assertThat(remainingSeatsOn(date)).isEqualTo(10);

        // Huỷ lần nữa → 409.
        mvc.perform(patch("/api/v1/bookings/" + bookingId + "/cancel")
                .contextPath("/api/v1").servletPath("/bookings/" + bookingId + "/cancel")
                .header("Authorization", token))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("CONFLICT"));
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
