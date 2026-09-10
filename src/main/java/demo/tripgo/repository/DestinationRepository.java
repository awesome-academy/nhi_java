package demo.tripgo.repository;

import demo.tripgo.entity.Destination;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DestinationRepository extends JpaRepository<Destination, Long> {

    Optional<Destination> findBySlug(String slug);

    // Đếm số tour cho từng điểm đến trong một truy vấn (left join để điểm đến 0 tour vẫn xuất hiện).
    @Query("""
        select d.id as id, d.name as name, d.slug as slug, count(t.id) as tourCount
        from Destination d
        left join Tour t on t.destination = d
        group by d.id, d.name, d.slug
        order by d.name asc
        """)
    List<DestinationTourCountView> findAllWithTourCount();
}
