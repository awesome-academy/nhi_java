package demo.tripgo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Tour du lịch. rating_avg/review_count được denormalized để lọc & sắp xếp theo đánh giá
// chạy thẳng ở DB (có index), tránh join-aggregate và N+1 khi liệt kê.
@Getter
@Setter
@Entity
@Table(
    name = "tours",
    indexes = {
        @Index(name = "idx_tour_destination", columnList = "destination_id"),
        @Index(name = "idx_tour_category", columnList = "category"),
        @Index(name = "idx_tour_price", columnList = "price"),
        @Index(name = "idx_tour_rating", columnList = "rating_avg"),
        @Index(name = "idx_tour_duration", columnList = "duration_days"),
        @Index(name = "idx_tour_created", columnList = "created_at")
    }
)
public class Tour {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    // Định danh thân thiện URL, duy nhất (theo mô hình dữ liệu 6.1).
    @Column(unique = true, length = 250)
    private String slug;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_id", nullable = false)
    private Destination destination;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TourCategory category;

    // Thời lượng tính theo số ngày, dùng cho bộ lọc duration.
    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal price;

    // Giá khuyến mãi (nếu có); null nghĩa là không giảm giá.
    @Column(name = "discount_price", precision = 12, scale = 0)
    private BigDecimal discountPrice;

    @Column(name = "rating_avg", nullable = false)
    private double ratingAvg = 0.0;

    @Column(name = "review_count", nullable = false)
    private int reviewCount = 0;

    @Column(name = "max_guests", nullable = false)
    private int maxGuests;

    // Ảnh đại diện dùng cho card ở danh sách — tránh phải fetch collection ảnh khi phân trang.
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(columnDefinition = "text")
    private String description;

    @ElementCollection
    @CollectionTable(name = "tour_highlights", joinColumns = @JoinColumn(name = "tour_id"))
    @Column(name = "highlight", length = 300)
    private List<String> highlights = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "tour_included", joinColumns = @JoinColumn(name = "tour_id"))
    @Column(name = "item", length = 300)
    private List<String> included = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "tour_excluded", joinColumns = @JoinColumn(name = "tour_id"))
    @Column(name = "item", length = 300)
    private List<String> excluded = new ArrayList<>();

    @OneToMany(mappedBy = "tour", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<TourImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "tour", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayNumber ASC")
    private List<ItineraryDay> itinerary = new ArrayList<>();

    // Gắn hai chiều để cascade lưu đúng khoá ngoại tour_id.
    public void addImage(TourImage image) {
        image.setTour(this);
        images.add(image);
    }

    public void addItineraryDay(ItineraryDay day) {
        day.setTour(this);
        itinerary.add(day);
    }

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
