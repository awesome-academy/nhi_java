package demo.tripgo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// Đánh giá của một user cho một tour. Ràng buộc mỗi user chỉ đánh giá một tour một lần.
@Getter
@Setter
@Entity
@Table(
    name = "reviews",
    indexes = @Index(name = "idx_review_tour", columnList = "tour_id"),
    uniqueConstraints = @UniqueConstraint(name = "uq_review_tour_user", columnNames = {"tour_id", "user_id"})
)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tour_id", nullable = false)
    private Tour tour;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Điểm đánh giá 1..5 (kiểm tra ở tầng request).
    @Column(nullable = false)
    private int rating;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
