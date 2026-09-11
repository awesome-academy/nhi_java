package demo.tripgo.controller;

import demo.tripgo.dto.request.CreateBookingRequest;
import demo.tripgo.dto.request.PageQuery;
import demo.tripgo.dto.response.BookingResponse;
import demo.tripgo.dto.response.BookingSummaryResponse;
import demo.tripgo.dto.response.PageResponse;
import demo.tripgo.entity.User;
import demo.tripgo.security.AuthUtils;
import demo.tripgo.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Mọi thao tác với đơn đều yêu cầu đăng nhập (kiểm ở tầng method, bổ trợ URL matcher).
// Quyền sở hữu được đảm bảo trong service qua findByIdAndUserId.
@RestController
@RequestMapping("/bookings")
@PreAuthorize("isAuthenticated()")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
        @AuthenticationPrincipal User user,
        @Valid @RequestBody CreateBookingRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(bookingService.createBooking(AuthUtils.requireUser(user), request));
    }

    @GetMapping
    public ResponseEntity<PageResponse<BookingSummaryResponse>> getMyBookings(
        @AuthenticationPrincipal User user,
        @Valid PageQuery request
    ) {
        return ResponseEntity.ok(bookingService.getMyBookings(
            AuthUtils.requireUser(user), request.pageOrDefault(), request.sizeOrDefault()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getMyBooking(
        @PathVariable Long id,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(bookingService.getMyBooking(AuthUtils.requireUser(user), id));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<BookingResponse> cancelBooking(
        @AuthenticationPrincipal User user,
        @PathVariable Long id
    ) {
        return ResponseEntity.ok(bookingService.cancelBooking(AuthUtils.requireUser(user), id));
    }
}
