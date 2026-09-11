package demo.tripgo.mapper;

import demo.tripgo.dto.response.BookingResponse;
import demo.tripgo.dto.response.BookingSummaryResponse;
import demo.tripgo.entity.Booking;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class BookingMapper {

    // Chi tiết đơn (POST tạo & GET /{id}) theo 6.5: status chữ thường, createdAt chỉ ngày.
    public BookingResponse toResponse(Booking booking) {
        return new BookingResponse(
            booking.getId(),
            booking.getCode(),
            booking.getTour().getId(),
            booking.getDeparture().getDepartureDate(),
            booking.getAdults(),
            booking.getChildren(),
            booking.getTotalPrice(),
            status(booking),
            booking.getCreatedAt().toLocalDate()
        );
    }

    // Item danh sách với tour gọn { title, thumbnail }.
    public BookingSummaryResponse toSummary(Booking booking) {
        return new BookingSummaryResponse(
            booking.getId(),
            booking.getCode(),
            new BookingSummaryResponse.TourBrief(
                booking.getTour().getTitle(),
                booking.getTour().getThumbnailUrl()
            ),
            booking.getDeparture().getDepartureDate(),
            booking.getTotalPrice(),
            status(booking)
        );
    }

    private String status(Booking booking) {
        return booking.getStatus().name().toLowerCase(Locale.ROOT);
    }
}
