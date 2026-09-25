package demo.tripgo.repository;

import demo.tripgo.entity.Destination;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DestinationRepository extends JpaRepository<Destination, Long> {

    Optional<Destination> findBySlug(String slug);

    // Điểm đến đã xoá mềm coi như không tồn tại với khách.
    @Query("select d from Destination d where d.id = :id and d.deletedAt is null")
    Optional<Destination> findActiveById(@Param("id") Long id);

    // Danh sách cho khu quản trị, lấy kèm số tour ngay trong truy vấn. Điều kiện deletedAt của
    // Tour nằm trong ON (không phải WHERE) để điểm đến chưa có tour nào vẫn xuất hiện.
    //
    // Nhận sẵn pattern LIKE ('%' khi không lọc) thay vì ":keyword is null or ...": Postgres không
    // suy được kiểu của tham số null nên bind thành bytea và ném "function lower(bytea) does not
    // exist". H2 trong test lại chạy bình thường, nên lỗi kiểu này chỉ lộ ra ở môi trường thật.
    @Query(value = """
        select d.id as id, d.name as name, d.slug as slug, d.image as image,
               count(t.id) as tourCount, d.deletedAt as deletedAt
        from Destination d
        left join Tour t on t.destination = d and t.deletedAt is null
        where d.deletedAt is null
          and lower(d.name) like lower(:pattern) escape '\\'
        group by d.id, d.name, d.slug, d.image, d.deletedAt
        """,
        countQuery = """
            select count(d) from Destination d
            where d.deletedAt is null
              and lower(d.name) like lower(:pattern) escape '\\'
            """)
    Page<AdminDestinationView> findActiveWithTourCount(
        @Param("pattern") String pattern, Pageable pageable);

    // Thùng rác: điểm đến đã xoá mềm thì theo định nghĩa không còn tour nào, nên tourCount = 0.
    @Query(value = """
        select d.id as id, d.name as name, d.slug as slug, d.image as image,
               0L as tourCount, d.deletedAt as deletedAt
        from Destination d
        where d.deletedAt is not null
        """,
        countQuery = "select count(d) from Destination d where d.deletedAt is not null")
    Page<AdminDestinationView> findDeletedForAdmin(Pageable pageable);

    // Slug unique tính trên toàn bảng, kể cả hàng đã xoá mềm -> không lọc deletedAt ở đây.
    boolean existsBySlugAndIdNot(String slug, Long id);

    // Đếm số tour cho từng điểm đến trong một truy vấn (left join để điểm đến 0 tour vẫn xuất hiện).
    // Điều kiện deletedAt của Tour nằm trong ON chứ không phải WHERE: đặt ở WHERE sẽ biến left join
    // thành inner join và làm rớt mất những điểm đến chưa có tour nào.
    @Query("""
        select d.slug as slug, d.name as name, d.image as image, count(t.id) as tourCount
        from Destination d
        left join Tour t on t.destination = d and t.deletedAt is null
        where d.deletedAt is null
        group by d.id, d.slug, d.name, d.image
        order by d.name asc
        """)
    List<DestinationTourCountView> findAllWithTourCount();
}
