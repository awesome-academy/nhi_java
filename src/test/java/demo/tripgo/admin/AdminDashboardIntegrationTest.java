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
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminDashboardIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TourRepository tours;
    @Autowired DestinationRepository destinations;
    @Autowired CategoryRepository categories;
    @Autowired BookingRepository bookings;
    @Autowired DepartureRepository departures;
    @Autowired UserRepository users;

    private Destination destination;
    private Tour tour;
    private Departure departure;

    @BeforeEach
    void setUp() {
        bookings.deleteAll();
        departures.deleteAll();
        tours.deleteAll();
        destinations.deleteAll();
        users.deleteAll();

        destination = new Destination();
        destination.setName("Đà Nẵng");
        destination.setSlug("da-nang");
        destination = destinations.save(destination);

        tour = saveTour();
        departure = new Departure();
        departure.setTour(tour);
        departure.setDepartureDate(LocalDate.now().plusDays(30));
        departure.setTotalSeats(20);
        departure.setBookedSeats(0);
        departure = departures.save(departure);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void emptySystemShowsZeros() throws Exception {
        // Xoá ngày khởi hành trước: tour_departures tham chiếu tours bằng khoá ngoại.
        departures.deleteAll();
        tours.deleteAll();

        DashboardStats stats = statsFrom(mvc.perform(get("/admin")).andExpect(status().isOk()));

        assertThat(stats.totalTours()).isZero();
        assertThat(stats.pendingBookings()).isZero();
        assertThat(stats.bookingsThisMonth()).isZero();
        // coalesce: không có đơn nào vẫn phải ra 0, không được null.
        assertThat(stats.revenueThisMonth()).isEqualByComparingTo("0");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void countsToursExcludingTrash() throws Exception {
        saveTour();
        Tour gone = saveTour();
        gone.setDeletedAt(LocalDateTime.now());
        tours.saveAndFlush(gone);

        // setUp tạo 1 tour, test thêm 2 (1 bị xoá mềm) -> còn 2 tour đang bán.
        assertThat(statsFrom(mvc.perform(get("/admin"))).totalTours()).isEqualTo(2);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void countsPendingBookingsOnly() throws Exception {
        saveBooking(BookingStatus.PENDING, "1000000");
        saveBooking(BookingStatus.PENDING, "1000000");
        saveBooking(BookingStatus.CONFIRMED, "1000000");

        DashboardStats stats = statsFrom(mvc.perform(get("/admin")));

        assertThat(stats.pendingBookings()).isEqualTo(2);
        assertThat(stats.bookingsThisMonth()).isEqualTo(3);
    }

    // Doanh thu bỏ qua đơn đã huỷ: 2.000.000 + 3.000.000, đơn huỷ 9.000.000 không tính.
    @Test
    @WithMockUser(roles = "ADMIN")
    void revenueExcludesCancelledBookings() throws Exception {
        saveBooking(BookingStatus.PENDING, "2000000");
        saveBooking(BookingStatus.CONFIRMED, "3000000");
        saveBooking(BookingStatus.CANCELLED, "9000000");

        assertThat(statsFrom(mvc.perform(get("/admin"))).revenueThisMonth())
            .isEqualByComparingTo("5000000");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void dashboardLinksToFilteredBookingList() throws Exception {
        mvc.perform(get("/admin"))
            .andExpect(content().string(containsString("/admin/bookings?status=PENDING")));
    }

    private DashboardStats statsFrom(ResultActions result) throws Exception {
        Object stats = result.andReturn().getModelAndView().getModel().get("stats");
        assertThat(stats).isInstanceOf(DashboardStats.class);
        return (DashboardStats) stats;
    }

    private Tour saveTour() {
        Category category = categories.findBySlug("beach").orElseGet(() -> {
            Category c = new Category();
            c.setSlug("beach");
            c.setName("Biển đảo");
            return categories.save(c);
        });

        Tour t = new Tour();
        t.setTitle("Tour " + UUID.randomUUID().toString().substring(0, 4));
        t.setSlug(UUID.randomUUID().toString());
        t.setDestination(destination);
        t.setCategory(category);
        t.setDurationDays(3);
        t.setPrice(new BigDecimal("1000000"));
        t.setMaxGuests(20);
        return tours.save(t);
    }

    private void saveBooking(BookingStatus status, String total) {
        User user = new User();
        user.setFullName("Khách");
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPassword("x");
        user.setRole(Role.USER);
        user = users.save(user);

        ContactInfo contact = new ContactInfo();
        contact.setFullName("Khách");
        contact.setEmail("k@example.com");
        contact.setPhone("0900000000");

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setTour(tour);
        booking.setDeparture(departure);
        booking.setCode("TG-" + UUID.randomUUID().toString().substring(0, 8));
        booking.setAdults(2);
        booking.setChildren(0);
        booking.setTotalPrice(new BigDecimal(total));
        booking.setStatus(status);
        booking.setContact(contact);
        bookings.save(booking);
    }
}
