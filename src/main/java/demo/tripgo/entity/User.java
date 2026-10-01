package demo.tripgo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Entity
// (provider, provider_id) unique: một tài khoản Facebook chỉ gắn được vào đúng một user.
// Postgres coi các NULL là khác nhau nên mọi user LOCAL (provider_id = null) không đụng ràng buộc.
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_id"}))
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 100)
    @Setter
    private String fullName;

    @Column(nullable = false, unique = true)
    @Setter
    private String email;

    // Null với tài khoản đăng nhập bằng Facebook: họ không bao giờ đặt mật khẩu ở TripGo.
    // AuthService.login chặn sẵn trường hợp này để không so khớp mật khẩu với null.
    @Column
    @Setter
    private String password;

    // Cột để NULL được, không phải nullable = false: ddl-auto=update KHÔNG thêm được cột NOT NULL
    // vào bảng đã có dữ liệu (Postgres từ chối, Hibernate chỉ log WARN rồi chạy tiếp — hậu quả là
    // cột không tồn tại và mọi truy vấn users đều hỏng).
    // Hàng cũ mang NULL và được hiểu là LOCAL; hàng mới luôn có giá trị nhờ default bên dưới.
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Setter
    private AuthProvider provider = AuthProvider.LOCAL;

    // Id do Facebook cấp (trường "id" của Graph API). Null với tài khoản LOCAL.
    @Column(name = "provider_id")
    @Setter
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Setter
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Setter
    private UserStatus status = UserStatus.ACTIVE;

    // Wishlist: quan hệ nhiều-nhiều với Tour (hợp đồng 6.6).
    // Dùng Set nên thêm trùng là no-op ngay ở tầng Java; khoá chính ghép (user_id, tour_id)
    // do JPA sinh ra chặn thêm một lần nữa ở tầng DB nếu có race.
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_wishlist",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "tour_id")
    )
    private Set<Tour> wishlist = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // NULL = dữ liệu có từ trước khi thêm cột, coi như tài khoản email/mật khẩu.
    public boolean isLocalAccount() {
        return provider == null || provider == AuthProvider.LOCAL;
    }

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
