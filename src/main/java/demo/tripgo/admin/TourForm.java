package demo.tripgo.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import org.springframework.util.AutoPopulatingList;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// Dữ liệu của form tạo/sửa tour. Dùng class có getter/setter (không phải record) vì Thymeleaf
// truy cập theo quy ước JavaBean khi bind th:object và hiển thị lại giá trị sau khi validate lỗi.
//
// Tách khỏi entity Tour có chủ ý: form không được phép đụng tới ratingAvg, reviewCount, deletedAt
// hay createdAt — đó là dữ liệu hệ thống tự quản, không phải thứ admin gõ vào.
@Getter
@Setter
public class TourForm {

    private Long id;

    @NotBlank(message = "Tên tour không được để trống")
    @Size(max = 200, message = "Tên tour không được quá 200 ký tự")
    private String title;

    // Bỏ trống -> tự sinh từ tên tour. Cho sửa tay vì slug nằm trên URL công khai.
    @Size(max = 250, message = "Slug không được quá 250 ký tự")
    private String slug;

    @NotNull(message = "Vui lòng chọn điểm đến")
    private Long destinationId;

    @NotNull(message = "Vui lòng chọn loại hình")
    private Long categoryId;

    @NotNull(message = "Giá không được để trống")
    @DecimalMin(value = "0", message = "Giá không được âm")
    private BigDecimal price;

    // Không @NotNull: tour không khuyến mãi thì để trống.
    @DecimalMin(value = "0", message = "Giá khuyến mãi không được âm")
    private BigDecimal discountPrice;

    @NotNull(message = "Thời lượng không được để trống")
    @Min(value = 1, message = "Thời lượng phải ít nhất 1 ngày")
    @Max(value = 365, message = "Thời lượng không được quá 365 ngày")
    private Integer durationDays;

    @NotNull(message = "Số khách tối đa không được để trống")
    @Min(value = 1, message = "Số khách tối đa phải ít nhất 1")
    @Max(value = 1000, message = "Số khách tối đa không được quá 1000")
    private Integer maxGuests;

    // URL ảnh hiện tại (đã lưu trong DB). Là hidden field để lần submit sau không làm mất ảnh cũ
    // khi admin không chọn file mới.
    @Size(max = 500, message = "Đường dẫn ảnh không được quá 500 ký tự")
    private String thumbnailUrl;

    // File admin vừa chọn; có file thì thay ảnh cũ, không có thì giữ nguyên thumbnailUrl.
    private MultipartFile thumbnailFile;

    // Tick vào để gỡ ảnh đại diện mà không cần tải ảnh khác lên.
    private boolean removeThumbnail;

    // Ảnh gallery tải thêm; ảnh cũ giữ trong existingImages, ảnh bị bỏ tick sẽ bị gỡ.
    private List<MultipartFile> galleryFiles = new ArrayList<>();

    private List<String> keepImageUrls = new ArrayList<>();

    // Ảnh gallery đang có, chỉ để hiển thị lại trên form (không bind từ request).
    private List<String> existingImageUrls = new ArrayList<>();

    private String description;

    // Lịch trình theo ngày. Khởi tạo sẵn để Spring bind được itinerary[0].title, itinerary[1]...
    // mà không ném IndexOutOfBounds khi JS thêm dòng mới.
    // AutoPopulatingList tự tạo phần tử khi Spring bind itinerary[n] vượt quá kích thước hiện tại;
    // ArrayList thường sẽ ném IndexOutOfBounds ngay khi JS thêm dòng mới.
    @Valid
    private List<ItineraryDayForm> itinerary = new AutoPopulatingList<>(ItineraryDayForm.class);

    // "Giá KM phải nhỏ hơn giá gốc" là ràng buộc giữa HAI trường nên không đặt được bằng annotation
    // trên một trường; kiểm ở đây rồi gắn lỗi vào đúng ô discountPrice ở controller.
    public boolean hasInvalidDiscount() {
        return price != null && discountPrice != null && discountPrice.compareTo(price) >= 0;
    }
}
