package demo.tripgo.admin;

import java.math.BigDecimal;

// Bốn ô số liệu trên dashboard.
public record DashboardStats(
    long totalTours,
    long pendingBookings,
    long bookingsThisMonth,
    BigDecimal revenueThisMonth
) {
}
