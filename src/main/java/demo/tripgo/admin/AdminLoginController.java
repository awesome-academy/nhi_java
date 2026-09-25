package demo.tripgo.admin;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

// Chỉ trả về trang đăng nhập. Việc kiểm tra email/mật khẩu do formLogin của Spring Security làm,
// nên ở đây không có xử lý POST nào cả.
@Controller
public class AdminLoginController {

    @GetMapping("/admin/login")
    public String loginPage(
        @RequestParam(required = false) String error,
        @RequestParam(required = false) String logout,
        @RequestParam(required = false) String denied,
        Model model
    ) {
        if (error != null) {
            model.addAttribute("message", "Email hoặc mật khẩu không đúng");
            model.addAttribute("messageType", "error");
        } else if (denied != null) {
            model.addAttribute("message", "Tài khoản của bạn không có quyền vào khu quản trị");
            model.addAttribute("messageType", "error");
        } else if (logout != null) {
            model.addAttribute("message", "Bạn đã đăng xuất");
            model.addAttribute("messageType", "info");
        }
        return "admin/login";
    }
}
