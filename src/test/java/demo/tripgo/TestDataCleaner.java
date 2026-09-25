package demo.tripgo;

import demo.tripgo.repository.BookingRepository;
import demo.tripgo.repository.DepartureRepository;
import demo.tripgo.repository.DestinationRepository;
import demo.tripgo.repository.ReviewRepository;
import demo.tripgo.repository.TourRepository;
import demo.tripgo.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Xoá sạch dữ liệu nghiệp vụ theo ĐÚNG thứ tự khoá ngoại.
//
// Trước đây mỗi lớp test tự viết lại chuỗi deleteAll() của riêng nó, và ba lần đã vỡ vì cùng một
// lý do: một lớp test mới tạo thêm booking/departure, lớp test cũ gọi tours.deleteAll() trước là
// vi phạm ràng buộc. Thứ tự đó chỉ nên tồn tại ở MỘT chỗ.
//
// Là @Component trong source test: @SpringBootTest quét từ package demo.tripgo nên bean này được
// nạp cho mọi context test, còn khi chạy ứng dụng thật thì source test không nằm trên classpath.
//
// Cố ý KHÔNG xoá categories: nhiều test dùng lại loại hình theo slug và tự tạo nếu chưa có, xoá
// đi chỉ làm chậm thêm.
@Component
public class TestDataCleaner {

    private final BookingRepository bookings;
    private final ReviewRepository reviews;
    private final DepartureRepository departures;
    private final TourRepository tours;
    private final DestinationRepository destinations;
    private final UserRepository users;

    public TestDataCleaner(
        BookingRepository bookings,
        ReviewRepository reviews,
        DepartureRepository departures,
        TourRepository tours,
        DestinationRepository destinations,
        UserRepository users
    ) {
        this.bookings = bookings;
        this.reviews = reviews;
        this.departures = departures;
        this.tours = tours;
        this.destinations = destinations;
        this.users = users;
    }

    // Con trỏ tới cha: booking/review/departure -> tour -> destination. User đứng cuối vì
    // booking và review đều tham chiếu tới nó.
    @Transactional
    public void clean() {
        bookings.deleteAll();
        reviews.deleteAll();
        departures.deleteAll();
        tours.deleteAll();
        destinations.deleteAll();
        users.deleteAll();
    }
}
