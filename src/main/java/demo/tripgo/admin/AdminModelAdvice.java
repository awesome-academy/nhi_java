package demo.tripgo.admin;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

// Topbar cần biết ai đang đăng nhập ở MỌI trang quản trị. Đưa vào model một chỗ thay vì lặp
// ở từng controller (và tránh phải thêm thư viện thymeleaf-extras-springsecurity chỉ để lấy tên).
@ControllerAdvice(basePackages = "demo.tripgo.admin")
public class AdminModelAdvice {

    @ModelAttribute("currentAdmin")
    public String currentAdmin(Authentication authentication) {
        return authentication == null ? null : authentication.getName();
    }
}
