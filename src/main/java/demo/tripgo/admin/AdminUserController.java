package demo.tripgo.admin;

import demo.tripgo.entity.Role;
import demo.tripgo.entity.User;
import demo.tripgo.exception.InvalidRequestParameterException;
import demo.tripgo.service.UserAdminService;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private static final int PAGE_SIZE = 20;

    private final UserAdminService userAdminService;

    public AdminUserController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    public String list(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String role,
        @RequestParam(defaultValue = "1") int page,
        Model model
    ) {
        Role filter = parseRole(role);
        Page<AdminUserRow> users = userAdminService.list(q, filter, Math.max(page, 1), PAGE_SIZE);

        model.addAttribute("activeMenu", "users");
        model.addAttribute("pageHeading", "Người dùng");
        model.addAttribute("users", users.getContent());
        model.addAttribute("q", q);
        model.addAttribute("selectedRole", filter);
        model.addAttribute("currentPage", users.getNumber() + 1);
        model.addAttribute("totalPages", Math.max(users.getTotalPages(), 1));
        model.addAttribute("total", users.getTotalElements());
        return "admin/users/list";
    }

    // q/filter/page đi kèm form để đổi xong vẫn đứng đúng trang đang xem.
    @PostMapping("/{id}/role")
    public String changeRole(
        @PathVariable Long id,
        @RequestParam String newRole,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String role,
        @RequestParam(required = false) Integer page,
        Authentication authentication,
        RedirectAttributes redirect
    ) {
        Role target = parseRole(newRole);
        if (target == null) {
            flash(redirect, "Vai trò không hợp lệ", "error");
        } else {
            try {
                User user = userAdminService.changeRole(id, target, authentication.getName());
                flash(redirect, target == Role.ADMIN
                    ? "Đã cấp quyền admin cho " + user.getEmail()
                    : "Đã chuyển " + user.getEmail() + " về người dùng thường", "success");
            } catch (InvalidRequestParameterException exception) {
                flash(redirect, exception.getMessage(), "error");
            }
        }

        // addAttribute nối vào query string và tự encode (tìm kiếm có dấu tiếng Việt vẫn đúng).
        if (q != null && !q.isBlank()) {
            redirect.addAttribute("q", q);
        }
        Role filter = parseRole(role);
        if (filter != null) {
            redirect.addAttribute("role", filter.name());
        }
        if (page != null && page > 1) {
            redirect.addAttribute("page", page);
        }
        return "redirect:/admin/users";
    }

    // Giá trị lạ trên URL (người dùng sửa tay) coi như không lọc, thay vì ném lỗi 500.
    private Role parseRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        try {
            return Role.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void flash(RedirectAttributes redirect, String message, String type) {
        redirect.addFlashAttribute("flashMessage", message);
        redirect.addFlashAttribute("flashType", type);
    }
}
