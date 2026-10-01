package demo.tripgo.service;

import demo.tripgo.admin.AdminBookingForm;
import demo.tripgo.admin.AdminBookingRow;
import demo.tripgo.admin.CustomerOption;
import demo.tripgo.admin.DepartureOption;
import demo.tripgo.dto.request.ContactRequest;
import demo.tripgo.dto.request.CreateBookingRequest;
import demo.tripgo.dto.response.BookingResponse;
import demo.tripgo.entity.Role;
import demo.tripgo.entity.Tour;
import demo.tripgo.entity.User;
import demo.tripgo.entity.UserStatus;
import demo.tripgo.entity.Booking;
import demo.tripgo.entity.BookingStatus;
import demo.tripgo.exception.InvalidBookingRequestException;
import demo.tripgo.event.BookingEvent;
import demo.tripgo.exception.ResourceNotFoundException;
import demo.tripgo.repository.BookingRepository;
import demo.tripgo.repository.DepartureRepository;
import demo.tripgo.repository.TourRepository;
import demo.tripgo.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

// Xem và đổi trạng thái đơn ở khu quản trị. Việc huỷ đơn (có hoàn chỗ, có khoá) uỷ lại cho
// BookingService thay vì chép sang đây.
@Service
public class BookingAdminService {

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final ApplicationEventPublisher events;
    private final UserRepository userRepository;
    private final TourRepository tourRepository;
    private final DepartureRepository departureRepository;

    public BookingAdminService(
        BookingRepository bookingRepository,
        BookingService bookingService,
        ApplicationEventPublisher events,
        UserRepository userRepository,
        TourRepository tourRepository,
        DepartureRepository departureRepository
    ) {
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.events = events;
        this.userRepository = userRepository;
        this.tourRepository = tourRepository;
        this.departureRepository = departureRepository;
    }

    // ---- Admin tạo đơn hộ khách ----

    @Transactional(readOnly = true)
    public List<Tour> bookableTours() {
        return tourRepository.findByDeletedAtIsNullOrderByTitleAsc();
    }

    // Chỉ khách (role USER) đang hoạt động: tài khoản bị khoá không đặt được đơn, admin không
    // phải khách. createForCustomer kiểm lại đúng hai điều này vì giá trị <select> sửa tay được.
    @Transactional(readOnly = true)
    public List<CustomerOption> bookableCustomers() {
        return userRepository.findByRoleAndStatusOrderByEmailAsc(Role.USER, UserStatus.ACTIVE).stream()
            .map(user -> new CustomerOption(user.getEmail(), user.getFullName()))
            .toList();
    }

    // Chỉ ngày từ hôm nay trở đi và còn chỗ: chọn ngày đã hết chỗ chỉ để nhận lỗi thì vô ích.
    @Transactional(readOnly = true)
    public List<DepartureOption> upcomingDepartures(Long tourId) {
        if (tourId == null) {
            return List.of();
        }
        return departureRepository
            .findByTourIdAndDepartureDateGreaterThanEqualOrderByDepartureDateAsc(tourId, LocalDate.now())
            .stream()
            .filter(departure -> departure.getRemainingSeats() > 0)
            .map(departure -> new DepartureOption(departure.getDepartureDate(), departure.getRemainingSeats()))
            .toList();
    }

    // Đơn gắn vào tài khoản khách CÓ SẴN (tìm theo email), trạng thái Chờ xác nhận như khách tự
    // đặt — nên cũng qua bước xác nhận và bị job tự huỷ nếu để quá hạn.
    //
    // Việc thật (kiểm chỗ có khoá, trừ chỗ, tính tiền, sinh mã, phát sự kiện gửi mail + báo
    // realtime) uỷ hết cho BookingService.createBooking: chép sang đây là có hai nơi trừ chỗ.
    @Transactional
    public BookingResponse createForCustomer(AdminBookingForm form) {
        String email = form.getCustomerEmail().trim().toLowerCase(Locale.ROOT);
        User customer = userRepository.findByEmail(email)
            .orElseThrow(() -> new InvalidBookingRequestException(
                "Không có tài khoản khách nào với email " + email));
        if (customer.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidBookingRequestException("Tài khoản " + email + " đang không hoạt động");
        }
        if (customer.getRole() != Role.USER) {
            throw new InvalidBookingRequestException("Tài khoản " + email + " không phải tài khoản khách");
        }

        CreateBookingRequest request = new CreateBookingRequest(
            form.getTourId(),
            form.getDepartureDate(),
            form.getAdults(),
            form.getChildren(),
            new ContactRequest(
                form.getContactName().trim(),
                form.getContactEmail().trim(),
                form.getContactPhone().trim(),
                form.getNote() == null || form.getNote().isBlank() ? null : form.getNote().trim()));
        return bookingService.createBooking(customer, request);
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
        events.publishEvent(BookingEvent.of(BookingEvent.Kind.CONFIRMED, booking));
        return booking;
    }

    @Transactional
    public Booking cancel(Long id) {
        Booking booking = bookingRepository.findWithDetailsById(id)
            .orElseThrow(() -> new ResourceNotFoundException("đơn đặt tour"));
        requirePending(booking);
        return bookingService.cancelById(id);
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
