package demo.tripgo.service;

import demo.tripgo.admin.excel.TourImportRow;
import demo.tripgo.entity.Category;
import demo.tripgo.entity.Destination;
import demo.tripgo.entity.Tour;
import demo.tripgo.repository.CategoryRepository;
import demo.tripgo.repository.DestinationRepository;
import demo.tripgo.repository.TourRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

// Lưu MỘT dòng của file nhập, trong transaction riêng của nó.
//
// Phải là bean riêng chứ không phải method private của TourImportService: @Transactional chạy qua
// proxy, nên gọi từ chính lớp đó (self-invocation) sẽ không mở transaction nào cả — lỗi im lặng.
//
// REQUIRES_NEW: một dòng hỏng chỉ rollback đúng dòng đó, những dòng đã lưu trước vẫn còn.
@Service
public class TourRowImporter {

    private final TourRepository tourRepository;
    private final DestinationRepository destinationRepository;
    private final CategoryRepository categoryRepository;

    public TourRowImporter(
        TourRepository tourRepository,
        DestinationRepository destinationRepository,
        CategoryRepository categoryRepository
    ) {
        this.tourRepository = tourRepository;
        this.destinationRepository = destinationRepository;
        this.categoryRepository = categoryRepository;
    }

    // Trả false nếu slug đã tồn tại (bỏ qua). Trùng thì BỎ QUA chứ không ghi đè: chạy lại cùng một
    // file không được tạo bản sao, và cũng không được lặng lẽ đè lên dữ liệu admin đã sửa tay.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean save(TourImportRow row, String slug) {
        if (tourRepository.existsBySlugAndIdNot(slug, -1L)) {
            return false;
        }

        Destination destination = destinationRepository.findBySlug(row.getDestinationSlug().trim())
            .filter(candidate -> !candidate.isDeleted())
            .orElseThrow(() -> new ImportRowException(
                "không có điểm đến với slug '" + row.getDestinationSlug() + "'"));

        Category category = categoryRepository.findBySlug(row.getCategorySlug().trim())
            .orElseThrow(() -> new ImportRowException(
                "không có loại hình với slug '" + row.getCategorySlug() + "'"));

        validate(row);

        Tour tour = new Tour();
        tour.setTitle(row.getTitle().trim());
        tour.setSlug(slug);
        tour.setDestination(destination);
        tour.setCategory(category);
        tour.setPrice(row.getPrice());
        tour.setDiscountPrice(row.getDiscountPrice());
        tour.setDurationDays(row.getDurationDays());
        tour.setMaxGuests(row.getMaxGuests());
        tour.setDescription(blankToNull(row.getDescription()));
        tourRepository.save(tour);
        return true;
    }

    // Kiểm tra giống hệt form tạo tour. Nhập file không được là đường vòng để lách validation.
    private void validate(TourImportRow row) {
        if (row.getPrice().signum() < 0) {
            throw new ImportRowException("giá không được âm");
        }
        if (row.getDiscountPrice() != null
            && row.getDiscountPrice().compareTo(row.getPrice()) >= 0) {
            throw new ImportRowException("giá khuyến mãi phải nhỏ hơn giá gốc");
        }
        if (row.getDurationDays() < 1 || row.getDurationDays() > 365) {
            throw new ImportRowException("số ngày phải từ 1 đến 365");
        }
        if (row.getMaxGuests() < 1 || row.getMaxGuests() > 1000) {
            throw new ImportRowException("số khách tối đa phải từ 1 đến 1000");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

}
