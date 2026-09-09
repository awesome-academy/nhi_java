package demo.tripgo.repository;

import demo.tripgo.entity.Tour;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TourRepository extends JpaRepository<Tour, Long>, JpaSpecificationExecutor<Tour> {

    // Fetch join điểm đến + ảnh trong một truy vấn để dựng màn chi tiết, tránh N+1.
    @Query("select t from Tour t "
        + "left join fetch t.destination "
        + "left join fetch t.images "
        + "where t.id = :id")
    Optional<Tour> findDetailById(@Param("id") Long id);

    // Khởi tạo tiếp lịch trình theo ngày cho cùng entity đã nạp (tránh MultipleBagFetchException
    // khi join fetch nhiều collection List cùng lúc).
    @Query("select t from Tour t "
        + "left join fetch t.itinerary "
        + "where t.id = :id")
    Optional<Tour> fetchItinerary(@Param("id") Long id);
}
