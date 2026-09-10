package demo.tripgo.repository;

import demo.tripgo.entity.Departure;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DepartureRepository extends JpaRepository<Departure, Long> {

    // Ngày khởi hành trong một khoảng (dùng khi lọc theo tháng), sắp theo ngày tăng dần.
    List<Departure> findByTourIdAndDepartureDateBetweenOrderByDepartureDateAsc(
        Long tourId, LocalDate start, LocalDate end);

    // Các ngày khởi hành từ mốc trở đi (mặc định: sắp tới), sắp theo ngày tăng dần.
    List<Departure> findByTourIdAndDepartureDateGreaterThanEqualOrderByDepartureDateAsc(
        Long tourId, LocalDate from);

    // Khoá bi quan hàng khởi hành khi đặt/huỷ để tránh bán vượt số chỗ khi có nhiều request đồng thời.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Departure> findByTourIdAndDepartureDate(Long tourId, LocalDate departureDate);
}
