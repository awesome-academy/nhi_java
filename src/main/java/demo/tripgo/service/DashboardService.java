package demo.tripgo.service;

import demo.tripgo.admin.DashboardCharts;
import demo.tripgo.admin.DashboardStats;
import demo.tripgo.entity.BookingStatus;
import demo.tripgo.repository.BookingRepository;
import demo.tripgo.repository.MonthlyRevenueView;
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

    // Số tour hiển thị trong biểu đồ "tour doanh thu cao nhất". Nhiều hơn thì nhãn tour chen nhau
    // mà cũng không ai đọc tới.
    private static final int TOP_TOURS = 5;
    private static final int TREND_MONTHS = 6;

    @Transactional(readOnly = true)
    public DashboardCharts charts() {
        LocalDate firstOfThisMonth = LocalDate.now().withDayOfMonth(1);

        // Lấy từ đầu tháng thứ 6 tính ngược lại, để trục thời gian luôn đủ 6 mốc kể cả tháng rỗng.
        LocalDateTime trendFrom = firstOfThisMonth.minusMonths(TREND_MONTHS - 1L).atStartOfDay();

        Map<YearMonth, MonthlyRevenueView> byMonth = bookingRepository.revenueByMonthSince(trendFrom)
            .stream()
            .collect(Collectors.toMap(
                view -> YearMonth.of(view.getYr(), view.getMth()),
                view -> view));

        List<DashboardCharts.MonthlyPoint> trend = new ArrayList<>();
        for (int offset = TREND_MONTHS - 1; offset >= 0; offset--) {
            YearMonth month = YearMonth.from(firstOfThisMonth.minusMonths(offset));
            MonthlyRevenueView view = byMonth.get(month);
            // Tháng không có đơn nào vẫn phải xuất hiện với giá trị 0: bỏ hẳn cột sẽ làm trục
            // thời gian nói dối, nhìn như tháng đó không tồn tại.
            trend.add(new DashboardCharts.MonthlyPoint(
                "%02d/%d".formatted(month.getMonthValue(), month.getYear()),
                view == null ? 0L : view.getBookingCount(),
                view == null ? BigDecimal.ZERO : view.getRevenue()));
        }

        List<DashboardCharts.TourRevenue> topTours =
            bookingRepository.revenueByTourSince(firstOfThisMonth.atStartOfDay()).stream()
                .limit(TOP_TOURS)
                .map(view -> new DashboardCharts.TourRevenue(view.getTourTitle(), view.getRevenue()))
                .toList();

        return new DashboardCharts(trend, topTours);
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
