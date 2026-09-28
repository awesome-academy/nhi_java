package demo.tripgo.admin;

import java.math.BigDecimal;
import java.util.List;

// Dữ liệu cho hai biểu đồ trên dashboard, trả dạng JSON cho Chart.js vẽ.
//
// Mỗi biểu đồ chỉ có MỘT chuỗi số liệu, nên dùng một màu duy nhất chứ không phải bảng màu phân
// loại — tô mỗi cột một màu khi chúng cùng nghĩa là gán màu cho thứ hạng, không phải cho dữ liệu.
public record DashboardCharts(
    List<MonthlyPoint> revenueByMonth,
    List<TourRevenue> topTours
) {
    public record MonthlyPoint(String label, long bookingCount, BigDecimal revenue) {
    }

    public record TourRevenue(String tourTitle, BigDecimal revenue) {
    }
}
