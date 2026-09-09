package demo.tripgo.service;

import demo.tripgo.dto.request.TourFilter;
import demo.tripgo.dto.request.TourListRequest;
import demo.tripgo.dto.request.TourSort;
import demo.tripgo.dto.response.PageResponse;
import demo.tripgo.dto.response.TourDetailResponse;
import demo.tripgo.dto.response.TourSummaryResponse;
import demo.tripgo.entity.Tour;
import demo.tripgo.entity.TourCategory;
import demo.tripgo.exception.InvalidRequestParameterException;
import demo.tripgo.exception.ResourceNotFoundException;
import demo.tripgo.mapper.TourMapper;
import demo.tripgo.repository.TourRepository;
import demo.tripgo.repository.TourSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class TourService {

    private final TourRepository tourRepository;
    private final TourMapper tourMapper;

    public TourService(TourRepository tourRepository, TourMapper tourMapper) {
        this.tourRepository = tourRepository;
        this.tourMapper = tourMapper;
    }

    // Chuẩn hoá tham số truy vấn rồi lọc + sắp xếp + phân trang ở DB.
    public PageResponse<TourSummaryResponse> listTours(TourListRequest request) {
        TourFilter filter = new TourFilter(
            request.q(),
            request.destination(),
            parseCategory(request.category()),
            request.minPrice(),
            request.maxPrice(),
            request.duration(),
            request.rating()
        );
        TourSort sort = TourSort.from(request.sort());
        Pageable pageable = PageRequest.of(
            request.pageOrDefault() - 1, request.limitOrDefault(), sort.toSort());

        Page<Tour> result = tourRepository.findAll(TourSpecifications.withFilter(filter), pageable);
        return PageResponse.of(result.map(tourMapper::toSummary));
    }

    public TourDetailResponse getTourDetail(Long id) {
        // Truy vấn 1: nạp điểm đến + ảnh; không thấy thì trả 404 thống nhất.
        Tour tour = tourRepository.findDetailById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Tour", id));
        // Truy vấn 2: nạp tiếp lịch trình (tách để tránh join nhiều bag một lúc) rồi gán lại kết quả.
        // fetchItinerary trả về đúng entity managed đã có ảnh ở trên; gán tường minh thay vì dựa vào
        // side-effect ngầm của persistence context, để logic không vỡ âm thầm nếu sau này đổi transaction.
        tour = tourRepository.fetchItinerary(id)
            .orElseThrow(() -> new ResourceNotFoundException("Tour", id));
        return tourMapper.toDetail(tour);
    }

    // Chấp nhận category không phân biệt hoa thường; giá trị lạ → 400.
    private TourCategory parseCategory(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return TourCategory.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestParameterException(
                "Invalid category: " + value + ". Allowed: " + Arrays.toString(TourCategory.values()));
        }
    }
}
