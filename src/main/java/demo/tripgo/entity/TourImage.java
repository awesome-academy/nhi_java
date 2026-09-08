package demo.tripgo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

// Ảnh trong gallery của tour, chỉ dùng ở màn chi tiết (danh sách dùng thumbnailUrl denormalized).
@Getter
@Setter
@Entity
@Table(name = "tour_images", indexes = @Index(name = "idx_tour_image_tour", columnList = "tour_id"))
public class TourImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tour_id", nullable = false)
    private Tour tour;

    @Column(nullable = false, length = 500)
    private String url;

    // Thứ tự hiển thị trong gallery.
    @Column(nullable = false)
    private int position;
}
