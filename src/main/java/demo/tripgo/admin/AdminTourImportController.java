package demo.tripgo.admin;

import demo.tripgo.admin.excel.ImportSummary;
import demo.tripgo.excel.ExcelParseException;
import demo.tripgo.service.TourImportService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Controller
@RequestMapping("/admin/tours/import")
public class AdminTourImportController {

    private final TourImportService tourImportService;

    public AdminTourImportController(TourImportService tourImportService) {
        this.tourImportService = tourImportService;
    }

    @GetMapping
    public String form(Model model) {
        prepare(model);
        return "admin/tours/import";
    }

    // File mẫu sinh từ chính TourImportRow, nên không bao giờ lệch với cột mà bộ nhập mong đợi.
    @GetMapping("/template")
    public ResponseEntity<Resource> template() {
        return ExcelDownload.of(tourImportService.template(), "mau-nhap-tour");
    }

    @PostMapping
    public String upload(@RequestParam("file") MultipartFile file, Model model) {
        prepare(model);

        if (file == null || file.isEmpty()) {
            model.addAttribute("errorMessage", "Vui lòng chọn file Excel");
            return "admin/tours/import";
        }

        try {
            ImportSummary summary = tourImportService.importTours(file.getInputStream());
            model.addAttribute("summary", summary);
        } catch (ExcelParseException exception) {
            // Lỗi ở mức cả file (không phải Excel, thiếu cột bắt buộc...): không có dòng nào để
            // báo riêng, hiện một thông báo chung.
            model.addAttribute("errorMessage", exception.getMessage());
        } catch (IOException exception) {
            model.addAttribute("errorMessage", "Không đọc được file tải lên");
        }
        return "admin/tours/import";
    }

    private void prepare(Model model) {
        model.addAttribute("activeMenu", "tours");
        model.addAttribute("pageHeading", "Nhập tour từ Excel");
    }
}
