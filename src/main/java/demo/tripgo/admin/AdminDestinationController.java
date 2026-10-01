package demo.tripgo.admin;

import demo.tripgo.entity.Destination;
import demo.tripgo.exception.InvalidRequestParameterException;
import demo.tripgo.service.DestinationAdminService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/destinations")
public class AdminDestinationController {

    private static final int PAGE_SIZE = 20;

    private final DestinationAdminService destinationAdminService;

    public AdminDestinationController(DestinationAdminService destinationAdminService) {
        this.destinationAdminService = destinationAdminService;
    }

    @GetMapping
    public String list(
        @RequestParam(required = false) String q,
        @RequestParam(defaultValue = "1") int page,
        Model model
    ) {
        Page<AdminDestinationRow> destinations =
            destinationAdminService.list(q, Math.max(page, 1), PAGE_SIZE);

        model.addAttribute("activeMenu", "destinations");
        model.addAttribute("pageHeading", "Điểm đến");
        model.addAttribute("destinations", destinations.getContent());
        model.addAttribute("q", q);
        model.addAttribute("currentPage", destinations.getNumber() + 1);
        model.addAttribute("totalPages", Math.max(destinations.getTotalPages(), 1));
        model.addAttribute("total", destinations.getTotalElements());
        return "admin/destinations/list";
    }

    @GetMapping("/trash")
    public String trash(@RequestParam(defaultValue = "1") int page, Model model) {
        Page<AdminDestinationRow> deleted =
            destinationAdminService.listDeleted(Math.max(page, 1), PAGE_SIZE);

        model.addAttribute("activeMenu", "destinations");
        model.addAttribute("pageHeading", "Thùng rác điểm đến");
        model.addAttribute("destinations", deleted.getContent());
        model.addAttribute("currentPage", deleted.getNumber() + 1);
        model.addAttribute("totalPages", Math.max(deleted.getTotalPages(), 1));
        model.addAttribute("total", deleted.getTotalElements());
        return "admin/destinations/trash";
    }

    // ---- Tạo / sửa ----

    @GetMapping("/new")
    public String createForm(Model model) {
        prepareForm(model, new DestinationForm(), "Thêm điểm đến");
        return "admin/destinations/form";
    }

    @PostMapping
    public String create(
        @Valid @ModelAttribute("form") DestinationForm form,
        BindingResult binding,
        Model model,
        RedirectAttributes redirect
    ) {
        if (binding.hasErrors()) {
            prepareForm(model, form, "Thêm điểm đến");
            return "admin/destinations/form";
        }
        try {
            Destination saved = destinationAdminService.create(form);
            flash(redirect, "Đã tạo điểm đến \"" + saved.getName() + "\"", "success");
        } catch (InvalidRequestParameterException exception) {
            return rejectUpload(form, binding, model, exception, "Thêm điểm đến");
        }
        return "redirect:/admin/destinations";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        prepareForm(model, destinationAdminService.loadForm(id), "Sửa điểm đến");
        return "admin/destinations/form";
    }

    @PostMapping("/{id}")
    public String update(
        @PathVariable Long id,
        @Valid @ModelAttribute("form") DestinationForm form,
        BindingResult binding,
        Model model,
        RedirectAttributes redirect
    ) {
        if (binding.hasErrors()) {
            form.setId(id);
            prepareForm(model, form, "Sửa điểm đến");
            return "admin/destinations/form";
        }
        try {
            Destination saved = destinationAdminService.update(id, form);
            flash(redirect, "Đã lưu điểm đến \"" + saved.getName() + "\"", "success");
        } catch (InvalidRequestParameterException exception) {
            form.setId(id);
            return rejectUpload(form, binding, model, exception, "Sửa điểm đến");
        }
        return "redirect:/admin/destinations";
    }

    // ---- Xoá mềm / khôi phục ----

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            String name = destinationAdminService.softDelete(id);
            flash(redirect, "Đã chuyển \"" + name + "\" vào thùng rác", "success");
        } catch (InvalidRequestParameterException exception) {
            // Còn tour thuộc điểm đến này: không phải lỗi hệ thống, báo lại ngay trên danh sách.
            flash(redirect, exception.getMessage(), "error");
        }
        return "redirect:/admin/destinations";
    }

    @PostMapping("/{id}/restore")
    public String restore(@PathVariable Long id, RedirectAttributes redirect) {
        flash(redirect, "Đã khôi phục \"" + destinationAdminService.restore(id) + "\"", "success");
        return "redirect:/admin/destinations/trash";
    }

    private String rejectUpload(
        DestinationForm form,
        BindingResult binding,
        Model model,
        InvalidRequestParameterException exception,
        String heading
    ) {
        binding.reject("upload.invalid", exception.getMessage());
        prepareForm(model, form, heading);
        return "admin/destinations/form";
    }

    private void prepareForm(Model model, DestinationForm form, String heading) {
        model.addAttribute("form", form);
        model.addAttribute("activeMenu", "destinations");
        model.addAttribute("pageHeading", heading);
    }

    private void flash(RedirectAttributes redirect, String message, String type) {
        redirect.addFlashAttribute("flashMessage", message);
        redirect.addFlashAttribute("flashType", type);
    }
}
