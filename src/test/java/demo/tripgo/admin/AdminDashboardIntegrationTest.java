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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
    @Autowired JdbcTemplate jdbc;
    @Autowired DepartureRepository departures;
    @Autowired UserRepository users;
    @Autowired demo.tripgo.TestDataCleaner cleaner;

    private Destination destination;
    private Tour tour;
    private Departure departure;

    @BeforeEach
    void setUp() {
        cleaner.clean();

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

    // ---- Biểu đồ ----

    // ---- Doanh thu theo ngày ----

    // Đủ mọi ngày của tháng, kể cả ngày không có đơn. Tháng 2/2024 (năm nhuận) có 29 ngày.
    @Test
    @WithMockUser(roles = "ADMIN")
    void dailyRevenueReturnsEveryDayOfMonth() throws Exception {
        mvc.perform(get("/admin/reports/chart-data").param("month", "2024-02"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.month").value("2024-02"))
            .andExpect(jsonPath("$.days.length()").value(29))
            .andExpect(jsonPath("$.days[0].label").value("01/02"))
            .andExpect(jsonPath("$.days[28].label").value("29/02"))
            .andExpect(jsonPath("$.days[28].revenue").value(0))
            .andExpect(jsonPath("$.totalRevenue").value(0));
    }

    // Gộp theo NGÀY ĐẶT ĐƠN, bỏ đơn huỷ, và đơn sát biên tháng không được lọt sang tháng bên cạnh.
    @Test
    @WithMockUser(roles = "ADMIN")
    void dailyRevenueGroupsByBookingDay() throws Exception {
        YearMonth month = YearMonth.now().minusMonths(1);

        placeAt(BookingStatus.CONFIRMED, "2000000", month.atDay(5).atTime(9, 0));
        placeAt(BookingStatus.PENDING, "3000000", month.atDay(5).atTime(18, 30));
        placeAt(BookingStatus.CANCELLED, "9000000", month.atDay(5).atTime(10, 0));
        placeAt(BookingStatus.CONFIRMED, "1000000", month.atEndOfMonth().atTime(23, 59, 59));
        // Tháng trước và tháng sau: không được tính.
        placeAt(BookingStatus.CONFIRMED, "7000000", month.atDay(1).atStartOfDay().minusSeconds(1));
        placeAt(BookingStatus.CONFIRMED, "8000000", month.plusMonths(1).atDay(1).atStartOfDay());

        int lastDay = month.lengthOfMonth() - 1;
        mvc.perform(get("/admin/reports/chart-data").param("month", month.toString()))
            .andExpect(jsonPath("$.days.length()").value(month.lengthOfMonth()))
            .andExpect(jsonPath("$.days[4].revenue").value(5000000))
            .andExpect(jsonPath("$.days[4].bookingCount").value(2))
            .andExpect(jsonPath("$.days[0].revenue").value(0))
            .andExpect(jsonPath("$.days[" + lastDay + "].revenue").value(1000000))
            .andExpect(jsonPath("$.totalRevenue").value(6000000))
            .andExpect(jsonPath("$.totalBookings").value(3));
    }

    // Không chọn tháng tương lai: kể cả khi sửa tay URL, server trả về tháng hiện tại.
    // Tham số sai định dạng hay không có cũng vậy (không được thành lỗi 500).
    @Test
    @WithMockUser(roles = "ADMIN")
    void dailyRevenueFallsBackToCurrentMonth() throws Exception {
        String current = YearMonth.now().toString();

        for (String month : new String[]{YearMonth.now().plusMonths(1).toString(), "thang-9", ""}) {
            mvc.perform(get("/admin/reports/chart-data").param("month", month))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(current))
                .andExpect(jsonPath("$.maxMonth").value(current));
        }
        mvc.perform(get("/admin/reports/chart-data"))
            .andExpect(jsonPath("$.month").value(current));
    }

    // Ô chọn tháng mặc định là tháng này và không cho chọn quá tháng này.
    @Test
    @WithMockUser(roles = "ADMIN")
    void dashboardHasMonthPickerCappedAtCurrentMonth() throws Exception {
        String current = YearMonth.now().toString();
        mvc.perform(get("/admin"))
            .andExpect(content().string(containsString("id=\"dailyMonth\"")))
            .andExpect(content().string(containsString("max=\"" + current + "\"")))
            .andExpect(content().string(containsString("dailyRevenueTable")));
    }

    // API tổng hợp mang đúng tên /chart-data; tên cũ đã bỏ.
    @Test
    @WithMockUser(roles = "ADMIN")
    void oldDailyRevenueUrlIsGone() throws Exception {
        mvc.perform(get("/admin/reports/revenue-daily"))
            .andExpect(status().isNotFound());
    }

    // Trang có nhãn trạng thái realtime và nạp kết nối STOMP dùng chung (admin-realtime.js ở layout).
    @Test
    @WithMockUser(roles = "ADMIN")
    void dashboardIsWiredForRealtimeChart() throws Exception {
        mvc.perform(get("/admin"))
            .andExpect(content().string(containsString("id=\"dailyLive\"")))
            .andExpect(content().string(containsString("id=\"dailyUpdatedAt\"")))
            .andExpect(content().string(containsString("/js/admin-realtime.js")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void normalUserCannotReadDailyRevenue() throws Exception {
        mvc.perform(get("/admin/reports/chart-data"))
            .andExpect(redirectedUrl("/admin/login?denied"));
    }

    // Biểu đồ không được là cách duy nhất đọc số liệu: trang phải có sẵn bảng kèm theo.
    @Test
    @WithMockUser(roles = "ADMIN")
    void dashboardShipsTableViewAlongsideCharts() throws Exception {
        mvc.perform(get("/admin"))
            .andExpect(content().string(containsString("Xem dạng bảng")))
            .andExpect(content().string(containsString("dailyRevenueTable")));
    }

    // Layout chỉ nhúng <main> của trang con; script đặt sau </main> bị bỏ khi render và biểu đồ
    // không bao giờ hiện — không lỗi, không cảnh báo. Test JSON /admin/reports/chart-data không bắt được.
    @Test
    @WithMockUser(roles = "ADMIN")
    void dashboardPageLoadsChartScripts() throws Exception {
        mvc.perform(get("/admin"))
            .andExpect(content().string(containsString("chart.umd.min.js")))
            .andExpect(content().string(containsString("/js/admin-charts.js")));
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

    private Booking saveBooking(BookingStatus status, String total) {
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
        return bookings.save(booking);
    }

    // created_at do @PrePersist gán và updatable = false, nên phải sửa thẳng bằng SQL để dựng đơn
    // đặt vào một ngày cụ thể.
    private void placeAt(BookingStatus status, String total, LocalDateTime createdAt) {
        Booking booking = saveBooking(status, total);
        jdbc.update("update bookings set created_at = ? where id = ?", createdAt, booking.getId());
    }
}
