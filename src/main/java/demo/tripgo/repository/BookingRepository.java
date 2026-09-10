package demo.tripgo.repository;

import demo.tripgo.entity.Booking;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Đơn của một user, mới nhất trước; fetch tour + departure để map không N+1.
    @EntityGraph(attributePaths = {"tour", "departure"})
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

    // Lấy đơn theo id nhưng ràng buộc thuộc về đúng user (không lộ đơn của người khác).
    @EntityGraph(attributePaths = {"tour", "departure"})
    Optional<Booking> findByIdAndUserId(Long id, Long userId);
}
