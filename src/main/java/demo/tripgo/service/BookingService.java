package demo.tripgo.service;

import demo.tripgo.dto.request.CreateBookingRequest;
import demo.tripgo.dto.response.BookingActionResponse;
import demo.tripgo.dto.response.BookingResponse;
import demo.tripgo.dto.response.ListResponse;
import demo.tripgo.entity.Booking;
import demo.tripgo.entity.BookingStatus;
import demo.tripgo.entity.ContactInfo;
import demo.tripgo.entity.Departure;
import demo.tripgo.entity.Tour;
import demo.tripgo.entity.User;
import demo.tripgo.exception.BookingAlreadyCancelledException;
import demo.tripgo.exception.InvalidBookingRequestException;
import demo.tripgo.exception.ResourceNotFoundException;
import demo.tripgo.exception.SoldOutException;
import demo.tripgo.mapper.BookingMapper;
import demo.tripgo.repository.BookingRepository;
import demo.tripgo.repository.DepartureRepository;
import demo.tripgo.repository.TourRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@Transactional(readOnly = true)
public class BookingService {

    private final BookingRepository bookingRepository;
    private final TourRepository tourRepository;
    private final DepartureRepository departureRepository;
    private final BookingMapper bookingMapper;

    @PersistenceContext
    private EntityManager entityManager;

    public BookingService(
        BookingRepository bookingRepository,
        TourRepository tourRepository,
        DepartureRepository departureRepository,
        BookingMapper bookingMapper
    ) {
        this.bookingRepository = bookingRepository;
        this.tourRepository = tourRepository;
        this.departureRepository = departureRepository;
        this.bookingMapper = bookingMapper;
    }

    @Transactional
    public BookingActionResponse createBooking(User user, CreateBookingRequest request) {
        Tour tour = tourRepository.findById(request.tourId())
            .orElseThrow(() -> new ResourceNotFoundException("Tour", request.tourId()));

        // Khoá hàng khởi hành để kiểm tra & trừ chỗ an toàn với request đồng thời.
        Departure departure = departureRepository
            .findByTourIdAndDepartureDate(tour.getId(), request.date())
            .orElseThrow(() -> new InvalidBookingRequestException(
                "No departure available for tour " + tour.getId() + " on " + request.date()));

        int guests = request.adults() + request.children();
        if (departure.getRemainingSeats() < guests) {
            throw new SoldOutException("Only " + departure.getRemainingSeats()
                + " seat(s) left for " + request.date() + ", requested " + guests);
        }
        departure.setBookedSeats(departure.getBookedSeats() + guests);

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setTour(tour);
        booking.setDeparture(departure);
        booking.setAdults(request.adults());
        booking.setChildren(request.children());
        booking.setTotalPrice(totalPrice(tour, guests));
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setContact(toContactInfo(request));
        Booking saved = bookingRepository.save(booking);

        // Sinh mã đơn từ id (đảm bảo duy nhất) sau khi đã có id.
        saved.setCode(generateCode(saved.getId()));
        return bookingMapper.toCreateResponse(saved);
    }

    public ListResponse<BookingResponse> getMyBookings(User user) {
        return ListResponse.of(
            bookingRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(bookingMapper::toResponse)
                .toList()
        );
    }

    public BookingResponse getMyBooking(User user, Long id) {
        Booking booking = bookingRepository.findByIdAndUserId(id, user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Booking", id));
        return bookingMapper.toResponse(booking);
    }

    @Transactional
    public BookingActionResponse cancelBooking(User user, Long id) {
        Booking booking = bookingRepository.findByIdAndUserId(id, user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Booking", id));
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BookingAlreadyCancelledException(booking.getCode());
        }

        booking.setStatus(BookingStatus.CANCELLED);
        // Hoàn lại số chỗ đã giữ. Departure đã được nạp sẵn (qua @EntityGraph) nên phải refresh KÈM
        // khoá bi quan để đọc lại giá trị mới nhất dưới lock — tránh lost update khi 2 request huỷ
        // cùng departure đồng thời (query có @Lock nhưng entity đang cache sẽ trả giá trị cũ).
        Departure departure = booking.getDeparture();
        entityManager.refresh(departure, LockModeType.PESSIMISTIC_WRITE);
        departure.setBookedSeats(departure.getBookedSeats() - (booking.getAdults() + booking.getChildren()));
        return bookingMapper.toCancelResponse(booking);
    }

    // Giá mỗi khách = giá KM nếu có, ngược lại giá gốc; tổng = giá * tổng số khách.
    private BigDecimal totalPrice(Tour tour, int guests) {
        BigDecimal unitPrice = tour.getDiscountPrice() != null ? tour.getDiscountPrice() : tour.getPrice();
        return unitPrice.multiply(BigDecimal.valueOf(guests));
    }

    private String generateCode(Long id) {
        return String.format("TG-%d-%06d", LocalDate.now().getYear(), id);
    }

    private ContactInfo toContactInfo(CreateBookingRequest request) {
        ContactInfo contact = new ContactInfo();
        contact.setFullName(request.contact().fullName());
        contact.setEmail(request.contact().email());
        contact.setPhone(request.contact().phone());
        return contact;
    }
}
