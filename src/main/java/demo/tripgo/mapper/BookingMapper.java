package demo.tripgo.mapper;

import demo.tripgo.dto.response.BookingActionResponse;
import demo.tripgo.dto.response.BookingResponse;
import demo.tripgo.dto.response.ContactResponse;
import demo.tripgo.entity.Booking;
import demo.tripgo.entity.ContactInfo;
import org.springframework.stereotype.Component;

@Component
public class BookingMapper {

    public BookingResponse toResponse(Booking booking) {
        return new BookingResponse(
            booking.getId(),
            booking.getCode(),
            booking.getStatus().name(),
            booking.getTour().getId(),
            booking.getTour().getTitle(),
            booking.getDeparture().getDepartureDate(),
            booking.getAdults(),
            booking.getChildren(),
            booking.getTotalPrice(),
            toContact(booking.getContact()),
            booking.getCreatedAt()
        );
    }

    // Bọc kèm message, giống UserMapper.toRegisterResponse / ReviewMapper.toCreateResponse.
    public BookingActionResponse toCreateResponse(Booking booking) {
        return new BookingActionResponse("Booking created successfully", toResponse(booking));
    }

    public BookingActionResponse toCancelResponse(Booking booking) {
        return new BookingActionResponse("Booking cancelled successfully", toResponse(booking));
    }

    private ContactResponse toContact(ContactInfo contact) {
        return new ContactResponse(contact.getFullName(), contact.getEmail(), contact.getPhone());
    }
}
