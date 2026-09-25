package demo.tripgo.admin;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

// Khung dashboard của giai đoạn này: bố cục và điều hướng đã chạy, 4 ô số liệu sẽ được nối vào
// dữ liệu thật ở giai đoạn sau (tổng tour, đơn chờ xác nhận, đơn tháng này, doanh thu tháng).
@Controller
public class AdminDashboardController {

    @GetMapping("/admin")
    public String dashboard(Model model) {
        model.addAttribute("activeMenu", "dashboard");
        return "admin/dashboard";
    }
}
