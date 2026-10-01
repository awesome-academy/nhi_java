package demo.tripgo;

import demo.tripgo.repository.BookingRepository;
import demo.tripgo.repository.CategoryRepository;
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
// Xoá luôn cả categories. Ban đầu mình để lại vì nhiều test dùng find-or-create theo slug, nhưng
// test nào tự seed danh mục lại đụng lỗi trùng slug — một quy tắc "xoá sạch" dễ đoán hơn là một
// quy tắc có ngoại lệ mà mỗi người phải nhớ.
@Component
public class TestDataCleaner {

    private final BookingRepository bookings;
    private final ReviewRepository reviews;
    private final DepartureRepository departures;
    private final TourRepository tours;
    private final DestinationRepository destinations;
    private final UserRepository users;
    private final CategoryRepository categories;

    public TestDataCleaner(
        BookingRepository bookings,
        ReviewRepository reviews,
        DepartureRepository departures,
        TourRepository tours,
        DestinationRepository destinations,
        UserRepository users,
        CategoryRepository categories
    ) {
        this.bookings = bookings;
        this.reviews = reviews;
        this.departures = departures;
        this.tours = tours;
        this.destinations = destinations;
        this.users = users;
        this.categories = categories;
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
        categories.deleteAll();
        users.deleteAll();
    }
}
