package demo.tripgo.repository;

import demo.tripgo.entity.Tour;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TourRepository extends JpaRepository<Tour, Long>, JpaSpecificationExecutor<Tour> {

    // Tour đã xoá mềm coi như không tồn tại với khách: dùng hàm này thay cho findById ở mọi
    // luồng công khai (đặt tour, đánh giá, wishlist). Khu quản trị vẫn dùng findById để xem
    // và khôi phục hàng trong thùng rác.
    @Query("select t from Tour t where t.id = :id and t.deletedAt is null")
    Optional<Tour> findActiveById(@Param("id") Long id);

    // Fetch join điểm đến + loại hình + ảnh trong một truy vấn để dựng màn chi tiết, tránh N+1.
    @Query("""
        select t from Tour t
        left join fetch t.destination
        left join fetch t.category
        left join fetch t.images
        where t.id = :id and t.deletedAt is null
        """)
    Optional<Tour> findDetailById(@Param("id") Long id);

    // Tra id từ slug để tái dùng hai truy vấn fetch bên dưới (chỉ lấy id nên rất nhẹ).
    @Query("select t.id from Tour t where t.slug = :slug and t.deletedAt is null")
    Optional<Long> findIdBySlug(@Param("slug") String slug);

    // Thùng rác của khu quản trị: chỉ những tour đã xoá mềm, fetch điểm đến để bảng không N+1.
    @Query(value = """
        select t from Tour t
        left join fetch t.destination
        where t.deletedAt is not null
        """,
        countQuery = "select count(t) from Tour t where t.deletedAt is not null")
    Page<Tour> findDeleted(Pageable pageable);

    // Tổng số tour đang bán (không tính tour trong thùng rác) — số liệu cho dashboard.
    long countByDeletedAtIsNull();

    // Số tour còn sống thuộc một điểm đến: dùng để chặn xoá điểm đến đang có tour.
    long countByDestinationIdAndDeletedAtIsNull(Long destinationId);

    // Slug là unique trên TOÀN bảng, kể cả hàng đã xoá mềm -> phải kiểm tra không lọc deletedAt.
    boolean existsBySlugAndIdNot(String slug, Long id);

    // Khởi tạo tiếp lịch trình theo ngày cho cùng entity đã nạp (tránh MultipleBagFetchException
    // khi join fetch nhiều collection List cùng lúc).
    @Query("""
        select t from Tour t
        left join fetch t.itinerary
        where t.id = :id and t.deletedAt is null
        """)
    Optional<Tour> fetchItinerary(@Param("id") Long id);
}
