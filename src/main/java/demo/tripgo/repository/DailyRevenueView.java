package demo.tripgo.repository;

import java.math.BigDecimal;

// Doanh thu gộp theo ngày đặt đơn trong một tháng. Alias dy vì "day" là từ khoá.
public interface DailyRevenueView {
    int getDy();

    long getBookingCount();

    BigDecimal getRevenue();
}
