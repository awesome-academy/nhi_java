package demo.tripgo.mapper;

import demo.tripgo.dto.response.DestinationResponse;
import demo.tripgo.dto.response.ItineraryDayResponse;
import demo.tripgo.dto.response.TourAvailabilityResponse;
import demo.tripgo.dto.response.TourDetailResponse;
import demo.tripgo.dto.response.TourImageResponse;
import demo.tripgo.dto.response.TourSummaryResponse;
import demo.tripgo.entity.Departure;
import demo.tripgo.entity.Destination;
import demo.tripgo.entity.Tour;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Component
public class TourMapper {

    // Card cho danh sách: chỉ thông tin gọn + thumbnail, không kèm gallery ảnh.
    public TourSummaryResponse toSummary(Tour tour) {
        return new TourSummaryResponse(
            tour.getId(),
            tour.getSlug(),
            tour.getTitle(),
            tour.getDestination().getName(),
            category(tour),
            tour.getDurationDays(),
            tour.getPrice(),
            tour.getDiscountPrice(),
            tour.getRatingAvg(),
            tour.getReviewCount(),
            tour.getThumbnailUrl()
        );
    }

    // Dữ liệu đầy đủ cho màn chi tiết. Các collection phải đã được nạp trong transaction gọi.
    public TourDetailResponse toDetail(Tour tour, List<TourAvailabilityResponse> startDates) {
        List<TourImageResponse> images = tour.getImages().stream()
            .map(image -> new TourImageResponse(image.getUrl(), image.getPosition()))
            .toList();
        List<ItineraryDayResponse> itinerary = tour.getItinerary().stream()
            .map(day -> new ItineraryDayResponse(day.getDayNumber(), day.getTitle(), day.getDescription()))
            .toList();

        return new TourDetailResponse(
            tour.getId(),
            tour.getTitle(),
            tour.getSlug(),
            toDestination(tour.getDestination()),
            category(tour),
            tour.getDurationDays(),
            tour.getPrice(),
            tour.getDiscountPrice(),
            tour.getRatingAvg(),
            tour.getReviewCount(),
            tour.getMaxGuests(),
            tour.getDescription(),
            List.copyOf(tour.getHighlights()),
            List.copyOf(tour.getIncluded()),
            List.copyOf(tour.getExcluded()),
            images,
            itinerary,
            startDates
        );
    }

    // price truyền từ ngoài vào (giá hiệu lực của tour) để không phải nạp lazy Tour cho từng ngày.
    public TourAvailabilityResponse toAvailability(Departure departure, BigDecimal price) {
        return new TourAvailabilityResponse(
            departure.getDepartureDate(),
            departure.getRemainingSeats(),
            price
        );
    }

    // Category xuất dạng slug chữ thường theo hợp đồng (beach|mountain|city|trekking|cruise|cultural).
    private String category(Tour tour) {
        return tour.getCategory().getSlug();
    }

    private DestinationResponse toDestination(Destination destination) {
        return new DestinationResponse(
            destination.getId(),
            destination.getName(),
            destination.getSlug()
        );
    }
}
