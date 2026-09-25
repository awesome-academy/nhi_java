package demo.tripgo.repository;

import demo.tripgo.PostgresTestBase;
import demo.tripgo.entity.Booking;
import demo.tripgo.entity.BookingStatus;
import demo.tripgo.entity.Category;
import demo.tripgo.entity.ContactInfo;
import demo.tripgo.entity.Departure;
import demo.tripgo.entity.Destination;
import demo.tripgo.entity.Role;
import demo.tripgo.entity.Tour;
import demo.tripgo.entity.User;
import demo.tripgo.service.DestinationAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Chạy đúng những truy vấn viết tay trên Postgres thật. Mục đích không phải kiểm lại nghiệp vụ
// (các test H2 đã làm) mà là bắt lỗi phương ngữ SQL — thứ H2 không bao giờ phát hiện.
@SpringBootTest
class PostgresQueryDialectTest extends PostgresTestBase {

    @Autowired DestinationRepository destinations;
    @Autowired TourRepository tours;
    @Autowired BookingRepository bookings;
    @Autowired DepartureRepository departures;
    @Autowired CategoryRepository categories;
    @Autowired UserRepository users;
    @Autowired DestinationAdminService destinationAdminService;

    private Destination destination;
    private Tour tour;

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
    }

    // Đây chính là truy vấn từng ném "function lower(bytea) does not exist" trên Postgres
    // trong khi H2 chạy bình thường.
    @Test
    void destinationListQueryRunsOnPostgresWithoutKeyword() {
        assertThat(destinationAdminService.list(null, 1, 20).getTotalElements()).isEqualTo(1);
        assertThat(destinationAdminService.list("  ", 1, 20).getTotalElements()).isEqualTo(1);
    }

    @Test
    void destinationListQueryFiltersByKeyword() {
        Destination sapa = new Destination();
        sapa.setName("Sapa");
        sapa.setSlug("sapa");
        destinations.save(sapa);

        assertThat(destinationAdminService.list("sapa", 1, 20).getTotalElements()).isEqualTo(1);
        assertThat(destinationAdminService.list("khong-co", 1, 20).getTotalElements()).isZero();
    }

    // '%' và '_' phải là ký tự thường, không phải wildcard: không tên nào chứa chúng -> 0 kết quả.
    @Test
    void likeWildcardsAreTreatedAsLiterals() {
        assertThat(destinationAdminService.list("%", 1, 20).getTotalElements()).isZero();
        assertThat(destinationAdminService.list("_", 1, 20).getTotalElements()).isZero();
    }

    // Tìm không dấu: gõ "da nang" phải ra "Đà Nẵng".
    @Test
    void searchIgnoresDiacritics() {
        assertThat(destinationAdminService.list("da nang", 1, 20).getTotalElements()).isEqualTo(1);
        assertThat(destinationAdminService.list("Đà Nẵng", 1, 20).getTotalElements()).isEqualTo(1);
        assertThat(destinationAdminService.list("DA NANG", 1, 20).getTotalElements()).isEqualTo(1);
        assertThat(destinationAdminService.list("nang", 1, 20).getTotalElements()).isEqualTo(1);
        assertThat(destinationAdminService.list("sapa", 1, 20).getTotalElements()).isZero();
    }

    @Test
    void destinationTourCountUsesLeftJoinCondition() {
        saveTour();
        Tour deleted = saveTour();
        deleted.setDeletedAt(LocalDateTime.now());
        tours.saveAndFlush(deleted);

        Destination empty = new Destination();
        empty.setName("Sapa");
        empty.setSlug("sapa");
        destinations.save(empty);

        var rows = destinationAdminService.list(null, 1, 20).getContent();
        assertThat(rows).hasSize(2);
        assertThat(rows).filteredOn(r -> r.slug().equals("da-nang"))
            .singleElement().extracting("tourCount").isEqualTo(2L);
        // Điểm đến chưa có tour vẫn phải xuất hiện.
        assertThat(rows).filteredOn(r -> r.slug().equals("sapa"))
            .singleElement().extracting("tourCount").isEqualTo(0L);
    }

    // coalesce + so sánh enum bằng tên đầy đủ trong HQL.
    @Test
    void revenueSumWorksAndIgnoresCancelled() {
        LocalDateTime from = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        assertThat(bookings.sumRevenueSince(from)).isEqualByComparingTo("0");

        saveBooking(BookingStatus.CONFIRMED, "2000000");
        saveBooking(BookingStatus.CANCELLED, "9000000");

        assertThat(bookings.sumRevenueSince(from)).isEqualByComparingTo("2000000");
    }

    // Các truy vấn xoá mềm viết tay: chạy được và lọc đúng trên Postgres.
    @Test
    void softDeleteQueriesRunOnPostgres() {
        assertThat(tours.findActiveById(tour.getId())).isPresent();
        assertThat(tours.findDetailById(tour.getId())).isPresent();
        assertThat(tours.fetchItinerary(tour.getId())).isPresent();
        assertThat(tours.findIdBySlug(tour.getSlug())).isPresent();

        tour.setDeletedAt(LocalDateTime.now());
        tours.saveAndFlush(tour);

        assertThat(tours.findActiveById(tour.getId())).isEmpty();
        assertThat(tours.findDetailById(tour.getId())).isEmpty();
        assertThat(tours.findIdBySlug(tour.getSlug())).isEmpty();
        assertThat(tours.findDeleted(PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
        assertThat(tours.countByDeletedAtIsNull()).isZero();
    }

    @Test
    void adminBookingQueriesRunOnPostgres() {
        saveBooking(BookingStatus.PENDING, "1000000");

        assertThat(bookings.findAllBy(PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
        assertThat(bookings.findByStatus(BookingStatus.PENDING, PageRequest.of(0, 10))
            .getTotalElements()).isEqualTo(1);
        assertThat(bookings.countByStatus(BookingStatus.PENDING)).isEqualTo(1);
    }

    @Test
    void publicDestinationListRunsOnPostgres() {
        assertThat(destinations.findAllWithTourCount()).hasSize(1);
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

        Departure departure = new Departure();
        departure.setTour(tour);
        departure.setDepartureDate(LocalDate.now().plusDays(30));
        departure.setTotalSeats(20);
        departure.setBookedSeats(2);
        departure = departures.save(departure);

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
