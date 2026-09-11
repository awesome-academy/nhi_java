package demo.tripgo.repository;

import demo.tripgo.entity.Booking;
import demo.tripgo.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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

    // Điều kiện được đánh giá: user đã đặt tour này và đơn chưa bị huỷ.
    boolean existsByUserIdAndTourIdAndStatusNot(Long userId, Long tourId, BookingStatus status);
}
