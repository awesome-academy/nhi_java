package demo.tripgo.dto.request;

import demo.tripgo.validation.Utf8ByteLength;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 100, message = "Họ tên không được vượt quá 100 ký tự")
    String name,

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    @Size(max = 255, message = "Email không được vượt quá 255 ký tự")
    String email,

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 8, message = "Mật khẩu phải có ít nhất 8 ký tự")
    @Utf8ByteLength(max = 72, message = "Mật khẩu quá dài, vui lòng chọn mật khẩu ngắn hơn")
    String password
) {
}
