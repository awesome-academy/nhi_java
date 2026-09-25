package demo.tripgo.service;

import demo.tripgo.admin.AdminBookingRow;
import demo.tripgo.admin.excel.BookingExportRow;
import demo.tripgo.admin.excel.RevenueExportRow;
import demo.tripgo.admin.excel.TourExportRow;
import demo.tripgo.dto.request.TourListRequest;
import demo.tripgo.dto.response.TourSummaryResponse;
import demo.tripgo.entity.BookingStatus;
import demo.tripgo.excel.ExcelMapper;
import demo.tripgo.repository.BookingRepository;
import demo.tripgo.repository.RevenueByTourView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Dựng file Excel cho ba màn quản trị. Mỗi hàm chỉ lấy dữ liệu rồi map sang lớp *ExportRow;
// phần ghi workbook do ExcelMapper lo.
@Service
public class ExcelExportService {

    // Xuất tối đa bấy nhiêu dòng: file lớn hơn thì vừa chậm vừa dễ hết bộ nhớ, mà admin cũng nên
    // lọc bớt trước khi xuất.
    private static final int EXPORT_LIMIT = 5_000;

    private final ExcelMapper excelMapper;
    private final BookingAdminService bookingAdminService;
    private final TourService tourService;
    private final BookingRepository bookingRepository;

    public ExcelExportService(
        ExcelMapper excelMapper,
        BookingAdminService bookingAdminService,
        TourService tourService,
        BookingRepository bookingRepository
    ) {
        this.excelMapper = excelMapper;
        this.bookingAdminService = bookingAdminService;
        this.tourService = tourService;
        this.bookingRepository = bookingRepository;
    }

    // Xuất theo ĐÚNG bộ lọc admin đang xem, không phải xuất tất cả: lọc xong rồi mới muốn gửi
    // file cho ai đó là thao tác thường gặp hơn nhiều.
    @Transactional(readOnly = true)
    public byte[] exportBookings(BookingStatus status) {
        List<BookingExportRow> rows = bookingAdminService.list(status, 1, EXPORT_LIMIT)
            .getContent().stream()
            .map(this::toBookingRow)
            .toList();
        return excelMapper.write(rows, BookingExportRow.class, "Đơn đặt");
    }

    @Transactional(readOnly = true)
    public byte[] exportTours(String keyword) {
        List<TourExportRow> rows = tourService.listTours(new TourListRequest(
                keyword, null, null, null, null, null, null, "newest", 1, EXPORT_LIMIT))
            .data().stream()
            .map(this::toTourRow)
            .toList();
        return excelMapper.write(rows, TourExportRow.class, "Tour");
    }

    @Transactional(readOnly = true)
    public byte[] exportMonthlyRevenue() {
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        List<RevenueExportRow> rows = bookingRepository.revenueByTourSince(startOfMonth).stream()
            .map(this::toRevenueRow)
            .toList();
        return excelMapper.write(rows, RevenueExportRow.class, "Doanh thu tháng");
    }

    private BookingExportRow toBookingRow(AdminBookingRow booking) {
        BookingExportRow row = new BookingExportRow();
        row.setCode(booking.code());
        row.setCustomerName(booking.customerName());
        row.setCustomerEmail(booking.customerEmail());
        row.setTourTitle(booking.tourTitle());
        row.setDepartureDate(booking.departureDate());
        row.setGuests(booking.guests());
        row.setTotalPrice(booking.totalPrice());
        // Nhãn tiếng Việt thay vì tên enum: file này để người đọc, không phải để máy đọc.
        row.setStatus(booking.status().getLabel());
        return row;
    }

    private TourExportRow toTourRow(TourSummaryResponse tour) {
        TourExportRow row = new TourExportRow();
        row.setId(tour.id());
        row.setTitle(tour.title());
        row.setSlug(tour.slug());
        row.setDestination(tour.destination());
        row.setCategory(tour.category());
        row.setPrice(tour.price());
        row.setDiscountPrice(tour.discountPrice());
        row.setDurationDays(tour.durationDays());
        row.setRating(tour.rating());
        row.setReviewCount(tour.reviewCount());
        return row;
    }

    private RevenueExportRow toRevenueRow(RevenueByTourView view) {
        RevenueExportRow row = new RevenueExportRow();
        row.setTourTitle(view.getTourTitle());
        row.setBookingCount(view.getBookingCount());
        row.setGuestCount(view.getGuestCount());
        row.setRevenue(view.getRevenue());
        return row;
    }
}
