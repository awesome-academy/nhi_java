package demo.tripgo.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

// Form tạo/sửa điểm đến. Cùng quy ước với TourForm: class có getter/setter để Thymeleaf bind
// được, và ảnh dùng cặp (URL hiện tại ở hidden field) + (file mới) + (cờ gỡ ảnh).
@Getter
@Setter
public class DestinationForm {

    private Long id;

    @NotBlank(message = "Tên điểm đến không được để trống")
    @Size(max = 150, message = "Tên điểm đến không được quá 150 ký tự")
    private String name;

    // Bỏ trống -> tự sinh từ tên. Cho sửa tay vì slug là tham số lọc công khai (?destination=da-nang).
    @Size(max = 150, message = "Slug không được quá 150 ký tự")
    private String slug;

    @Size(max = 500, message = "Đường dẫn ảnh không được quá 500 ký tự")
    private String image;

    private MultipartFile imageFile;

    private boolean removeImage;
}
