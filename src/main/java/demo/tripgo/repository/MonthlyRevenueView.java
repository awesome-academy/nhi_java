package demo.tripgo.repository;

import java.math.BigDecimal;

// Doanh thu gộp theo tháng. Dùng cho biểu đồ xu hướng trên dashboard.
public interface MonthlyRevenueView {
    int getYr();

    int getMth();

    long getBookingCount();

    BigDecimal getRevenue();
}
