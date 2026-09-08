package demo.tripgo.mapper;

import demo.tripgo.dto.response.DestinationResponse;
import demo.tripgo.dto.response.ItineraryDayResponse;
import demo.tripgo.dto.response.TourDetailResponse;
import demo.tripgo.dto.response.TourImageResponse;
import demo.tripgo.dto.response.TourSummaryResponse;
import demo.tripgo.entity.Destination;
import demo.tripgo.entity.Tour;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TourMapper {

    // Card cho danh sách: chỉ thông tin gọn + thumbnail, không kèm gallery ảnh.
    public TourSummaryResponse toSummary(Tour tour) {
        return new TourSummaryResponse(
            tour.getId(),
            tour.getTitle(),
            toDestination(tour.getDestination()),
            tour.getCategory(),
            tour.getDurationDays(),
            tour.getPrice(),
            tour.getDiscountPrice(),
            tour.getRatingAvg(),
            tour.getReviewCount(),
            tour.getThumbnailUrl()
        );
    }

    // Dữ liệu đầy đủ cho màn chi tiết. Các collection phải đã được nạp trong transaction gọi.
    public TourDetailResponse toDetail(Tour tour) {
        List<TourImageResponse> images = tour.getImages().stream()
            .map(image -> new TourImageResponse(image.getUrl(), image.getPosition()))
            .toList();
        List<ItineraryDayResponse> itinerary = tour.getItinerary().stream()
            .map(day -> new ItineraryDayResponse(day.getDayNumber(), day.getTitle(), day.getDescription()))
            .toList();

        return new TourDetailResponse(
            tour.getId(),
            tour.getTitle(),
            toDestination(tour.getDestination()),
            tour.getCategory(),
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
            itinerary
        );
    }

    private DestinationResponse toDestination(Destination destination) {
        return new DestinationResponse(
            destination.getId(),
            destination.getName(),
            destination.getSlug()
        );
    }
}
