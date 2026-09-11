package demo.tripgo.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Thông tin liên hệ trong body đặt tour.
public record ContactRequest(
    @NotBlank(message = "Contact name is required")
    @Size(max = 100, message = "Contact name must not exceed 100 characters")
    String fullName,

    @NotBlank(message = "Contact email is required")
    @Email(message = "Contact email is invalid")
    String email,

    // Số điện thoại VN: bắt đầu bằng 0 hoặc +84 rồi 9-10 chữ số.
    @NotBlank(message = "Contact phone is required")
    @Pattern(regexp = "^(0\\d{9,10}|\\+84\\d{9,10})$", message = "Invalid phone number")
    String phone,

    // Ghi chú tuỳ chọn.
    @Size(max = 500, message = "Note must not exceed 500 characters")
    String note
) {
}
