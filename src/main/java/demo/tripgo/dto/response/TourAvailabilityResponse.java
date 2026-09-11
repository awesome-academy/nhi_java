package demo.tripgo.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

// Một ngày khởi hành: ngày đi, số chỗ còn lại và giá mỗi khách cho ngày đó.
public record TourAvailabilityResponse(
    LocalDate date,
    int slotsLeft,
    BigDecimal price
) {
}
