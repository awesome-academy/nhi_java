package demo.tripgo.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Thông tin liên hệ trong body đặt tour.
public record ContactRequest(
    @NotBlank(message = "Họ tên liên hệ không được để trống")
    @Size(max = 100, message = "Họ tên liên hệ không được vượt quá 100 ký tự")
    String fullName,

    @NotBlank(message = "Email liên hệ không được để trống")
    @Email(message = "Email liên hệ không hợp lệ")
    String email,

    // Số điện thoại VN: bắt đầu bằng 0 hoặc +84 rồi 9-10 chữ số.
    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^(0\\d{9,10}|\\+84\\d{9,10})$", message = "Số điện thoại không hợp lệ")
    String phone,

    // Ghi chú tuỳ chọn.
    @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự")
    String note
) {
}
