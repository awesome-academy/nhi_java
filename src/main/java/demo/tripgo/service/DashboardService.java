package demo.tripgo.service;

import demo.tripgo.admin.DashboardStats;
import demo.tripgo.entity.BookingStatus;
import demo.tripgo.repository.BookingRepository;
import demo.tripgo.repository.TourRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class DashboardService {

    private final TourRepository tourRepository;
    private final BookingRepository bookingRepository;

    public DashboardService(TourRepository tourRepository, BookingRepository bookingRepository) {
        this.tourRepository = tourRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional(readOnly = true)
    public DashboardStats stats() {
        // "Tháng này" tính từ 00:00 ngày 1 của tháng hiện tại, không phải 30 ngày gần nhất —
        // đó là cách người đọc báo cáo hiểu cụm từ này.
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        return new DashboardStats(
            tourRepository.countByDeletedAtIsNull(),
            bookingRepository.countByStatus(BookingStatus.PENDING),
            bookingRepository.countByCreatedAtGreaterThanEqual(startOfMonth),
            bookingRepository.sumRevenueSince(startOfMonth));
    }
}
