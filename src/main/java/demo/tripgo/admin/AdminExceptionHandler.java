package demo.tripgo.admin;

import demo.tripgo.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

// GlobalExceptionHandler chỉ còn phục vụ package controller của API (trả JSON), nên khu quản trị
// cần bản riêng trả về TRANG HTML. Không có nó, một link cũ trỏ tới tour đã bị xoá hẳn sẽ ném
// exception ra ngoài và admin nhận trang 500 thay vì "không tìm thấy".
@ControllerAdvice(basePackages = "demo.tripgo.admin")
public class AdminExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(HttpServletRequest request, Model model) {
        model.addAttribute("path", request.getRequestURI());
        return "error/404";
    }
}
