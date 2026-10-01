package demo.tripgo.event;

import demo.tripgo.TestDataCleaner;
import demo.tripgo.config.WebSocketConfig;
import demo.tripgo.dto.request.CreateBookingRequest;
import demo.tripgo.dto.request.ContactRequest;
import demo.tripgo.entity.BookingStatus;
import demo.tripgo.entity.Category;
import demo.tripgo.entity.Departure;
import demo.tripgo.entity.Destination;
import demo.tripgo.entity.Role;
import demo.tripgo.entity.Tour;
import demo.tripgo.entity.User;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

// Kiểm việc PHÁT sự kiện và đẩy xuống kênh realtime. Phần bắt tay WebSocket thật là của Spring,
// ở đây thay SimpMessagingTemplate bằng mock để bám vào đúng phần TripGo tự viết.
@SpringBootTest
@ActiveProfiles("test")
class BookingNotificationIntegrationTest {

    @Autowired TestDataCleaner cleaner;
    @Autowired BookingService bookingService;
    @Autowired BookingAdminService bookingAdminService;
    @Autowired TourRepository tours;
    @Autowired DestinationRepository destinations;
    @Autowired CategoryRepository categories;
    @Autowired DepartureRepository departures;
    @Autowired UserRepository users;

    // @MockBean đã bị gỡ ở Spring Boot 4; bản thay thế là @MockitoBean.
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

    @Test
    void publishesEventWhenBookingCreated() {
        bookingService.createBooking(customer, bookingRequest());

        BookingEvent event = captureEvent();
        assertThat(event.kind()).isEqualTo(BookingEvent.Kind.CREATED);
        assertThat(event.tourTitle()).isEqualTo("Đà Nẵng 3N2Đ");
        assertThat(event.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(event.code()).startsWith("TG-");
        assertThat(event.message()).contains("Đơn mới");
    }

    @Test
    void publishesEventWhenAdminConfirms() {
        Long id = bookingService.createBooking(customer, bookingRequest()).id();

        bookingAdminService.confirm(id);

        assertThat(captureLastEvent().kind()).isEqualTo(BookingEvent.Kind.CONFIRMED);
    }

    @Test
    void publishesEventWhenBookingCancelled() {
        Long id = bookingService.createBooking(customer, bookingRequest()).id();

        bookingAdminService.cancel(id);

        BookingEvent event = captureLastEvent();
        assertThat(event.kind()).isEqualTo(BookingEvent.Kind.CANCELLED);
        assertThat(event.status()).isEqualTo(BookingStatus.CANCELLED);
    }

    // Transaction rollback thì KHÔNG được gửi thông báo: nếu không, admin thấy báo một đơn
    // không hề tồn tại.
    @Test
    void sendsNothingWhenTransactionRollsBack() {
        try {
            bookingService.createBooking(customer, new CreateBookingRequest(
                999999L, LocalDate.now().plusDays(30), 2, 0, contact()));
        } catch (RuntimeException expected) {
            // Tour không tồn tại -> rollback.
        }

        verify(messagingTemplate, never()).convertAndSend(eq(WebSocketConfig.TOPIC_BOOKINGS), any(Object.class));
    }

    private BookingEvent captureEvent() {
        ArgumentCaptor<BookingEvent> captor = ArgumentCaptor.forClass(BookingEvent.class);
        verify(messagingTemplate, timeout(2000))
            .convertAndSend(eq(WebSocketConfig.TOPIC_BOOKINGS), captor.capture());
        return captor.getValue();
    }

    private BookingEvent captureLastEvent() {
        ArgumentCaptor<BookingEvent> captor = ArgumentCaptor.forClass(BookingEvent.class);
        verify(messagingTemplate, timeout(2000).atLeastOnce())
            .convertAndSend(eq(WebSocketConfig.TOPIC_BOOKINGS), captor.capture());
        return captor.getAllValues().getLast();
    }

    private CreateBookingRequest bookingRequest() {
        return new CreateBookingRequest(tour.getId(), LocalDate.now().plusDays(30), 2, 0, contact());
    }

    private ContactRequest contact() {
        return new ContactRequest("Khách", "k@example.com", "0900000000", null);
    }
}
