package demo.tripgo.dto.response;

import java.time.LocalDate;

// Một ngày khởi hành cùng số chỗ còn lại cho client chọn ngày đặt tour.
public record TourAvailabilityResponse(
    LocalDate departureDate,
    int totalSeats,
    int remainingSeats
) {
}
