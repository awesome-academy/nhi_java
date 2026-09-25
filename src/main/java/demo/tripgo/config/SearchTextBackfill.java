package demo.tripgo.config;

import demo.tripgo.entity.Destination;
import demo.tripgo.entity.Tour;
import demo.tripgo.repository.DestinationRepository;
import demo.tripgo.repository.TourRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Điền cột tìm kiếm không dấu cho dữ liệu đã có từ trước khi thêm cột.
//
// Không làm bằng SQL: bỏ dấu tiếng Việt trong SQL thuần cần extension unaccent, mà kể cả có thì
// kết quả cũng phải khớp từng ký tự với hàm Java dùng lúc tìm — lệch một chút là tìm không ra.
// Chạy qua entity nên chắc chắn dùng đúng một hàm chuẩn hoá.
//
// Idempotent: chỉ đụng hàng có searchText/searchName còn null, nên khởi động lại không tốn gì.
@Configuration
public class SearchTextBackfill {

    private static final Logger log = LoggerFactory.getLogger(SearchTextBackfill.class);

    @Bean
    public ApplicationRunner backfillSearchText(
        TourRepository tourRepository,
        DestinationRepository destinationRepository
    ) {
        return args -> run(tourRepository, destinationRepository);
    }

    @Transactional
    void run(TourRepository tourRepository, DestinationRepository destinationRepository) {
        List<Tour> tours = tourRepository.findBySearchTextIsNull();
        if (!tours.isEmpty()) {
            // @PreUpdate tự gọi refreshSearchText() khi lưu; gọi tường minh cho rõ ý định.
            tours.forEach(Tour::refreshSearchText);
            tourRepository.saveAll(tours);
            log.info("Đã điền search_text cho {} tour", tours.size());
        }

        List<Destination> destinations = destinationRepository.findBySearchNameIsNull();
        if (!destinations.isEmpty()) {
            destinationRepository.saveAll(destinations);
            log.info("Đã điền search_name cho {} điểm đến", destinations.size());
        }
    }
}
