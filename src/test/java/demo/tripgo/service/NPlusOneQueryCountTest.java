package demo.tripgo.service;

import demo.tripgo.dto.request.TourListRequest;
import demo.tripgo.entity.Booking;
import demo.tripgo.entity.BookingStatus;
import demo.tripgo.entity.ContactInfo;
import demo.tripgo.entity.Departure;
import demo.tripgo.entity.Destination;
import demo.tripgo.entity.Role;
import demo.tripgo.entity.Tour;
import demo.tripgo.entity.TourCategory;
import demo.tripgo.entity.TourImage;
import demo.tripgo.entity.User;
import demo.tripgo.repository.BookingRepository;
import demo.tripgo.repository.DepartureRepository;
import demo.tripgo.repository.DestinationRepository;
import demo.tripgo.repository.TourRepository;
import demo.tripgo.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Khoá lại số câu SQL của các endpoint danh sách. N+1 là lỗi KHÔNG nhìn thấy trong response:
// API vẫn trả đúng dữ liệu, chỉ là chạy 1 + N truy vấn thay vì 2. Test này biến nó thành lỗi
// nhìn thấy được — thêm tour/đơn vào mà số truy vấn tăng theo là fail ngay.
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
class NPlusOneQueryCountTest {

    @Autowired TourService tourService;
    @Autowired BookingService bookingService;
    @Autowired TourRepository tours;
    @Autowired DestinationRepository destinations;
    @Autowired DepartureRepository departures;
    @Autowired UserRepository users;
    @Autowired BookingRepository bookings;
    @Autowired ReviewService reviewService;
    @Autowired demo.tripgo.repository.ReviewRepository reviews;
    @Autowired EntityManagerFactory emf;

    private User user;
    private Tour detailTour;

    private Statistics stats() {
        return emf.unwrap(SessionFactory.class).getStatistics();
    }

    @BeforeEach
    void setUp() {
        reviews.deleteAll();
        bookings.deleteAll();
        departures.deleteAll();
        tours.deleteAll();
        destinations.deleteAll();
        users.deleteAll();

        // 3 điểm đến khác nhau -> nếu destination không được fetch, mỗi tour sẽ sinh thêm 1 truy vấn.
        Destination[] ds = new Destination[3];
        for (int i = 0; i < 3; i++) {
            Destination d = new Destination();
            d.setName("Điểm đến " + i);
            d.setSlug("diem-den-" + i);
            ds[i] = destinations.save(d);
        }

        user = new User();
        user.setFullName("Người đặt");
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPassword("x");
        user.setRole(Role.USER);
        user = users.save(user);

        // 10 tour, mỗi tour một điểm đến + ảnh riêng; 10 đơn của cùng user.
        for (int i = 0; i < 10; i++) {
            Tour tour = new Tour();
            tour.setTitle("Tour " + i);
            tour.setDestination(ds[i % 3]);
            tour.setCategory(TourCategory.BEACH);
            tour.setDurationDays(3);
            tour.setPrice(new BigDecimal("1000000"));
            tour.setMaxGuests(20);
            tour.setDescription("mô tả " + i);
            TourImage image = new TourImage();
            image.setUrl("https://img/" + i + ".jpg");
            image.setPosition(0);
            tour.addImage(image);
            tour = tours.save(tour);

            Departure departure = new Departure();
            departure.setTour(tour);
            departure.setDepartureDate(LocalDate.now().plusDays(10 + i));
            departure.setTotalSeats(20);
            departure.setBookedSeats(0);
            departure = departures.save(departure);

            ContactInfo contact = new ContactInfo();
            contact.setFullName("Nguyen Van A");
            contact.setEmail("a@example.com");
            contact.setPhone("0912345678");

            Booking booking = new Booking();
            booking.setUser(user);
            booking.setTour(tour);
            booking.setDeparture(departure);
            booking.setAdults(1);
            booking.setChildren(0);
            booking.setTotalPrice(new BigDecimal("1000000"));
            booking.setStatus(BookingStatus.PENDING);
            booking.setContact(contact);
            bookings.save(booking);
            if (i == 0) {
                detailTour = tour;
            }
        }

        // Màn chi tiết đọc cả 3 @ElementCollection -> phải có dữ liệu thì mới đo đúng.
        detailTour.getHighlights().add("Điểm nổi bật");
        detailTour.getIncluded().add("Khách sạn 2 đêm");
        detailTour.getExcluded().add("Vé máy bay");
        detailTour.setSlug("tour-chi-tiet-" + detailTour.getId());
        detailTour = tours.save(detailTour);

        // 12 người khác nhau đánh giá cùng 1 tour: nếu thiếu @EntityGraph("user"),
        // mapper đọc user.getFullName() sẽ sinh thêm 12 truy vấn.
        for (int i = 0; i < 12; i++) {
            User reviewer = new User();
            reviewer.setFullName("Người đánh giá " + i);
            reviewer.setEmail(UUID.randomUUID() + "@example.com");
            reviewer.setPassword("x");
            reviewer.setRole(Role.USER);
            reviewer = users.save(reviewer);

            demo.tripgo.entity.Review review = new demo.tripgo.entity.Review();
            review.setTour(detailTour);
            review.setUser(reviewer);
            review.setRating(1 + (i % 5));
            review.setComment("nhận xét " + i);
            reviews.save(review);
        }
    }

    // GET /tours: 1 câu đếm tổng + 1 câu lấy dữ liệu (đã fetch destination). KHÔNG phụ thuộc số tour.
    @Test
    void tourListRunsExactlyTwoQueriesRegardlessOfRowCount() {
        stats().clear();

        var page = tourService.listTours(new TourListRequest(
            null, null, null, null, null, null, null, null, 1, 10));

        assertThat(page.data()).hasSize(10);
        // Mapper có đọc destination.getName() của từng tour -> nếu thiếu fetch, đây sẽ là 12.
        assertThat(page.data().get(0).destination()).isNotBlank();
        assertThat(stats().getPrepareStatementCount())
            .as("GET /tours phải luôn là 2 truy vấn (count + data), bất kể có bao nhiêu tour")
            .isEqualTo(2);
    }

    // GET /tours/{slug}: số truy vấn CỐ ĐỊNH, không phụ thuộc tour có bao nhiêu ảnh / ngày /
    // mục lịch trình. 7 câu = 1 tra id từ slug + 1 fetch (điểm đến + ảnh) + 1 fetch lịch trình
    // + 3 @ElementCollection (highlights, included, excluded) + 1 lấy ngày khởi hành.
    // Không thể gộp các collection vào một câu: join nhiều bag cùng lúc -> MultipleBagFetchException.
    @Test
    void tourDetailBySlugRunsFixedNumberOfQueries() {
        stats().clear();

        var detail = tourService.getTourDetail(detailTour.getSlug());

        // Chạm vào mọi collection để chắc chắn không còn lazy load nào bị bỏ sót.
        assertThat(detail.destination().name()).isNotBlank();
        assertThat(detail.images()).isNotEmpty();
        assertThat(detail.highlights()).isNotEmpty();
        assertThat(detail.included()).isNotEmpty();
        assertThat(detail.excluded()).isNotEmpty();
        assertThat(detail.startDates()).isNotEmpty();

        assertThat(stats().getPrepareStatementCount())
            .as("GET /tours/{slug} phải cố định 7 truy vấn, không tăng theo số ảnh/ngày/lịch trình")
            .isEqualTo(7);
    }

    // Tra bằng id dạng số thì bớt đúng 1 truy vấn (không cần bước tra id từ slug).
    @Test
    void tourDetailByNumericIdSkipsSlugLookup() {
        stats().clear();

        tourService.getTourDetail(String.valueOf(detailTour.getId()));

        assertThat(stats().getPrepareStatementCount())
            .as("Tra theo id bỏ được bước tìm id từ slug -> ít hơn đúng 1 truy vấn")
            .isEqualTo(6);
    }

    // GET /tours/{id}/reviews: 1 nạp tour (lấy rating_avg) + 1 đếm + 1 lấy dữ liệu.
    // Tour có 12 đánh giá của 12 người khác nhau; thiếu @EntityGraph("user") thì mapper đọc
    // user.getFullName() sẽ thành 3 + 10 truy vấn.
    @Test
    void reviewListRunsExactlyThreeQueriesRegardlessOfRowCount() {
        stats().clear();

        var page = reviewService.getReviews(detailTour.getId(), 1, 10);

        assertThat(page.data()).hasSize(10);
        assertThat(page.total()).isEqualTo(12);
        assertThat(page.data().get(0).user().name()).isNotBlank();
        assertThat(stats().getPrepareStatementCount())
            .as("GET /reviews phải luôn là 3 truy vấn nhờ @EntityGraph(\"user\"), dù có bao nhiêu đánh giá")
            .isEqualTo(3);
    }

    // GET /bookings: @EntityGraph nạp sẵn tour + departure, mapper đọc cả hai.
    @Test
    void bookingListRunsExactlyTwoQueriesRegardlessOfRowCount() {
        stats().clear();

        var page = bookingService.getMyBookings(user, 1, 10);

        assertThat(page.data()).hasSize(10);
        assertThat(page.data().get(0).tour().title()).isNotBlank();
        assertThat(page.data().get(0).date()).isNotNull();
        assertThat(stats().getPrepareStatementCount())
            .as("GET /bookings phải luôn là 2 truy vấn (count + data) nhờ @EntityGraph")
            .isEqualTo(2);
    }
}
