package demo.tripgo.service;

import demo.tripgo.dto.request.CreateReviewRequest;
import demo.tripgo.dto.response.CreateReviewResponse;
import demo.tripgo.dto.response.ReviewPageResponse;
import demo.tripgo.dto.response.ReviewResponse;
import demo.tripgo.entity.Review;
import demo.tripgo.entity.Tour;
import demo.tripgo.entity.User;
import demo.tripgo.exception.ResourceNotFoundException;
import demo.tripgo.exception.ReviewAlreadyExistsException;
import demo.tripgo.mapper.ReviewMapper;
import demo.tripgo.repository.ReviewRepository;
import demo.tripgo.repository.TourRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final TourRepository tourRepository;
    private final ReviewMapper reviewMapper;

    public ReviewService(
        ReviewRepository reviewRepository,
        TourRepository tourRepository,
        ReviewMapper reviewMapper
    ) {
        this.reviewRepository = reviewRepository;
        this.tourRepository = tourRepository;
        this.reviewMapper = reviewMapper;
    }

    // Đánh giá phân trang (mới nhất trước) + điểm trung bình của tour (lấy từ rating_avg đã denormalized).
    public ReviewPageResponse getReviews(Long tourId, int page, int limit) {
        Tour tour = tourRepository.findById(tourId)
            .orElseThrow(() -> new ResourceNotFoundException("Tour", tourId));
        Pageable pageable = PageRequest.of(
            page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<ReviewResponse> reviews = reviewRepository.findByTourId(tourId, pageable)
            .map(reviewMapper::toResponse);
        return ReviewPageResponse.of(reviews, tour.getRatingAvg());
    }

    @Transactional
    public CreateReviewResponse createReview(Long tourId, User user, CreateReviewRequest request) {
        Tour tour = tourRepository.findById(tourId)
            .orElseThrow(() -> new ResourceNotFoundException("Tour", tourId));
        // Mỗi user chỉ đánh giá một tour một lần (kiểm tra trước, giống register kiểm tra email trùng).
        if (reviewRepository.existsByTourIdAndUserId(tourId, user.getId())) {
            throw new ReviewAlreadyExistsException(tourId);
        }

        Review review = new Review();
        review.setTour(tour);
        review.setUser(user);
        review.setRating(request.rating());
        review.setComment(request.comment());

        Review saved;
        try {
            // saveAndFlush để vi phạm unique bung ngay tại đây (không lọt ra lúc commit ngoài tầm catch).
            saved = reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException exception) {
            // Thua trong cửa sổ race check-then-act: uq_review_tour_user chặn ở DB.
            // Dịch về cùng exception nghiệp vụ để message trả về luôn nhất quán, không phụ thuộc timing.
            throw new ReviewAlreadyExistsException(tourId);
        }

        refreshRatingAggregate(tour, tourId);
        return reviewMapper.toCreateResponse(saved);
    }

    // Cập nhật rating_avg/review_count trên tour để list & detail luôn phản ánh đúng đánh giá mới.
    private void refreshRatingAggregate(Tour tour, Long tourId) {
        Double average = reviewRepository.findAverageRatingByTourId(tourId);
        long count = reviewRepository.countByTourId(tourId);
        tour.setRatingAvg(average == null ? 0.0 : average);
        tour.setReviewCount((int) count);
        // tour đang được quản lý trong transaction → dirty checking tự flush.
    }
}
