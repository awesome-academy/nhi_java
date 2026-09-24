package demo.tripgo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

// Loại hình tour. Trước đây là enum TourCategory; chuyển thành bảng để danh mục sửa được
// ở tầng dữ liệu mà không phải build lại ứng dụng.
// slug là định danh client gửi lên (?category=beach), name là nhãn tiếng Việt để hiển thị.
@Getter
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    @Setter
    private String slug;

    @Column(nullable = false, length = 100)
    @Setter
    private String name;
}
