package demo.tripgo.repository;

import demo.tripgo.entity.TourImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TourImageRepository extends JpaRepository<TourImage, Long> {

    long countByUrl(String url);
}
