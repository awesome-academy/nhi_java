package demo.tripgo.admin;

import demo.tripgo.entity.Booking;
import demo.tripgo.entity.BookingStatus;
import demo.tripgo.entity.Category;
import demo.tripgo.entity.ContactInfo;
import demo.tripgo.entity.Departure;
import demo.tripgo.entity.Destination;
import demo.tripgo.entity.Role;
import demo.tripgo.entity.Tour;
import demo.tripgo.entity.User;
import demo.tripgo.repository.BookingRepository;
import demo.tripgo.repository.CategoryRepository;
import demo.tripgo.repository.DepartureRepository;
import demo.tripgo.repository.DestinationRepository;
import demo.tripgo.repository.TourRepository;
import demo.tripgo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminBookingIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired BookingRepository bookings;
    @Autowired DepartureRepository departures;
    @Autowired TourRepository tours;
    @Autowired DestinationRepository destinations;
    @Autowired CategoryRepository categories;
    @Autowired UserRepository users;

    private Tour tour;
    private Departure departure;

    @BeforeEach
    void setUp() {
        bookings.deleteAll();
        departures.deleteAll();
        tours.deleteAll();
        destinations.deleteAll();
        users.deleteAll();

        Destination destination = new Destination();
        destination.setName("Đà Nẵng");
        destination.setSlug("da-nang");
        destination = destinations.save(destination);

        Category category = categories.findBySlug("beach").orElseGet(() -> {
            Category c = new Category();
            c.setSlug("beach");
            c.setName("Biển đảo");
            return categories.save(c);
        });

        tour = new Tour();
        tour.setTitle("Đà Nẵng 3N2Đ");
        tour.setSlug("da-nang-3n2d");
        tour.setDestination(destination);
        tour.setCategory(category);
        tour.setDurationDays(3);
        tour.setPrice(new BigDecimal("4500000"));
        tour.setMaxGuests(20);
        tour = tours.save(tour);

        departure = new Departure();
        departure.setTour(tour);
        departure.setDepartureDate(LocalDate.now().plusDays(30));
        departure.setTotalSeats(20);
        departure.setBookedSeats(0);
        departure = departures.save(departure);
    }

    // ---- Danh sách & lọc ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void listsBookingsWithCustomerTourAndStatus() throws Exception {
        saveBooking(BookingStatus.PENDING, 2);

        mvc.perform(get("/admin/bookings"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/bookings/list"))
            .andExpect(model().attribute("total", 1L))
            .andExpect(content().string(containsString("Đà Nẵng 3N2Đ")))
            .andExpect(content().string(containsString("Chờ xác nhận")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void filtersByStatus() throws Exception {
        saveBooking(BookingStatus.PENDING, 2);
        Booking confirmed = saveBooking(BookingStatus.CONFIRMED, 1);

        mvc.perform(get("/admin/bookings").param("status", "CONFIRMED"))
            .andExpect(model().attribute("total", 1L))
            .andExpect(content().string(containsString(confirmed.getCode())));

        mvc.perform(get("/admin/bookings").param("status", "CANCELLED"))
            .andExpect(model().attribute("total", 0L))
            .andExpect(content().string(containsString("Không có đơn nào ở trạng thái này")));
    }

    // Người dùng sửa tay URL: không được làm vỡ trang.
    @Test
    @WithMockUser(roles = "ADMIN")
    void unknownStatusFallsBackToNoFilter() throws Exception {
        saveBooking(BookingStatus.PENDING, 2);

        mvc.perform(get("/admin/bookings").param("status", "KHONG-CO-THAT"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("selectedStatus", (Object) null))
            .andExpect(model().attribute("total", 1L));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void emptyListSaysSo() throws Exception {
        mvc.perform(get("/admin/bookings"))
            .andExpect(content().string(containsString("Chưa có đơn đặt nào")));
    }

    // ---- Xác nhận ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void confirmsPendingBookingWithoutTouchingSeats() throws Exception {
        Booking booking = saveBooking(BookingStatus.PENDING, 3);
        departure.setBookedSeats(3);
        departures.saveAndFlush(departure);

        mvc.perform(post("/admin/bookings/{id}/confirm", booking.getId()).with(csrf()))
            .andExpect(redirectedUrl("/admin/bookings"))
            .andExpect(flash().attribute("flashMessage", containsString("Đã xác nhận đơn")));

        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus())
            .isEqualTo(BookingStatus.CONFIRMED);
        // Chỗ đã trừ lúc khách đặt, xác nhận không được trừ thêm lần nữa.
        assertThat(departures.findById(departure.getId()).orElseThrow().getBookedSeats()).isEqualTo(3);
    }

    // ---- Huỷ ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void cancelReleasesSeats() throws Exception {
        Booking booking = saveBooking(BookingStatus.PENDING, 4);
        departure.setBookedSeats(4);
        departures.saveAndFlush(departure);

        mvc.perform(post("/admin/bookings/{id}/cancel", booking.getId()).with(csrf()))
            .andExpect(flash().attribute("flashMessage", containsString("số chỗ đã được hoàn lại")));

        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus())
            .isEqualTo(BookingStatus.CANCELLED);
        assertThat(departures.findById(departure.getId()).orElseThrow().getBookedSeats()).isZero();
    }

    // ---- Chuyển trạng thái không hợp lệ ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void cannotConfirmAlreadyConfirmedBooking() throws Exception {
        Booking booking = saveBooking(BookingStatus.CONFIRMED, 2);

        mvc.perform(post("/admin/bookings/{id}/confirm", booking.getId()).with(csrf()))
            .andExpect(flash().attribute("flashType", "error"))
            .andExpect(flash().attribute("flashMessage", containsString("chỉ đơn chờ xác nhận")));

        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus())
            .isEqualTo(BookingStatus.CONFIRMED);
    }

    // Huỷ đơn đã huỷ không được trừ chỗ lần hai — đây là chỗ dễ sinh lỗi âm số chỗ.
    @Test
    @WithMockUser(roles = "ADMIN")
    void cannotCancelTwiceAndSeatsStayCorrect() throws Exception {
        Booking booking = saveBooking(BookingStatus.PENDING, 5);
        departure.setBookedSeats(5);
        departures.saveAndFlush(departure);

        mvc.perform(post("/admin/bookings/{id}/cancel", booking.getId()).with(csrf()));
        mvc.perform(post("/admin/bookings/{id}/cancel", booking.getId()).with(csrf()))
            .andExpect(flash().attribute("flashType", "error"));

        assertThat(departures.findById(departure.getId()).orElseThrow().getBookedSeats()).isZero();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unknownBookingReturns404() throws Exception {
        mvc.perform(post("/admin/bookings/{id}/confirm", 999999).with(csrf()))
            .andExpect(status().isNotFound());
    }

    // ---- Giữ bộ lọc sau khi thao tác ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void keepsFilterAfterStatusChange() throws Exception {
        Booking booking = saveBooking(BookingStatus.PENDING, 2);

        mvc.perform(post("/admin/bookings/{id}/confirm", booking.getId()).with(csrf())
                .param("status", "PENDING"))
            .andExpect(redirectedUrl("/admin/bookings?status=PENDING"));
    }

    // ---- Bảo vệ ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void changeWithoutCsrfIsRejected() throws Exception {
        Booking booking = saveBooking(BookingStatus.PENDING, 2);

        mvc.perform(post("/admin/bookings/{id}/confirm", booking.getId()))
            .andExpect(status().isForbidden());

        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus())
            .isEqualTo(BookingStatus.PENDING);
    }

    @Test
    @WithMockUser(roles = "USER")
    void normalUserCannotSeeBookings() throws Exception {
        mvc.perform(get("/admin/bookings"))
            .andExpect(redirectedUrl("/admin/login?denied"));
    }

    @Test
    void anonymousIsRedirectedToLogin() throws Exception {
        mvc.perform(get("/admin/bookings"))
            .andExpect(redirectedUrl("/admin/login"));
    }

    // Đơn của khách khác vẫn phải hiện: admin xem toàn bộ, không giới hạn theo user.
    @Test
    @WithMockUser(roles = "ADMIN")
    void showsBookingsOfAllCustomers() throws Exception {
        Booking a = saveBooking(BookingStatus.PENDING, 1);
        Booking b = saveBooking(BookingStatus.PENDING, 1);

        mvc.perform(get("/admin/bookings"))
            .andExpect(model().attribute("total", 2L))
            .andExpect(content().string(containsString(a.getCode())))
            .andExpect(content().string(containsString(b.getCode())))
            .andExpect(content().string(not(containsString("Chưa có đơn đặt nào"))));
    }

    private Booking saveBooking(BookingStatus status, int adults) {
        User user = new User();
        user.setFullName("Khách " + UUID.randomUUID().toString().substring(0, 4));
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPassword("x");
        user.setRole(Role.USER);
        user = users.save(user);

        ContactInfo contact = new ContactInfo();
        contact.setFullName(user.getFullName());
        contact.setEmail(user.getEmail());
        contact.setPhone("0900000000");

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setTour(tour);
        booking.setDeparture(departure);
        booking.setCode("TG-2026-" + UUID.randomUUID().toString().substring(0, 6));
        booking.setAdults(adults);
        booking.setChildren(0);
        booking.setTotalPrice(new BigDecimal("4500000").multiply(BigDecimal.valueOf(adults)));
        booking.setStatus(status);
        booking.setContact(contact);
        return bookings.save(booking);
    }
}
