package demo.tripgo.repository;

import java.math.BigDecimal;

// Projection cho báo cáo doanh thu theo tour.
public interface RevenueByTourView {
    String getTourTitle();

    long getBookingCount();

    long getGuestCount();

    BigDecimal getRevenue();
}
