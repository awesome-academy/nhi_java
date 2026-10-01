package demo.tripgo.service;

import demo.tripgo.admin.ItineraryDayForm;
import demo.tripgo.admin.TourForm;
import demo.tripgo.admin.TrashTourRow;
import demo.tripgo.entity.BookingStatus;
import demo.tripgo.entity.Category;
import demo.tripgo.entity.Destination;
import demo.tripgo.entity.ItineraryDay;
import demo.tripgo.entity.TourImage;
import demo.tripgo.entity.Tour;
import demo.tripgo.exception.ResourceNotFoundException;
import demo.tripgo.repository.BookingRepository;
import demo.tripgo.repository.CategoryRepository;
import demo.tripgo.repository.DestinationRepository;
import demo.tripgo.repository.TourRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Thao tác chỉ khu quản trị dùng: xoá mềm, khôi phục, xem thùng rác.
// Tách khỏi TourService (phục vụ API công khai) để hai bên không lẫn: TourService chỉ thấy tour
// còn sống, service này cố ý làm việc với cả tour đã xoá.
@Service
public class TourAdminService {

    // Slug là unique trên toàn bảng. Tour đã xoá mà vẫn giữ slug thì tạo tour mới trùng tên sẽ báo
    // lỗi trùng, trong khi admin nhìn danh sách chẳng thấy tour nào tên đó -> rất khó hiểu.
    // Vì vậy lúc xoá thì "trả" slug lại bằng cách gắn hậu tố, lúc khôi phục thì đòi lại nếu còn trống.
    private static final String DELETED_SLUG_MARKER = "-deleted-";
    private static final int SLUG_MAX_LENGTH = 250;

    private final TourRepository tourRepository;
    private final BookingRepository bookingRepository;
    private final DestinationRepository destinationRepository;
    private final CategoryRepository categoryRepository;
    private final FileStorageService fileStorageService;
    private final ImageCleanupService imageCleanupService;

    public TourAdminService(
        TourRepository tourRepository,
        BookingRepository bookingRepository,
        DestinationRepository destinationRepository,
        CategoryRepository categoryRepository,
        FileStorageService fileStorageService,
        ImageCleanupService imageCleanupService
    ) {
        this.tourRepository = tourRepository;
        this.bookingRepository = bookingRepository;
        this.destinationRepository = destinationRepository;
        this.categoryRepository = categoryRepository;
        this.fileStorageService = fileStorageService;
        this.imageCleanupService = imageCleanupService;
    }

    // ---- Tạo / sửa ----

    @Transactional(readOnly = true)
    public TourForm loadForm(Long id) {
        // Đọc trong transaction nên hai collection lazy (images, itinerary) nạp được bình thường.
        Tour tour = tourRepository.findActiveById(id)
            .orElseThrow(() -> new ResourceNotFoundException("tour"));

        TourForm form = new TourForm();
        form.setId(tour.getId());
        form.setTitle(tour.getTitle());
        form.setSlug(tour.getSlug());
        form.setDestinationId(tour.getDestination().getId());
        form.setCategoryId(tour.getCategory().getId());
        form.setPrice(tour.getPrice());
        form.setDiscountPrice(tour.getDiscountPrice());
        form.setDurationDays(tour.getDurationDays());
        form.setMaxGuests(tour.getMaxGuests());
        form.setThumbnailUrl(tour.getThumbnailUrl());
        form.setDescription(tour.getDescription());

        form.setExistingImageUrls(tour.getImages().stream().map(TourImage::getUrl).toList());
        // Mặc định giữ lại toàn bộ ảnh cũ; admin bỏ tick ảnh nào thì ảnh đó bị gỡ khi lưu.
        form.setKeepImageUrls(new ArrayList<>(form.getExistingImageUrls()));

        for (ItineraryDay day : tour.getItinerary()) {
            ItineraryDayForm row = new ItineraryDayForm();
            row.setTitle(day.getTitle());
            row.setDescription(day.getDescription());
            form.getItinerary().add(row);
        }
        return form;
    }

    @Transactional
    public Tour create(TourForm form) {
        Tour tour = new Tour();
        apply(form, tour);
        return tourRepository.save(tour);
    }

    @Transactional
    public Tour update(Long id, TourForm form) {
        Tour tour = tourRepository.findActiveById(id)
            .orElseThrow(() -> new ResourceNotFoundException("tour"));
        apply(form, tour);
        return tourRepository.save(tour);
    }

    // Chỉ chép những trường admin thực sự nhập. ratingAvg/reviewCount/deletedAt do hệ thống quản,
    // cố tình không đụng tới ở đây.
    private void apply(TourForm form, Tour tour) {
        Destination destination = destinationRepository.findActiveById(form.getDestinationId())
            .orElseThrow(() -> new ResourceNotFoundException("điểm đến"));
        Category category = categoryRepository.findById(form.getCategoryId())
            .orElseThrow(() -> new ResourceNotFoundException("loại hình"));

        tour.setTitle(form.getTitle().trim());
        tour.setSlug(uniqueSlug(form, tour));
        tour.setDestination(destination);
        tour.setCategory(category);
        tour.setPrice(form.getPrice());
        tour.setDiscountPrice(form.getDiscountPrice());
        tour.setDurationDays(form.getDurationDays());
        tour.setMaxGuests(form.getMaxGuests());
        tour.setThumbnailUrl(resolveThumbnail(form, tour));
        tour.setDescription(blankToNull(form.getDescription()));

        applyGallery(form, tour);
        applyItinerary(form, tour);
    }

    // Thứ tự ưu tiên: file mới > yêu cầu gỡ > giữ nguyên ảnh cũ.
    private String resolveThumbnail(TourForm form, Tour tour) {
        String current = tour.getThumbnailUrl();

        String uploaded = fileStorageService.store(form.getThumbnailFile());
        if (uploaded != null) {
            imageCleanupService.deleteIfUnused(current);
            return uploaded;
        }
        if (form.isRemoveThumbnail()) {
            imageCleanupService.deleteIfUnused(current);
            return null;
        }
        return current != null ? current : blankToNull(form.getThumbnailUrl());
    }

    private void applyGallery(TourForm form, Tour tour) {
        List<String> keep = form.getKeepImageUrls() == null ? List.of() : form.getKeepImageUrls();

        // Ảnh cũ bị bỏ tick: gỡ khỏi tour (orphanRemoval xoá hàng) và xoá luôn file trên đĩa.
        List<TourImage> removed = tour.getImages().stream()
            .filter(image -> !keep.contains(image.getUrl()))
            .toList();
        removed.forEach(image -> imageCleanupService.deleteIfUnused(image.getUrl()));
        tour.getImages().removeAll(removed);

        if (form.getGalleryFiles() != null) {
            form.getGalleryFiles().stream()
                .map(fileStorageService::store)
                .filter(url -> url != null)
                .forEach(url -> {
                    TourImage image = new TourImage();
                    image.setUrl(url);
                    tour.addImage(image);
                });
        }

        // Đánh lại thứ tự để gallery không có lỗ hổng sau khi xoá ảnh giữa.
        int position = 0;
        for (TourImage image : tour.getImages()) {
            image.setPosition(position++);
        }
    }

    // Thay toàn bộ lịch trình thay vì so khớp từng dòng: đơn giản hơn nhiều mà kết quả như nhau,
    // vì ItineraryDay không được tham chiếu từ đâu khác (orphanRemoval dọn hàng cũ).
    private void applyItinerary(TourForm form, Tour tour) {
        tour.getItinerary().clear();
        if (form.getItinerary() == null) {
            return;
        }
        int dayNumber = 1;
        for (ItineraryDayForm row : form.getItinerary()) {
            if (row == null || row.isBlank()) {
                continue;
            }
            ItineraryDay day = new ItineraryDay();
            day.setDayNumber(dayNumber++);
            // title là NOT NULL; dòng chỉ có mô tả vẫn lưu được với nhãn mặc định.
            day.setTitle(row.getTitle() == null || row.getTitle().isBlank()
                ? "Ngày " + (dayNumber - 1)
                : row.getTitle().trim());
            day.setDescription(blankToNull(row.getDescription()));
            tour.addItineraryDay(day);
        }
    }

    // Admin bỏ trống slug -> sinh từ tên tour. Trùng với tour khác (kể cả tour trong thùng rác,
    // vì ràng buộc unique tính trên cả bảng) -> nối thêm -2, -3... cho tới khi còn trống.
    private String uniqueSlug(TourForm form, Tour tour) {
        String base = form.getSlug() == null || form.getSlug().isBlank()
            ? SlugGenerator.from(form.getTitle())
            : SlugGenerator.from(form.getSlug());
        if (base.isEmpty()) {
            base = "tour";
        }

        Long id = tour.getId();
        String candidate = base;
        int suffix = 2;
        while (tourRepository.existsBySlugAndIdNot(candidate, id == null ? -1L : id)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Transactional(readOnly = true)
    public Page<TrashTourRow> listDeleted(int page, int size) {
        return tourRepository
            .findDeleted(PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "deletedAt")))
            .map(tour -> new TrashTourRow(
                tour.getId(),
                tour.getTitle(),
                tour.getDestination() == null ? null : tour.getDestination().getName(),
                tour.getDeletedAt()));
    }

    // Trả về số đơn còn hiệu lực để báo lại cho admin. KHÔNG chặn xoá: xoá mềm chỉ ẩn tour khỏi
    // danh sách bán, đơn đã đặt vẫn giữ nguyên tham chiếu và khách vẫn đi tour bình thường.
    @Transactional
    public DeleteResult softDelete(Long id) {
        Tour tour = tourRepository.findActiveById(id)
            .orElseThrow(() -> new ResourceNotFoundException("tour"));

        long activeBookings =
            bookingRepository.countByTourIdAndStatusNot(tour.getId(), BookingStatus.CANCELLED);

        tour.setDeletedAt(LocalDateTime.now());
        tour.setSlug(releaseSlug(tour));
        tourRepository.save(tour);

        return new DeleteResult(tour.getTitle(), activeBookings);
    }

    @Transactional
    public String restore(Long id) {
        Tour tour = tourRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("tour"));
        if (!tour.isDeleted()) {
            // Bấm khôi phục hai lần (F5 lại trang) không được coi là lỗi.
            return tour.getTitle();
        }

        tour.setSlug(reclaimSlug(tour));
        tour.setDeletedAt(null);
        tourRepository.save(tour);
        return tour.getTitle();
    }

    // "da-nang-3n2d" -> "da-nang-3n2d-deleted-12". Cắt bớt phần đầu nếu chạm giới hạn cột.
    private String releaseSlug(Tour tour) {
        if (tour.getSlug() == null || tour.getSlug().contains(DELETED_SLUG_MARKER)) {
            return tour.getSlug();
        }
        String suffix = DELETED_SLUG_MARKER + tour.getId();
        String base = tour.getSlug();
        int room = SLUG_MAX_LENGTH - suffix.length();
        if (base.length() > room) {
            base = base.substring(0, room);
        }
        return base + suffix;
    }

    // Đòi lại slug gốc; nếu trong lúc nằm thùng rác đã có tour khác dùng slug đó thì giữ nguyên
    // slug có hậu tố — thà URL xấu còn hơn khôi phục thất bại vì vi phạm ràng buộc unique.
    private String reclaimSlug(Tour tour) {
        String slug = tour.getSlug();
        if (slug == null) {
            return null;
        }
        String suffix = DELETED_SLUG_MARKER + tour.getId();
        if (!slug.endsWith(suffix)) {
            return slug;
        }
        String original = slug.substring(0, slug.length() - suffix.length());
        if (original.isEmpty() || tourRepository.existsBySlugAndIdNot(original, tour.getId())) {
            return slug;
        }
        return original;
    }

    public record DeleteResult(String title, long activeBookings) {
    }
}
