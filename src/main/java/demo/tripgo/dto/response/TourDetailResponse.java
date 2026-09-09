package demo.tripgo.dto.response;

import demo.tripgo.entity.TourCategory;

import java.math.BigDecimal;
import java.util.List;

// Dữ liệu đầy đủ để client dựng màn chi tiết & đặt tour.
public record TourDetailResponse(
    Long id,
    String title,
    DestinationResponse destination,
    TourCategory category,
    int durationDays,
    BigDecimal price,
    BigDecimal discountPrice,
    double ratingAvg,
    int reviewCount,
    int maxGuests,
    String description,
    List<String> highlights,
    List<String> included,
    List<String> excluded,
    List<TourImageResponse> images,
    List<ItineraryDayResponse> itinerary
) {
}
