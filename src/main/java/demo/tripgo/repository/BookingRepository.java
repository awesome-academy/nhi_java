package demo.tripgo.repository;

import demo.tripgo.entity.Booking;
import demo.tripgo.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Đơn của một user, phân trang; fetch tour + departure để map không N+1.
    @EntityGraph(attributePaths = {"tour", "departure"})
    Page<Booking> findByUserId(Long userId, Pageable pageable);

    // Lấy đơn theo id nhưng ràng buộc thuộc về đúng user (không lộ đơn của người khác).
    @EntityGraph(attributePaths = {"tour", "departure"})
    Optional<Booking> findByIdAndUserId(Long id, Long userId);

    // Bảng đơn của khu quản trị. Fetch sẵn user/tour/departure vì mỗi dòng đều hiển thị cả ba;
    // không có @EntityGraph thì 20 dòng sinh 60 truy vấn phụ.
    @EntityGraph(attributePaths = {"user", "tour", "departure"})
    Page<Booking> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"user", "tour", "departure"})
    Page<Booking> findByStatus(BookingStatus status, Pageable pageable);

    // Nạp kèm quan hệ để đổi trạng thái không phải truy vấn thêm.
    @EntityGraph(attributePaths = {"user", "tour", "departure"})
    Optional<Booking> findWithDetailsById(Long id);

    long countByStatus(BookingStatus status);

    long countByCreatedAtGreaterThanEqual(java.time.LocalDateTime from);

    // Doanh thu tháng: chỉ tính đơn CHƯA huỷ. coalesce để tháng không có đơn nào trả 0 thay vì null.
    @Query("""
        select coalesce(sum(b.totalPrice), 0)
        from Booking b
        where b.status <> demo.tripgo.entity.BookingStatus.CANCELLED
          and b.createdAt >= :from
        """)
    java.math.BigDecimal sumRevenueSince(@Param("from") java.time.LocalDateTime from);

    // Số đơn còn hiệu lực của một tour: dùng để cảnh báo admin trước khi xoá.
    long countByTourIdAndStatusNot(Long tourId, BookingStatus status);

    // Điều kiện được đánh giá: user đã đặt tour này và đơn chưa bị huỷ.
    boolean existsByUserIdAndTourIdAndStatusNot(Long userId, Long tourId, BookingStatus status);
}
