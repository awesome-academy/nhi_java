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
    List<ItineraryDayResponse> itinerary
) {
}
