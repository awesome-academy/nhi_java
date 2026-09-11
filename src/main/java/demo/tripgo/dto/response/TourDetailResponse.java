package demo.tripgo.dto.response;

import java.math.BigDecimal;
import java.util.List;

// Dữ liệu đầy đủ để client dựng màn chi tiết & đặt tour.
public record TourDetailResponse(
    Long id,
    String title,
    String slug,
    DestinationResponse destination,
    String category,
    int durationDays,
    BigDecimal price,
    BigDecimal discountPrice,
    double rating,
    int reviewCount,
    int maxGroupSize,
    String description,
    List<String> highlights,
    List<String> included,
    List<String> excluded,
    List<TourImageResponse> images,
    List<ItineraryDayResponse> itinerary,
    // Các ngày khởi hành sắp tới kèm số chỗ còn và giá — client dựng được form đặt tour
    // ngay trên màn chi tiết, không phải gọi thêm /availability.
    List<TourAvailabilityResponse> startDates
) {
}
