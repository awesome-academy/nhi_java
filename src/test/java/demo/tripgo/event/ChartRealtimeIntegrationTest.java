package demo.tripgo.event;

import demo.tripgo.TestDataCleaner;
import demo.tripgo.config.WebSocketConfig;
import demo.tripgo.dto.request.ContactRequest;
import demo.tripgo.dto.request.CreateBookingRequest;
import demo.tripgo.entity.Category;
import demo.tripgo.entity.Departure;
import demo.tripgo.entity.Destination;
import demo.tripgo.entity.Role;
import demo.tripgo.entity.Tour;
import demo.tripgo.entity.User;
import demo.tripgo.job.BackgroundJob;
import demo.tripgo.job.JobTrigger;
import demo.tripgo.job.MaintenanceJobs;
import demo.tripgo.repository.CategoryRepository;
import demo.tripgo.repository.DepartureRepository;
import demo.tripgo.repository.DestinationRepository;
import demo.tripgo.repository.TourRepository;
import demo.tripgo.repository.UserRepository;
import demo.tripgo.service.BookingAdminService;
import demo.tripgo.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// Biểu đồ realtime: có dữ liệu mới thì server phải gửi tín hiệu tới /topic/chart-updates để
// trình duyệt tải lại /admin/reports/chart-data. Bắt tay WebSocket thật là của Spring; ở đây
// thay SimpMessagingTemplate bằng mock để kiểm đúng phần TripGo tự viết (khi nào gửi, gửi gì).
@SpringBootTest
@ActiveProfiles("test")
class ChartRealtimeIntegrationTest {

    @Autowired TestDataCleaner cleaner;
    @Autowired BookingService bookingService;
    @Autowired BookingAdminService bookingAdminService;
    @Autowired MaintenanceJobs maintenanceJobs;
    @Autowired TourRepository tours;
    @Autowired DestinationRepository destinations;
    @Autowired CategoryRepository categories;
    @Autowired DepartureRepository departures;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean SimpMessagingTemplate messagingTemplate;

    private Tour tour;
    private User customer;

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

        tour = new Tour();
        tour.setTitle("Đà Nẵng 3N2Đ");
        tour.setSlug("da-nang-3n2d");
        tour.setDestination(destination);
        tour.setCategory(category);
        tour.setDurationDays(3);
        tour.setPrice(new BigDecimal("1000000"));
        tour.setMaxGuests(20);
        tour = tours.save(tour);

        Departure departure = new Departure();
        departure.setTour(tour);
        departure.setDepartureDate(LocalDate.now().plusDays(30));
        departure.setTotalSeats(20);
        departure.setBookedSeats(0);
        departures.save(departure);

        customer = new User();
        customer.setFullName("Khách");
        customer.setEmail(UUID.randomUUID() + "@example.com");
        customer.setPassword("x");
        customer.setRole(Role.USER);
        customer = users.save(customer);
    }

    // ---- Đơn thay đổi -> tín hiệu BOOKING ----

    @Test
    void newBookingSignalsChart() {
        bookingService.createBooking(customer, bookingRequest());

        ChartUpdate update = lastUpdate();
        assertThat(update.source()).isEqualTo(ChartUpdate.Source.BOOKING);
        assertThat(update.message()).startsWith("Đơn mới TG-");
        assertThat(update.at()).isNotNull();
    }

    @Test
    void confirmAndCancelAlsoSignalChart() {
        Long first = bookingService.createBooking(customer, bookingRequest()).id();
        Long second = bookingService.createBooking(customer, bookingRequest()).id();

        clearInvocations(messagingTemplate);
        bookingAdminService.confirm(first);
        assertThat(lastUpdate().message()).startsWith("Đã xác nhận đơn");

        clearInvocations(messagingTemplate);
        bookingAdminService.cancel(second);
        assertThat(lastUpdate().message()).startsWith("Đã huỷ đơn");
    }

    // Rollback thì KHÔNG báo: client sẽ tải lại và thấy số liệu y nguyên, hoặc tệ hơn là đọc
    // trúng dữ liệu chưa commit nếu nghe trước commit.
    @Test
    void rolledBackBookingSendsNoSignal() {
        try {
            bookingService.createBooking(customer, new CreateBookingRequest(
                999999L, LocalDate.now().plusDays(30), 2, 0, contact()));
        } catch (RuntimeException expected) {
            // Tour không tồn tại -> rollback.
        }

        verify(messagingTemplate, never())
            .convertAndSend(eq(WebSocketConfig.TOPIC_CHART_UPDATES), any(Object.class));
    }

    // ---- Job xong -> tín hiệu JOB ----

    @Test
    void cancelJobBroadcastsWhenItChangedData() {
        Long id = bookingService.createBooking(customer, bookingRequest()).id();
        jdbc.update("update bookings set created_at = ? where id = ?", LocalDateTime.now().minusHours(80), id);
        clearInvocations(messagingTemplate);

        maintenanceJobs.run(BackgroundJob.CANCEL_EXPIRED_BOOKINGS, JobTrigger.MANUAL);

        List<ChartUpdate> updates = allUpdates();
        assertThat(updates).anySatisfy(update -> {
            assertThat(update.source()).isEqualTo(ChartUpdate.Source.JOB);
            assertThat(update.message()).isEqualTo("Huỷ đơn quá hạn: Tìm thấy 1 đơn quá 72 giờ, đã huỷ 1");
        });
    }

    // Không huỷ đơn nào thì số liệu không đổi: không báo, để mọi client khỏi gọi lại API vô ích.
    @Test
    void cancelJobStaysQuietWhenNothingChanged() {
        bookingService.createBooking(customer, bookingRequest());
        clearInvocations(messagingTemplate);

        maintenanceJobs.run(BackgroundJob.CANCEL_EXPIRED_BOOKINGS, JobTrigger.MANUAL);

        verify(messagingTemplate, never())
            .convertAndSend(eq(WebSocketConfig.TOPIC_CHART_UPDATES), any(Object.class));
    }

    private ChartUpdate lastUpdate() {
        return allUpdates().getLast();
    }

    private List<ChartUpdate> allUpdates() {
        ArgumentCaptor<ChartUpdate> captor = ArgumentCaptor.forClass(ChartUpdate.class);
        verify(messagingTemplate, atLeastOnce())
            .convertAndSend(eq(WebSocketConfig.TOPIC_CHART_UPDATES), captor.capture());
        return captor.getAllValues();
    }

    private CreateBookingRequest bookingRequest() {
        return new CreateBookingRequest(tour.getId(), LocalDate.now().plusDays(30), 2, 0, contact());
    }

    private ContactRequest contact() {
        return new ContactRequest("Khách", "k@example.com", "0900000000", null);
    }
}
