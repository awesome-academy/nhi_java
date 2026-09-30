package demo.tripgo.admin;

import java.math.BigDecimal;
import java.util.List;

// Biểu đồ "Doanh thu theo ngày" của một tháng, tính theo NGÀY ĐẶT ĐƠN (cùng cách tính với các
// số liệu khác trên dashboard, để các con số khớp nhau).
//
// month là tháng THỰC SỰ được trả về (yyyy-MM): tham số sai hoặc tháng tương lai bị đổi về tháng
// hiện tại, JS đọc lại trường này để ô chọn tháng hiển thị đúng.
public record DailyRevenue(
    String month,
    String maxMonth,
    long totalBookings,
    BigDecimal totalRevenue,
    List<DailyPoint> days
) {
    public record DailyPoint(int day, String label, long bookingCount, BigDecimal revenue) {
    }
}
