package demo.tripgo.admin;

import demo.tripgo.service.DashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@Controller
public class AdminDashboardController {

    private final DashboardService dashboardService;

    public AdminDashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/admin")
    public String dashboard(Model model) {
        model.addAttribute("activeMenu", "dashboard");
        model.addAttribute("pageHeading", "Dashboard");
        model.addAttribute("stats", dashboardService.stats());
        // Ô chọn tháng: mặc định và giới hạn trên đều là tháng hiện tại (không chọn tháng tương lai).
        model.addAttribute("currentMonth", YearMonth.now().toString());
        return "admin/dashboard";
    }

    // Dữ liệu biểu đồ trả riêng dạng JSON thay vì nhúng vào HTML: tránh chuyện escape JSON trong
    // thẻ <script> của Thymeleaf, và đổi tháng thì chỉ tải lại dữ liệu, không tải lại cả trang.
    //
    // month dạng yyyy-MM (đúng định dạng <input type="month"> gửi lên). Sai định dạng thì coi
    // như không chọn -> tháng hiện tại, thay vì trả lỗi 500 cho một tham số người dùng sửa tay.
    @GetMapping("/admin/reports/chart-data")
    @ResponseBody
    public DailyRevenue dailyRevenue(@RequestParam(required = false) String month) {
        return dashboardService.dailyRevenue(parseMonth(month));
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException exception) {
            return null;
        }
    }
}
