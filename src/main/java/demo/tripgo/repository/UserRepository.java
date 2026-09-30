package demo.tripgo.repository;

import demo.tripgo.entity.AuthProvider;
import demo.tripgo.entity.Role;
import demo.tripgo.entity.User;
import demo.tripgo.entity.UserStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    // Tài khoản có từ trước khi thêm cột provider; UserProviderBackfill điền nốt lúc khởi động.
    List<User> findByProviderIsNull();

    // Khoá nhận dạng của tài khoản social: id Facebook không đổi kể cả khi user đổi tên/email.
    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);

    // Principal lấy từ JWT là entity đã detached -> phải nạp lại kèm wishlist trong transaction
    // hiện tại. left join fetch để user chưa lưu tour nào vẫn trả về (wishlist rỗng).
    @Query("select u from User u left join fetch u.wishlist where u.id = :userId")
    Optional<User> findByIdWithWishlist(@Param("userId") Long userId);

    // Ô chọn khách của form admin tạo đơn: chỉ khách (role USER) đang hoạt động.
    List<User> findByRoleAndStatusOrderByEmailAsc(Role role, UserStatus status);

    // Trang Người dùng của khu quản trị. Lọc role bằng "in :roles" (không lọc = truyền mọi role)
    // thay vì ":role is null or ...": tham số enum null khiến Postgres không suy được kiểu.
    @Query("""
        select u from User u
        where u.role in :roles
          and (lower(u.email) like :pattern escape '\\'
               or lower(u.fullName) like :pattern escape '\\')
        """)
    Page<User> searchForAdmin(
        @Param("pattern") String pattern,
        @Param("roles") Collection<Role> roles,
        Pageable pageable
    );

    // Khoá mọi hàng admin trước khi hạ quyền: hai admin cùng hạ quyền nhau một lúc thì người thứ
    // hai phải đợi và đếm lại, không thì cả hai cùng thấy "còn 2 admin" và hệ thống mất sạch admin.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.role = :role")
    List<User> lockByRole(@Param("role") Role role);
}
