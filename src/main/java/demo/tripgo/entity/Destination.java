package demo.tripgo.entity;

import demo.tripgo.service.SearchText;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// Điểm đến của tour; slug dùng làm khoá lọc thân thiện URL (vd "da-nang").
@Getter
@Entity
@Table(name = "destinations")
public class Destination {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    @Setter
    private String name;

    // Đã có unique index tự sinh, phục vụ lọc theo ?destination=<slug>.
    @Column(nullable = false, unique = true, length = 150)
    @Setter
    private String slug;

    // Ảnh đại diện điểm đến (hợp đồng 6.4); cho phép null vì không bắt buộc có ảnh.
    @Column(length = 500)
    @Setter
    private String image;

    // null = đang hoạt động. Xoá mềm chỉ ghi mốc thời gian, hàng vẫn nằm lại trong bảng để đơn đặt
    // và đánh giá cũ còn tra được (đó là lý do không dùng DELETE thật).
    @Column(name = "deleted_at")
    @Setter
    private LocalDateTime deletedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    // Dạng không dấu của tên, dùng cho tìm kiếm (xem Tour.searchText).
    @Column(name = "search_name", length = 300)
    private String searchName;

    @PrePersist
    @PreUpdate
    void refreshSearchName() {
        searchName = SearchText.normalize(name);
    }

}
