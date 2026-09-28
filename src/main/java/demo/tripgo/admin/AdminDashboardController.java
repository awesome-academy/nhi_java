package demo.tripgo.admin;

import demo.tripgo.service.DashboardService;
import demo.tripgo.service.ExcelExportService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class AdminDashboardController {

    private final DashboardService dashboardService;
    private final ExcelExportService excelExportService;

    public AdminDashboardController(
        DashboardService dashboardService,
        ExcelExportService excelExportService
    ) {
        this.dashboardService = dashboardService;
        this.excelExportService = excelExportService;
    }

    @GetMapping("/admin")
    public String dashboard(Model model) {
        model.addAttribute("activeMenu", "dashboard");
        model.addAttribute("pageHeading", "Dashboard");
        model.addAttribute("stats", dashboardService.stats());
        return "admin/dashboard";
    }

    // Dữ liệu biểu đồ trả riêng dạng JSON thay vì nhúng vào HTML: tránh chuyện escape JSON trong
    // thẻ <script> của Thymeleaf, và sau này đổi biểu đồ không phải đụng tới controller.
    @GetMapping("/admin/reports/charts")
    @ResponseBody
    public DashboardCharts charts() {
        return dashboardService.charts();
    }

    @GetMapping("/admin/reports/revenue/export")
    public ResponseEntity<Resource> exportRevenue() {
        return ExcelDownload.of(excelExportService.exportMonthlyRevenue(), "doanh-thu-thang");
    }
}
