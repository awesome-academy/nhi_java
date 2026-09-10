package demo.tripgo.controller;

import demo.tripgo.dto.request.CreateBookingRequest;
import demo.tripgo.dto.response.BookingActionResponse;
import demo.tripgo.dto.response.BookingResponse;
import demo.tripgo.dto.response.ListResponse;
import demo.tripgo.entity.User;
import demo.tripgo.security.AuthUtils;
import demo.tripgo.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingActionResponse> createBooking(
        @AuthenticationPrincipal User user,
        @Valid @RequestBody CreateBookingRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(bookingService.createBooking(AuthUtils.requireUser(user), request));
    }

    @GetMapping
    public ResponseEntity<ListResponse<BookingResponse>> getMyBookings(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(bookingService.getMyBookings(AuthUtils.requireUser(user)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getMyBooking(
        @PathVariable Long id,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(bookingService.getMyBooking(AuthUtils.requireUser(user), id));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<BookingActionResponse> cancelBooking(
        @PathVariable Long id,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(bookingService.cancelBooking(AuthUtils.requireUser(user), id));
    }
}
