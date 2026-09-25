package demo.tripgo.repository;

import demo.tripgo.entity.AuthProvider;
import demo.tripgo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    // Tài khoản có từ trước khi thêm cột provider; UserProviderBackfill điền nốt lúc khởi động.
    java.util.List<User> findByProviderIsNull();

    // Khoá nhận dạng của tài khoản social: id Facebook không đổi kể cả khi user đổi tên/email.
    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);

    // Principal lấy từ JWT là entity đã detached -> phải nạp lại kèm wishlist trong transaction
    // hiện tại. left join fetch để user chưa lưu tour nào vẫn trả về (wishlist rỗng).
    @Query("select u from User u left join fetch u.wishlist where u.id = :userId")
    Optional<User> findByIdWithWishlist(@Param("userId") Long userId);
}
