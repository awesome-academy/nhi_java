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
import demo.tripgo.job.BackgroundJob;
import demo.tripgo.job.JobRunState;
import demo.tripgo.job.JobRunTracker;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

// Màn hình "Job nền". Profile test tắt lịch tự động (tasks.maintenance.enabled=false), nên ở đây
// job chỉ chạy khi bấm "Chạy ngay" — đúng thứ cần kiểm, và không có lần chạy nào xen vào test.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminJobIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JobRunTracker tracker;
    @Autowired BookingRepository bookings;
    @Autowired DepartureRepository departures;
    @Autowired TourRepository tours;
    @Autowired DestinationRepository destinations;
    @Autowired CategoryRepository categories;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired demo.tripgo.TestDataCleaner cleaner;

    private Departure departure;

    @BeforeEach
    void setUp() {
        cleaner.clean();

        Destination destination = new Destination();
        destination.setName("Đà Nẵng");
        destination.setSlug("da-nang");
        destination = destinations.save(destination);

        Category category = new Category();
        category.setSlug("beach");
        category.setName("Biển đảo");
        category = categories.save(category);

        Tour tour = new Tour();
        tour.setTitle("Đà Nẵng 3N2Đ");
        tour.setSlug("da-nang-3n2d");
        tour.setDestination(destination);
        tour.setCategory(category);
        tour.setDurationDays(3);
        tour.setPrice(new BigDecimal("1000000"));
        tour.setMaxGuests(20);
        tour = tours.save(tour);

        departure = new Departure();
        departure.setTour(tour);
        departure.setDepartureDate(LocalDate.now().plusDays(30));
        departure.setTotalSeats(20);
        departure.setBookedSeats(2);
        departure = departures.save(departure);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void pageListsBothJobsAndSaysScheduleIsOff() throws Exception {
        mvc.perform(get("/admin/jobs"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/jobs/list"))
            .andExpect(model().attribute("jobs", hasSize(2)))
            .andExpect(content().string(containsString("Huỷ đơn quá hạn")))
            .andExpect(content().string(containsString("Tính lại điểm đánh giá")))
            .andExpect(content().string(containsString("Lịch chạy tự động đang tắt")))
            .andExpect(content().string(containsString("/admin/jobs/cancel-expired-bookings/run")));
    }

    // Bấm "Chạy ngay": job chạy trên luồng nền (không phải luồng request), huỷ đơn quá hạn,
    // và màn hình hiện kết quả + tên luồng.
    @Test
    @WithMockUser(roles = "ADMIN")
    void runNowCancelsExpiredBookingOnBackgroundThread() throws Exception {
        saveExpiredPendingBooking();
        long before = tracker.state(BackgroundJob.CANCEL_EXPIRED_BOOKINGS).runCount();

        mvc.perform(post("/admin/jobs/cancel-expired-bookings/run").with(csrf()))
            .andExpect(redirectedUrl("/admin/jobs"))
            .andExpect(flash().attribute("flashType", "success"));

        JobRunState state = awaitRun(BackgroundJob.CANCEL_EXPIRED_BOOKINGS, before);
        assertThat(state.status()).isEqualTo(JobRunState.Status.SUCCESS);
        assertThat(state.summary()).isEqualTo("Tìm thấy 1 đơn quá 72 giờ, đã huỷ 1");
        assertThat(state.threadName()).startsWith("tripgo-task-");
        assertThat(bookings.findAll()).allMatch(b -> b.getStatus() == BookingStatus.CANCELLED);

        mvc.perform(get("/admin/jobs"))
            .andExpect(content().string(containsString("Tìm thấy 1 đơn quá 72 giờ, đã huỷ 1")))
            .andExpect(content().string(containsString("Chạy tay")))
            .andExpect(content().string(containsString("tripgo-task-")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unknownJobIsNotFound() throws Exception {
        mvc.perform(post("/admin/jobs/xoa-het-du-lieu/run").with(csrf()))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void runNowWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/admin/jobs/cancel-expired-bookings/run"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void normalUserCannotSeeJobs() throws Exception {
        mvc.perform(get("/admin/jobs"))
            .andExpect(redirectedUrl("/admin/login?denied"));
    }

    // Job chạy bất đồng bộ: chờ tới khi số lần chạy tăng và trạng thái không còn RUNNING.
    private JobRunState awaitRun(BackgroundJob job, long runCountBefore) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            JobRunState state = tracker.state(job);
            if (state.runCount() > runCountBefore && !state.isRunning()) {
                return state;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Job " + job + " không chạy xong trong 5 giây");
    }

    private void saveExpiredPendingBooking() {
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
        booking.setTour(departure.getTour());
        booking.setDeparture(departure);
        booking.setCode("TG-" + UUID.randomUUID().toString().substring(0, 8));
        booking.setAdults(2);
        booking.setChildren(0);
        booking.setTotalPrice(new BigDecimal("2000000"));
        booking.setStatus(BookingStatus.PENDING);
        booking.setContact(contact);
        booking = bookings.saveAndFlush(booking);

        jdbc.update("update bookings set created_at = ? where id = ?",
            LocalDateTime.now().minusHours(80), booking.getId());
    }
}
