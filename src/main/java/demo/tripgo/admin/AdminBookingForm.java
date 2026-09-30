package demo.tripgo.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

// Form admin tạo đơn hộ khách. Ràng buộc số khách và liên hệ giống hệt CreateBookingRequest của
// API, để đơn tạo từ đâu cũng qua cùng một bộ luật. Không có ô giá tiền: server tự tính.
@Getter
@Setter
public class AdminBookingForm {

    @NotNull(message = "Vui lòng chọn tour")
    private Long tourId;

    @NotNull(message = "Vui lòng chọn ngày khởi hành")
    @FutureOrPresent(message = "Ngày khởi hành phải là hôm nay hoặc sau hôm nay")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate departureDate;

    // Đơn gắn vào tài khoản khách có sẵn tìm theo email này (khách thấy đơn trong "Đơn của tôi").
    @NotBlank(message = "Vui lòng nhập email tài khoản khách")
    @Email(message = "Email tài khoản khách không hợp lệ")
    private String customerEmail;

    @NotNull(message = "Vui lòng nhập số người lớn")
    @Min(value = 1, message = "Phải có ít nhất 1 người lớn")
    @Max(value = 100, message = "Số người lớn không được vượt quá 100")
    private Integer adults = 1;

    @NotNull(message = "Vui lòng nhập số trẻ em")
    @Min(value = 0, message = "Số trẻ em không được nhỏ hơn 0")
    @Max(value = 100, message = "Số trẻ em không được vượt quá 100")
    private Integer children = 0;

    @NotBlank(message = "Họ tên liên hệ không được để trống")
    @Size(max = 100, message = "Họ tên liên hệ không được vượt quá 100 ký tự")
    private String contactName;

    @NotBlank(message = "Email liên hệ không được để trống")
    @Email(message = "Email liên hệ không hợp lệ")
    private String contactEmail;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^(0\\d{9,10}|\\+84\\d{9,10})$", message = "Số điện thoại không hợp lệ")
    private String contactPhone;

    @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự")
    private String note;
}
