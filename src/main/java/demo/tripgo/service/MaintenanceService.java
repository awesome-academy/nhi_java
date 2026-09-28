package demo.tripgo.service;

import demo.tripgo.entity.Booking;
import demo.tripgo.entity.BookingStatus;
import demo.tripgo.entity.Tour;
import demo.tripgo.repository.BookingRepository;
import demo.tripgo.repository.ReviewRepository;
import demo.tripgo.repository.TourRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// Hai việc dọn dẹp định kỳ. Tách khỏi lớp lập lịch để gọi thẳng được trong test mà không phải
// chờ tới giờ chạy.
@Service
public class MaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceService.class);

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final TourRepository tourRepository;
    private final ReviewRepository reviewRepository;

    public MaintenanceService(
        BookingRepository bookingRepository,
        BookingService bookingService,
        TourRepository tourRepository,
        ReviewRepository reviewRepository
    ) {
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.tourRepository = tourRepository;
        this.reviewRepository = reviewRepository;
    }

    // Đơn chờ xác nhận quá lâu đang giữ chỗ mà không ai đi: huỷ để trả chỗ lại cho khách khác.
    //
    // Huỷ TỪNG đơn qua BookingService.cancelById để dùng đúng phần hoàn chỗ có khoá bi quan.
    // Gom cả mẻ vào một transaction thì một đơn hỏng sẽ kéo đổ cả mẻ.
    public int cancelExpiredPendingBookings(int expireAfterHours) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(expireAfterHours);
        List<Booking> expired =
            bookingRepository.findByStatusAndCreatedAtBefore(BookingStatus.PENDING, cutoff);

        int cancelled = 0;
        for (Booking booking : expired) {
            try {
                bookingService.cancelById(booking.getId());
                cancelled++;
            } catch (RuntimeException exception) {
                // Một đơn hỏng không được làm dừng cả mẻ; ghi log để còn lần ra.
                log.warn("Không huỷ được đơn quá hạn {}: {}",
                    booking.getCode(), exception.getMessage());
            }
        }
        if (cancelled > 0) {
            log.info("Đã tự huỷ {} đơn chờ xác nhận quá {} giờ", cancelled, expireAfterHours);
        }
        return cancelled;
    }

    // rating_avg và review_count được denormalize để lọc/sắp xếp chạy thẳng ở DB. Chúng được cập
    // nhật lúc tạo đánh giá, nên chỉ cần một lần cập nhật thất bại là số liệu trôi. Job này so lại
    // với bảng reviews và sửa những tour lệch.
    @Transactional
    public int refreshStaleRatings() {
        List<Tour> stale = tourRepository.findWithStaleRating();
        for (Tour tour : stale) {
            Double average = reviewRepository.findAverageRatingByTourId(tour.getId());
            tour.setRatingAvg(average == null ? 0.0 : average);
            tour.setReviewCount((int) reviewRepository.countByTourId(tour.getId()));
        }
        if (!stale.isEmpty()) {
            tourRepository.saveAll(stale);
            log.info("Đã tính lại đánh giá cho {} tour bị lệch số liệu", stale.size());
        }
        return stale.size();
    }
}
