package demo.tripgo.repository;

import demo.tripgo.entity.Destination;
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
