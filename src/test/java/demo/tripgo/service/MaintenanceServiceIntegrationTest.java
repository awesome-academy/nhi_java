package demo.tripgo.service;

import demo.tripgo.TestDataCleaner;
import demo.tripgo.entity.Booking;
import demo.tripgo.entity.BookingStatus;
import demo.tripgo.entity.Category;
import demo.tripgo.entity.ContactInfo;
import demo.tripgo.entity.Departure;
import demo.tripgo.entity.Destination;
import demo.tripgo.entity.Review;
import demo.tripgo.entity.Role;
import demo.tripgo.entity.Tour;
import demo.tripgo.entity.User;
import demo.tripgo.repository.BookingRepository;
import demo.tripgo.repository.CategoryRepository;
import demo.tripgo.repository.DepartureRepository;
import demo.tripgo.repository.DestinationRepository;
import demo.tripgo.repository.ReviewRepository;
import demo.tripgo.repository.TourRepository;
import demo.tripgo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MaintenanceServiceIntegrationTest {

    @Autowired TestDataCleaner cleaner;
    @Autowired MaintenanceService maintenance;
    @Autowired BookingRepository bookings;
    @Autowired DepartureRepository departures;
    @Autowired TourRepository tours;
    @Autowired DestinationRepository destinations;
    @Autowired CategoryRepository categories;
    @Autowired ReviewRepository reviews;
    @Autowired UserRepository users;

    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    private Tour tour;
    private Departure departure;

    @BeforeEach
    void setUp() {
        cleaner.clean();

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
        tour.setPrice(new BigDecimal("1000000"));
        tour.setMaxGuests(20);
        tour = tours.save(tour);

        departure = new Departure();
        departure.setTour(tour);
        departure.setDepartureDate(LocalDate.now().plusDays(30));
        departure.setTotalSeats(20);
        departure.setBookedSeats(0);
        departure = departures.save(departure);
    }

    // ---- Tự huỷ đơn quá hạn ----

    @Test
    void cancelsOnlyPendingBookingsOlderThanCutoff() {
        Booking old = saveBooking(BookingStatus.PENDING, 3, LocalDateTime.now().minusHours(80));
        Booking fresh = saveBooking(BookingStatus.PENDING, 2, LocalDateTime.now().minusHours(10));
        Booking confirmed = saveBooking(BookingStatus.CONFIRMED, 4, LocalDateTime.now().minusHours(90));
        departure.setBookedSeats(9);
        departures.saveAndFlush(departure);

        assertThat(maintenance.cancelExpiredPendingBookings(72).cancelled()).isEqualTo(1);

        assertThat(status(old)).isEqualTo(BookingStatus.CANCELLED);
        // Đơn mới và đơn đã xác nhận không được đụng tới.
        assertThat(status(fresh)).isEqualTo(BookingStatus.PENDING);
        assertThat(status(confirmed)).isEqualTo(BookingStatus.CONFIRMED);

        // Phải hoàn đúng 3 chỗ của đơn bị huỷ, không nhiều hơn.
        assertThat(departures.findById(departure.getId()).orElseThrow().getBookedSeats())
            .isEqualTo(6);
    }

    @Test
    void doesNothingWhenNoBookingExpired() {
        saveBooking(BookingStatus.PENDING, 2, LocalDateTime.now().minusHours(1));

        assertThat(maintenance.cancelExpiredPendingBookings(72).cancelled()).isZero();
    }

    // Chạy lại lần hai không được trừ chỗ thêm lần nữa.
    @Test
    void runningTwiceIsIdempotent() {
        saveBooking(BookingStatus.PENDING, 3, LocalDateTime.now().minusHours(80));
        departure.setBookedSeats(3);
        departures.saveAndFlush(departure);

        assertThat(maintenance.cancelExpiredPendingBookings(72).cancelled()).isEqualTo(1);
        assertThat(maintenance.cancelExpiredPendingBookings(72).cancelled()).isZero();
        assertThat(departures.findById(departure.getId()).orElseThrow().getBookedSeats()).isZero();
    }

    // ---- Tính lại đánh giá ----

    @Test
    void recomputesRatingForToursThatDrifted() {
        saveReview(5);
        saveReview(3);
        // Giả lập số liệu đã trôi: ghi đè thẳng hai cột denormalized.
        tour.setRatingAvg(1.0);
        tour.setReviewCount(99);
        tours.saveAndFlush(tour);

        assertThat(maintenance.refreshStaleRatings()).isEqualTo(1);

        Tour fixed = tours.findById(tour.getId()).orElseThrow();
        assertThat(fixed.getRatingAvg()).isEqualTo(4.0);
        assertThat(fixed.getReviewCount()).isEqualTo(2);
    }

    // Tour không có đánh giá nào mà lại mang số liệu cũ -> phải đưa về 0.
    @Test
    void resetsRatingWhenAllReviewsAreGone() {
        tour.setRatingAvg(4.5);
        tour.setReviewCount(7);
        tours.saveAndFlush(tour);

        assertThat(maintenance.refreshStaleRatings()).isEqualTo(1);

        Tour fixed = tours.findById(tour.getId()).orElseThrow();
        assertThat(fixed.getRatingAvg()).isZero();
        assertThat(fixed.getReviewCount()).isZero();
    }

    // Số liệu đã đúng thì job không được đụng vào gì cả.
    @Test
    void leavesConsistentToursAlone() {
        saveReview(4);
        tour.setRatingAvg(4.0);
        tour.setReviewCount(1);
        tours.saveAndFlush(tour);

        assertThat(maintenance.refreshStaleRatings()).isZero();
    }

    // ---- Tiện ích ----

    private BookingStatus status(Booking booking) {
        return bookings.findById(booking.getId()).orElseThrow().getStatus();
    }

    private User saveUser() {
        User user = new User();
        user.setFullName("Khách");
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPassword("x");
        user.setRole(Role.USER);
        return users.save(user);
    }

    private void saveReview(int rating) {
        Review review = new Review();
        review.setTour(tour);
        review.setUser(saveUser());
        review.setRating(rating);
        review.setComment("ok");
        reviews.save(review);
    }

    private Booking saveBooking(BookingStatus status, int adults, LocalDateTime createdAt) {
        ContactInfo contact = new ContactInfo();
        contact.setFullName("Khách");
        contact.setEmail("k@example.com");
        contact.setPhone("0900000000");

        Booking booking = new Booking();
        booking.setUser(saveUser());
        booking.setTour(tour);
        booking.setDeparture(departure);
        booking.setCode("TG-" + UUID.randomUUID().toString().substring(0, 8));
        booking.setAdults(adults);
        booking.setChildren(0);
        booking.setTotalPrice(new BigDecimal("1000000"));
        booking.setStatus(status);
        booking.setContact(contact);
        booking = bookings.saveAndFlush(booking);

        // created_at do @PrePersist gán và khai updatable = false, nên sửa qua entity không ăn
        // thua — phải cập nhật thẳng bằng SQL để giả lập đơn cũ mà không phải chờ thật.
        jdbc.update("update bookings set created_at = ? where id = ?", createdAt, booking.getId());
        return booking;
    }
}
