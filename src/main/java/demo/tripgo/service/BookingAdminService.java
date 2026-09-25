package demo.tripgo.service;

import demo.tripgo.admin.AdminBookingRow;
import demo.tripgo.entity.Booking;
import demo.tripgo.entity.BookingStatus;
import demo.tripgo.exception.InvalidBookingRequestException;
import demo.tripgo.exception.ResourceNotFoundException;
import demo.tripgo.repository.BookingRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Xem và đổi trạng thái đơn ở khu quản trị. Việc huỷ đơn (có hoàn chỗ, có khoá) uỷ lại cho
// BookingService thay vì chép sang đây.
@Service
public class BookingAdminService {

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;

    public BookingAdminService(BookingRepository bookingRepository, BookingService bookingService) {
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
    }

    @Transactional(readOnly = true)
    public Page<AdminBookingRow> list(BookingStatus status, int page, int size) {
        // Đơn mới nhất lên đầu; thêm id để thứ tự ổn định khi trùng createdAt.
        Pageable pageable = PageRequest.of(page - 1, size,
            Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));

        Page<Booking> bookings = status == null
            ? bookingRepository.findAllBy(pageable)
            : bookingRepository.findByStatus(status, pageable);

        return bookings.map(this::toRow);
    }

    @Transactional(readOnly = true)
    public long countPending() {
        return bookingRepository.countByStatus(BookingStatus.PENDING);
    }

    @Transactional
    public Booking confirm(Long id) {
        Booking booking = bookingRepository.findWithDetailsById(id)
            .orElseThrow(() -> new ResourceNotFoundException("đơn đặt tour"));
        requirePending(booking);

        // Không đụng tới số chỗ: chỗ đã bị trừ ngay lúc khách đặt, xác nhận chỉ đổi trạng thái.
        booking.setStatus(BookingStatus.CONFIRMED);
        return booking;
    }

    @Transactional
    public Booking cancel(Long id) {
        Booking booking = bookingRepository.findWithDetailsById(id)
            .orElseThrow(() -> new ResourceNotFoundException("đơn đặt tour"));
        requirePending(booking);
        return bookingService.cancelByAdmin(id);
    }

    // Chặn ở tầng service chứ không chỉ ẩn nút trên giao diện: nút ẩn không ngăn được request
    // gửi thẳng, và hai admin thao tác cùng lúc trên một đơn là chuyện có thật.
    private void requirePending(Booking booking) {
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new InvalidBookingRequestException(
                "Đơn %s đang ở trạng thái \"%s\", chỉ đơn chờ xác nhận mới đổi được"
                    .formatted(booking.getCode(), booking.getStatus().getLabel()));
        }
    }

    private AdminBookingRow toRow(Booking booking) {
        return new AdminBookingRow(
            booking.getId(),
            booking.getCode(),
            booking.getContact() == null ? null : booking.getContact().getFullName(),
            booking.getContact() == null ? null : booking.getContact().getEmail(),
            booking.getTour().getTitle(),
            booking.getDeparture().getDepartureDate(),
            booking.getAdults() + booking.getChildren(),
            booking.getTotalPrice(),
            booking.getStatus());
    }
}
