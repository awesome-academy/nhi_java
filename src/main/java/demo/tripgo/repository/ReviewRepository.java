package demo.tripgo.repository;

import demo.tripgo.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    // Fetch join user để tránh N+1 khi map tên người đánh giá; ManyToOne nên an toàn với phân trang.
    @EntityGraph(attributePaths = "user")
    Page<Review> findByTourId(Long tourId, Pageable pageable);

    boolean existsByTourIdAndUserId(Long tourId, Long userId);

    long countByTourId(Long tourId);

    // Trả null nếu tour chưa có đánh giá nào.
    @Query("select avg(r.rating) from Review r where r.tour.id = :tourId")
    Double findAverageRatingByTourId(@Param("tourId") Long tourId);
}
