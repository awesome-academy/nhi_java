package demo.tripgo.service;

import demo.tripgo.admin.AdminDestinationRow;
import demo.tripgo.admin.DestinationForm;
import demo.tripgo.entity.Destination;
import demo.tripgo.exception.InvalidRequestParameterException;
import demo.tripgo.exception.ResourceNotFoundException;
import demo.tripgo.repository.AdminDestinationView;
import demo.tripgo.repository.DestinationRepository;
import demo.tripgo.repository.TourRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// CRUD điểm đến cho khu quản trị. Cùng mô hình với TourAdminService: xoá mềm + thùng rác,
// slug được "trả lại" khi xoá để tên cũ dùng lại được.
@Service
public class DestinationAdminService {

    private static final String DELETED_SLUG_MARKER = "-deleted-";
    private static final int SLUG_MAX_LENGTH = 150;

    private final DestinationRepository destinationRepository;
    private final TourRepository tourRepository;
    private final FileStorageService fileStorageService;
    private final ImageCleanupService imageCleanupService;

    public DestinationAdminService(
        DestinationRepository destinationRepository,
        TourRepository tourRepository,
        FileStorageService fileStorageService,
        ImageCleanupService imageCleanupService
    ) {
        this.destinationRepository = destinationRepository;
        this.tourRepository = tourRepository;
        this.fileStorageService = fileStorageService;
        this.imageCleanupService = imageCleanupService;
    }

    @Transactional(readOnly = true)
    public Page<AdminDestinationRow> list(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("name"));
        return destinationRepository.findActiveWithTourCount(SearchText.likePattern(keyword), pageable)
            .map(this::toRow);
    }

    @Transactional(readOnly = true)
    public Page<AdminDestinationRow> listDeleted(int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size,
            Sort.by(Sort.Direction.DESC, "deletedAt"));
        return destinationRepository.findDeletedForAdmin(pageable).map(this::toRow);
    }

    @Transactional(readOnly = true)
    public DestinationForm loadForm(Long id) {
        Destination destination = destinationRepository.findActiveById(id)
            .orElseThrow(() -> new ResourceNotFoundException("điểm đến"));

        DestinationForm form = new DestinationForm();
        form.setId(destination.getId());
        form.setName(destination.getName());
        form.setSlug(destination.getSlug());
        form.setImage(destination.getImage());
        return form;
    }

    @Transactional
    public Destination create(DestinationForm form) {
        Destination destination = new Destination();
        apply(form, destination);
        return destinationRepository.save(destination);
    }

    @Transactional
    public Destination update(Long id, DestinationForm form) {
        Destination destination = destinationRepository.findActiveById(id)
            .orElseThrow(() -> new ResourceNotFoundException("điểm đến"));
        apply(form, destination);
        return destinationRepository.save(destination);
    }

    // Khác tour: điểm đến CÓ chặn xoá khi còn tour. Xoá mềm không cắt quan hệ, nên những tour đó
    // sẽ trỏ tới một điểm đến vô hình và bộ lọc theo điểm đến ở API công khai ra kết quả kỳ quặc.
    @Transactional
    public String softDelete(Long id) {
        Destination destination = destinationRepository.findActiveById(id)
            .orElseThrow(() -> new ResourceNotFoundException("điểm đến"));

        long tourCount = tourRepository.countByDestinationIdAndDeletedAtIsNull(id);
        if (tourCount > 0) {
            throw new InvalidRequestParameterException(
                "Không thể xoá \"%s\": còn %d tour thuộc điểm đến này".formatted(
                    destination.getName(), tourCount));
        }

        destination.setDeletedAt(LocalDateTime.now());
        destination.setSlug(releaseSlug(destination));
        destinationRepository.save(destination);
        return destination.getName();
    }

    @Transactional
    public String restore(Long id) {
        Destination destination = destinationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("điểm đến"));
        if (!destination.isDeleted()) {
            return destination.getName();
        }
        destination.setSlug(reclaimSlug(destination));
        destination.setDeletedAt(null);
        destinationRepository.save(destination);
        return destination.getName();
    }

    private void apply(DestinationForm form, Destination destination) {
        destination.setName(form.getName().trim());
        destination.setSlug(uniqueSlug(form, destination));
        destination.setImage(resolveImage(form, destination));
    }

    // Thứ tự ưu tiên giống ảnh tour: file mới > yêu cầu gỡ > giữ nguyên ảnh cũ.
    private String resolveImage(DestinationForm form, Destination destination) {
        String current = destination.getImage();

        String uploaded = fileStorageService.store(form.getImageFile());
        if (uploaded != null) {
            imageCleanupService.deleteIfUnused(current);
            return uploaded;
        }
        if (form.isRemoveImage()) {
            imageCleanupService.deleteIfUnused(current);
            return null;
        }
        return current != null ? current : blankToNull(form.getImage());
    }

    private String uniqueSlug(DestinationForm form, Destination destination) {
        String base = form.getSlug() == null || form.getSlug().isBlank()
            ? SlugGenerator.from(form.getName())
            : SlugGenerator.from(form.getSlug());
        if (base.isEmpty()) {
            base = "diem-den";
        }

        Long id = destination.getId();
        String candidate = base;
        int suffix = 2;
        while (destinationRepository.existsBySlugAndIdNot(candidate, id == null ? -1L : id)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private String releaseSlug(Destination destination) {
        if (destination.getSlug() == null || destination.getSlug().contains(DELETED_SLUG_MARKER)) {
            return destination.getSlug();
        }
        String suffix = DELETED_SLUG_MARKER + destination.getId();
        String base = destination.getSlug();
        int room = SLUG_MAX_LENGTH - suffix.length();
        if (base.length() > room) {
            base = base.substring(0, room);
        }
        return base + suffix;
    }

    private String reclaimSlug(Destination destination) {
        String slug = destination.getSlug();
        if (slug == null) {
            return null;
        }
        String suffix = DELETED_SLUG_MARKER + destination.getId();
        if (!slug.endsWith(suffix)) {
            return slug;
        }
        String original = slug.substring(0, slug.length() - suffix.length());
        if (original.isEmpty()
            || destinationRepository.existsBySlugAndIdNot(original, destination.getId())) {
            return slug;
        }
        return original;
    }

    private AdminDestinationRow toRow(AdminDestinationView view) {
        return new AdminDestinationRow(
            view.getId(),
            view.getName(),
            view.getSlug(),
            view.getImage(),
            view.getTourCount(),
            view.getDeletedAt());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
