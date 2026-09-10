package demo.tripgo.repository;

import demo.tripgo.entity.Departure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DepartureRepository extends JpaRepository<Departure, Long> {

    // Ngày khởi hành trong một khoảng (dùng khi lọc theo tháng), sắp theo ngày tăng dần.
    List<Departure> findByTourIdAndDepartureDateBetweenOrderByDepartureDateAsc(
        Long tourId, LocalDate start, LocalDate end);

    // Các ngày khởi hành từ mốc trở đi (mặc định: sắp tới), sắp theo ngày tăng dần.
    List<Departure> findByTourIdAndDepartureDateGreaterThanEqualOrderByDepartureDateAsc(
        Long tourId, LocalDate from);
}
