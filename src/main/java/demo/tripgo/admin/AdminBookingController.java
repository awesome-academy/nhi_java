package demo.tripgo.admin;

import demo.tripgo.entity.Booking;
import demo.tripgo.entity.BookingStatus;
import demo.tripgo.exception.BookingAlreadyCancelledException;
import demo.tripgo.exception.InvalidBookingRequestException;
import demo.tripgo.service.BookingAdminService;
import demo.tripgo.service.ExcelExportService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/bookings")
public class AdminBookingController {

    private static final int PAGE_SIZE = 20;

    private final BookingAdminService bookingAdminService;
    private final ExcelExportService excelExportService;

    public AdminBookingController(
        BookingAdminService bookingAdminService,
        ExcelExportService excelExportService
    ) {
        this.bookingAdminService = bookingAdminService;
        this.excelExportService = excelExportService;
    }

    @GetMapping
    public String list(
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "1") int page,
        Model model
    ) {
        BookingStatus filter = parseStatus(status);
        Page<AdminBookingRow> bookings =
            bookingAdminService.list(filter, Math.max(page, 1), PAGE_SIZE);

        model.addAttribute("activeMenu", "bookings");
        model.addAttribute("pageHeading", "Đơn đặt");
        model.addAttribute("bookings", bookings.getContent());
        model.addAttribute("statuses", BookingStatus.values());
        model.addAttribute("selectedStatus", filter);
        model.addAttribute("currentPage", bookings.getNumber() + 1);
        model.addAttribute("totalPages", Math.max(bookings.getTotalPages(), 1));
        model.addAttribute("total", bookings.getTotalElements());
        return "admin/bookings/list";
    }

    // Xuất đúng bộ lọc đang xem, nên URL nhận cùng tham số status với trang danh sách.
    @GetMapping("/export")
    public ResponseEntity<Resource> export(@RequestParam(required = false) String status) {
        BookingStatus filter = parseStatus(status);
        String suffix = filter == null ? "tat-ca" : filter.name().toLowerCase(java.util.Locale.ROOT);
        return ExcelDownload.of(excelExportService.exportBookings(filter), "don-dat-" + suffix);
    }

    @PostMapping("/{id}/confirm")
    public String confirm(@PathVariable Long id, @RequestParam(required = false) String status,
                          RedirectAttributes redirect) {
        return applyChange(redirect, status,
            () -> bookingAdminService.confirm(id), "Đã xác nhận đơn %s");
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, @RequestParam(required = false) String status,
                         RedirectAttributes redirect) {
        return applyChange(redirect, status,
            () -> bookingAdminService.cancel(id), "Đã huỷ đơn %s, số chỗ đã được hoàn lại");
    }

    // Đổi trạng thái hỏng (đơn không còn chờ xác nhận) không phải lỗi hệ thống mà là tình huống
    // bình thường khi hai admin thao tác cùng lúc -> báo lại trên chính trang danh sách.
    private String applyChange(
        RedirectAttributes redirect,
        String status,
        java.util.function.Supplier<Booking> action,
        String successTemplate
    ) {
        try {
            Booking booking = action.get();
            redirect.addFlashAttribute("flashMessage", successTemplate.formatted(booking.getCode()));
            redirect.addFlashAttribute("flashType", "success");
        } catch (InvalidBookingRequestException | BookingAlreadyCancelledException exception) {
            redirect.addFlashAttribute("flashMessage", exception.getMessage());
            redirect.addFlashAttribute("flashType", "error");
        }
        // Giữ nguyên bộ lọc đang xem để admin không bị nhảy về "Tất cả" sau mỗi thao tác.
        return status == null || status.isBlank()
            ? "redirect:/admin/bookings"
            : "redirect:/admin/bookings?status=" + status;
    }

    // Giá trị lạ trên URL (người dùng sửa tay) coi như không lọc, thay vì ném lỗi 500.
    private BookingStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return BookingStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
