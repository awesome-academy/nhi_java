package demo.tripgo.admin;

import demo.tripgo.dto.request.TourListRequest;
import demo.tripgo.entity.Tour;
import demo.tripgo.exception.InvalidRequestParameterException;
import demo.tripgo.repository.CategoryRepository;
import demo.tripgo.repository.DestinationRepository;
import demo.tripgo.dto.response.PageResponse;
import demo.tripgo.dto.response.TourSummaryResponse;
import demo.tripgo.service.ExcelExportService;
import demo.tripgo.service.TourAdminService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import demo.tripgo.service.TourService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// Dùng lại TourService của API công khai thay vì viết truy vấn riêng cho admin. Nhờ vậy tiêu chí
// "thay đổi ở giao diện phản ánh ngay ở API công khai" là hệ quả tự nhiên, không phải nhớ đồng bộ
// hai đường code — và tour đã xoá mềm tự động không lọt vào danh sách.
@Controller
@RequestMapping("/admin/tours")
public class AdminTourController {

    // Bảng quản trị xem được nhiều dòng hơn danh sách cho khách (mặc định 10).
    private static final int PAGE_SIZE = 20;

    private final TourService tourService;
    private final TourAdminService tourAdminService;
    private final DestinationRepository destinationRepository;
    private final CategoryRepository categoryRepository;
    private final ExcelExportService excelExportService;

    public AdminTourController(
        TourService tourService,
        TourAdminService tourAdminService,
        DestinationRepository destinationRepository,
        CategoryRepository categoryRepository,
        ExcelExportService excelExportService
    ) {
        this.excelExportService = excelExportService;
        this.tourService = tourService;
        this.tourAdminService = tourAdminService;
        this.destinationRepository = destinationRepository;
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    public String list(
        @RequestParam(required = false) String q,
        @RequestParam(defaultValue = "1") int page,
        Model model
    ) {
        // page < 1 (người dùng sửa tay trên URL) sẽ làm PageRequest ném lỗi -> chặn tại đây.
        int safePage = Math.max(page, 1);

        PageResponse<TourSummaryResponse> tours = tourService.listTours(new TourListRequest(
            q, null, null, null, null, null, null, "newest", safePage, PAGE_SIZE));

        model.addAttribute("activeMenu", "tours");
        model.addAttribute("pageHeading", "Tour");
        model.addAttribute("tours", tours.data());
        model.addAttribute("q", q);
        model.addAttribute("currentPage", tours.page());
        model.addAttribute("totalPages", totalPages(tours));
        model.addAttribute("total", tours.total());
        return "admin/tours/list";
    }

    @GetMapping("/export")
    public ResponseEntity<Resource> export(@RequestParam(required = false) String q) {
        return ExcelDownload.of(excelExportService.exportTours(q), "tour");
    }

    // ---- Tạo / sửa ----

    @GetMapping("/new")
    public String createForm(Model model) {
        prepareForm(model, new TourForm(), "Thêm tour");
        return "admin/tours/form";
    }

    @PostMapping
    public String create(
        @Valid @ModelAttribute("form") TourForm form,
        BindingResult binding,
        Model model,
        RedirectAttributes redirect
    ) {
        if (hasErrors(form, binding)) {
            prepareForm(model, form, "Thêm tour");
            return "admin/tours/form";
        }

        Tour saved;
        try {
            saved = tourAdminService.create(form);
        } catch (InvalidRequestParameterException exception) {
            // Ảnh sai định dạng/quá nặng: hiện lỗi ngay trên form, giữ nguyên dữ liệu đã gõ,
            // thay vì để exception lọt ra thành trang 500.
            return rejectUpload(form, binding, model, exception, "Thêm tour");
        }
        redirect.addFlashAttribute("flashMessage", "Đã tạo tour \"" + saved.getTitle() + "\"");
        redirect.addFlashAttribute("flashType", "success");
        return "redirect:/admin/tours";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        prepareForm(model, tourAdminService.loadForm(id), "Sửa tour");
        return "admin/tours/form";
    }

    @PostMapping("/{id}")
    public String update(
        @PathVariable Long id,
        @Valid @ModelAttribute("form") TourForm form,
        BindingResult binding,
        Model model,
        RedirectAttributes redirect
    ) {
        if (hasErrors(form, binding)) {
            form.setId(id);
            prepareForm(model, form, "Sửa tour");
            return "admin/tours/form";
        }
        Tour saved;
        try {
            saved = tourAdminService.update(id, form);
        } catch (InvalidRequestParameterException exception) {
            return rejectUpload(form, binding, model, exception, "Sửa tour");
        }
        redirect.addFlashAttribute("flashMessage", "Đã lưu tour \"" + saved.getTitle() + "\"");
        redirect.addFlashAttribute("flashType", "success");
        return "redirect:/admin/tours";
    }

    private String rejectUpload(
        TourForm form,
        BindingResult binding,
        Model model,
        InvalidRequestParameterException exception,
        String heading
    ) {
        binding.reject("upload.invalid", exception.getMessage());
        prepareForm(model, form, heading);
        return "admin/tours/form";
    }

    // Ràng buộc giữa hai trường không diễn đạt được bằng annotation trên một trường,
    // nên kiểm ở đây rồi gắn lỗi vào đúng ô để form hiện đỏ đúng chỗ.
    private boolean hasErrors(TourForm form, BindingResult binding) {
        if (form.hasInvalidDiscount()) {
            binding.rejectValue("discountPrice", "discount.invalid",
                "Giá khuyến mãi phải nhỏ hơn giá gốc");
        }
        return binding.hasErrors();
    }

    // Dropdown điểm đến/loại hình phải nạp lại ở MỌI lần trả view, kể cả khi validate lỗi —
    // quên chỗ này thì form lỗi sẽ hiện hai ô chọn trống trơn.
    private void prepareForm(Model model, TourForm form, String heading) {
        model.addAttribute("form", form);
        model.addAttribute("activeMenu", "tours");
        model.addAttribute("pageHeading", heading);
        model.addAttribute("destinations", destinationRepository.findAll().stream()
            .filter(destination -> !destination.isDeleted())
            .toList());
        model.addAttribute("categories", categoryRepository.findAllByOrderByIdAsc());
    }

    // Thùng rác: xem và khôi phục tour đã xoá mềm.
    @GetMapping("/trash")
    public String trash(@RequestParam(defaultValue = "1") int page, Model model) {
        Page<TrashTourRow> deleted = tourAdminService.listDeleted(Math.max(page, 1), PAGE_SIZE);

        model.addAttribute("activeMenu", "tours");
        model.addAttribute("pageHeading", "Thùng rác");
        model.addAttribute("tours", deleted.getContent());
        model.addAttribute("currentPage", deleted.getNumber() + 1);
        model.addAttribute("totalPages", Math.max(deleted.getTotalPages(), 1));
        model.addAttribute("total", deleted.getTotalElements());
        return "admin/tours/trash";
    }

    // POST + CSRF: xoá là thao tác thay đổi dữ liệu, không được để gọi bằng GET.
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        TourAdminService.DeleteResult result = tourAdminService.softDelete(id);

        String message = "Đã chuyển \"" + result.title() + "\" vào thùng rác";
        if (result.activeBookings() > 0) {
            // Không chặn xoá, nhưng admin cần biết: khách đã đặt vẫn đi tour này bình thường.
            message += " — lưu ý tour còn " + result.activeBookings() + " đơn đặt còn hiệu lực";
        }
        redirect.addFlashAttribute("flashMessage", message);
        redirect.addFlashAttribute("flashType", "success");
        return "redirect:/admin/tours";
    }

    @PostMapping("/{id}/restore")
    public String restore(@PathVariable Long id, RedirectAttributes redirect) {
        String title = tourAdminService.restore(id);
        redirect.addFlashAttribute("flashMessage", "Đã khôi phục \"" + title + "\"");
        redirect.addFlashAttribute("flashType", "success");
        return "redirect:/admin/tours/trash";
    }

    private int totalPages(PageResponse<?> page) {
        if (page.total() == 0) {
            return 1;
        }
        return (int) Math.ceil((double) page.total() / page.limit());
    }
}
