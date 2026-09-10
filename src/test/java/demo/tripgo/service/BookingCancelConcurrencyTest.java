package demo.tripgo.service;

import demo.tripgo.entity.Booking;
import demo.tripgo.entity.BookingStatus;
import demo.tripgo.entity.ContactInfo;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class BookingCancelConcurrencyTest {

    @Autowired BookingService bookingService;
    @Autowired BookingRepository bookings;
    @Autowired DepartureRepository departures;
    @Autowired TourRepository tours;
    @Autowired DestinationRepository destinations;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    // Huỷ đồng thời 2 đơn trên cùng departure không được để lost update khi hoàn chỗ.
    @Test
    void concurrentCancelsRestoreSeatsWithoutLostUpdate() throws Exception {
        for (int iteration = 0; iteration < 10; iteration++) {
            int finalSeats = runOneConcurrentCancel();
            assertThat(finalSeats)
                .as("lần %d: bookedSeats sau khi huỷ đồng thời 2 đơn (2+3 chỗ) từ mức 5", iteration)
                .isZero();
        }
    }

    private int runOneConcurrentCancel() throws Exception {
        bookings.deleteAll();
        departures.deleteAll();
        tours.deleteAll();
        destinations.deleteAll();
        users.deleteAll();

        Destination d = new Destination();
        d.setName("Da Nang");
        d.setSlug("da-nang");
        d = destinations.save(d);

        Tour tour = new Tour();
        tour.setTitle("Tour");
        tour.setDestination(d);
        tour.setCategory(TourCategory.BEACH);
        tour.setDurationDays(3);
        tour.setPrice(new BigDecimal("100"));
        tour.setMaxGuests(50);
        tour = tours.save(tour);

        Departure departure = new Departure();
        departure.setTour(tour);
        departure.setDepartureDate(LocalDate.now().plusDays(10));
        departure.setTotalSeats(10);
        departure.setBookedSeats(5); // 2 đơn đang giữ 2 + 3 chỗ
        departure = departures.save(departure);
        Long departureId = departure.getId();

        User u1 = saveUser();
        User u2 = saveUser();
        Long b1 = saveBooking(u1, tour, departure, 2, 0);
        Long b2 = saveBooking(u2, tour, departure, 3, 0);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> f1 = pool.submit(() -> {
                start.await();
                bookingService.cancelBooking(u1, b1);
                return null;
            });
            Future<?> f2 = pool.submit(() -> {
                start.await();
                bookingService.cancelBooking(u2, b2);
                return null;
            });
            start.countDown();
            f1.get();
            f2.get();
        } finally {
            pool.shutdownNow();
        }

        return departures.findById(departureId).orElseThrow().getBookedSeats();
    }

    private User saveUser() {
        User user = new User();
        user.setFullName("Booker");
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPassword(encoder.encode("password123"));
        user.setRole(Role.USER);
        return users.save(user);
    }

    private Long saveBooking(User user, Tour tour, Departure departure, int adults, int children) {
        Booking booking = new Booking();
        booking.setCode("TG-" + UUID.randomUUID().toString().substring(0, 12));
        booking.setUser(user);
        booking.setTour(tour);
        booking.setDeparture(departure);
        booking.setAdults(adults);
        booking.setChildren(children);
        booking.setTotalPrice(new BigDecimal("100"));
        booking.setStatus(BookingStatus.CONFIRMED);
        ContactInfo contact = new ContactInfo();
        contact.setFullName("Booker");
        contact.setEmail("b@example.com");
        contact.setPhone("0900000000");
        booking.setContact(contact);
        return bookings.save(booking).getId();
    }
}
