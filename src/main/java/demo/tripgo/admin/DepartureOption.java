package demo.tripgo.admin;

import java.time.LocalDate;

// Một ngày khởi hành trong ô chọn của form tạo đơn.
public record DepartureOption(LocalDate date, int remainingSeats) {
}
