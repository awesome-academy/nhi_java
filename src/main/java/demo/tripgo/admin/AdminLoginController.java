package demo.tripgo.admin;

import demo.tripgo.config.AdminSocialLogin;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

// Chỉ trả về trang đăng nhập. Việc kiểm tra email/mật khẩu do formLogin của Spring Security làm,
// nên ở đây không có xử lý POST nào cả.
@Controller
public class AdminLoginController {

    private static final String FACEBOOK_ERROR = "facebook";

    // Có bean này nghĩa là đăng nhập Facebook đã bật; không có thì ẩn nút, tránh một nút bấm vào
    // chỉ để nhận lỗi.
    private final boolean facebookLoginEnabled;

    public AdminLoginController(ObjectProvider<AdminSocialLogin> socialLogin) {
        this.facebookLoginEnabled = socialLogin.getIfAvailable() != null;
    }

    @GetMapping("/admin/login")
    public String loginPage(
        @RequestParam(required = false) String error,
        @RequestParam(required = false) String logout,
        @RequestParam(required = false) String denied,
        Model model
    ) {
        model.addAttribute("facebookLoginEnabled", facebookLoginEnabled);

        if (FACEBOOK_ERROR.equals(error)) {
            model.addAttribute("message", "Đăng nhập Facebook không thành công, vui lòng thử lại");
            model.addAttribute("messageType", "error");
        } else if (error != null) {
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
