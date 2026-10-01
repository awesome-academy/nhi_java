package demo.tripgo.service;

import demo.tripgo.repository.DestinationRepository;
import demo.tripgo.repository.TourImageRepository;
import demo.tripgo.repository.TourRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Xoá file ảnh, nhưng chỉ khi không còn bản ghi nào khác dùng nó.
//
// Vì sao cần: form upload sinh tên UUID nên bình thường mỗi bản ghi có file riêng và xoá thẳng là
// đúng. Nhưng dữ liệu gán bằng script (hoặc nhập tay) có thể cho NHIỀU bản ghi trỏ chung một file
// — lúc đó sửa một tour rồi thay ảnh sẽ xoá mất file của tất cả những bản ghi còn lại.
//
// Đếm >= 2 thì giữ file. Tại thời điểm gọi, bản ghi đang sửa VẪN còn trỏ tới URL cũ trong DB,
// nên "chỉ mình nó dùng" tương ứng với count == 1.
@Service
public class ImageCleanupService {

    private static final Logger log = LoggerFactory.getLogger(ImageCleanupService.class);

    private final FileStorageService fileStorageService;
    private final TourRepository tourRepository;
    private final TourImageRepository tourImageRepository;
    private final DestinationRepository destinationRepository;

    public ImageCleanupService(
        FileStorageService fileStorageService,
        TourRepository tourRepository,
        TourImageRepository tourImageRepository,
        DestinationRepository destinationRepository
    ) {
        this.fileStorageService = fileStorageService;
        this.tourRepository = tourRepository;
        this.tourImageRepository = tourImageRepository;
        this.destinationRepository = destinationRepository;
    }

    @Transactional(readOnly = true)
    public void deleteIfUnused(String url) {
        if (url == null || url.isBlank()) {
            return;
        }

        long references = countReferences(url);
        if (references > 1) {
            log.debug("Giữ lại {}: còn {} bản ghi đang dùng", url, references);
            return;
        }
        fileStorageService.delete(url);
    }

    // Quét cả ba chỗ có thể trỏ tới một file: ảnh đại diện tour, ảnh gallery, ảnh điểm đến.
    private long countReferences(String url) {
        return tourRepository.countByThumbnailUrl(url)
            + tourImageRepository.countByUrl(url)
            + destinationRepository.countByImage(url);
    }
}
