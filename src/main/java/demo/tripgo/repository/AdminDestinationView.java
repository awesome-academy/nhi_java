package demo.tripgo.repository;

import java.time.LocalDateTime;

// Projection cho bảng điểm đến ở khu quản trị: lấy kèm số tour ngay trong truy vấn danh sách,
// thay vì đếm lại cho từng dòng (20 dòng = 20 truy vấn phụ).
public interface AdminDestinationView {
    Long getId();

    String getName();

    String getSlug();

    String getImage();

    long getTourCount();

    LocalDateTime getDeletedAt();
}
