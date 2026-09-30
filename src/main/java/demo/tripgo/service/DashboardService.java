package demo.tripgo.service;

import demo.tripgo.admin.DailyRevenue;
import demo.tripgo.admin.DashboardStats;
import demo.tripgo.entity.BookingStatus;
import demo.tripgo.repository.BookingRepository;
import demo.tripgo.repository.DailyRevenueView;
import demo.tripgo.repository.TourRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final TourRepository tourRepository;
    private final BookingRepository bookingRepository;

    public DashboardService(TourRepository tourRepository, BookingRepository bookingRepository) {
        this.tourRepository = tourRepository;
        this.bookingRepository = bookingRepository;
    }

    // requested null hoặc ở tương lai -> tháng hiện tại. Chặn cả ở đây chứ không chỉ ở ô chọn
    // tháng trên giao diện: URL thì ai cũng sửa tay được.
    @Transactional(readOnly = true)
    public DailyRevenue dailyRevenue(YearMonth requested) {
        YearMonth current = YearMonth.now();
        YearMonth month = requested == null || requested.isAfter(current) ? current : requested;

        Map<Integer, DailyRevenueView> byDay = bookingRepository.revenueByDayBetween(
                month.atDay(1).atStartOfDay(),
                month.plusMonths(1).atDay(1).atStartOfDay())
            .stream()
            .collect(Collectors.toMap(DailyRevenueView::getDy, view -> view));

        List<DailyRevenue.DailyPoint> days = new ArrayList<>();
        long totalBookings = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        // Đủ mọi ngày của tháng (28/29/30/31), ngày không có đơn mang giá trị 0 — cùng lý do với
        // biểu đồ 6 tháng: bỏ cột đi thì trục thời gian nói dối.
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            DailyRevenueView view = byDay.get(day);
            long count = view == null ? 0L : view.getBookingCount();
            BigDecimal revenue = view == null ? BigDecimal.ZERO : view.getRevenue();
            days.add(new DailyRevenue.DailyPoint(
                day, "%02d/%02d".formatted(day, month.getMonthValue()), count, revenue));
            totalBookings += count;
            totalRevenue = totalRevenue.add(revenue);
        }

        return new DailyRevenue(month.toString(), current.toString(), totalBookings, totalRevenue, days);
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
