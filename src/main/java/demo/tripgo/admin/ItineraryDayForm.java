package demo.tripgo.admin;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// Một dòng trong phần lịch trình của form. Số ngày không nhập tay: nó chính là thứ tự dòng,
// được đánh lại khi lưu nên xoá dòng giữa không để lại lỗ hổng (Ngày 1, 2, 4...).
@Getter
@Setter
public class ItineraryDayForm {

    @Size(max = 200, message = "Tiêu đề ngày không được quá 200 ký tự")
    private String title;

    private String description;

    // Dòng người dùng thêm rồi bỏ trống sẽ bị loại khi lưu thay vì báo lỗi bắt buộc nhập.
    public boolean isBlank() {
        return (title == null || title.isBlank()) && (description == null || description.isBlank());
    }
}
