package demo.tripgo.mapper;

import demo.tripgo.dto.response.CreateReviewResponse;
import demo.tripgo.dto.response.ReviewResponse;
import demo.tripgo.entity.Review;
import org.springframework.stereotype.Component;

@Component
public class ReviewMapper {

    public ReviewResponse toResponse(Review review) {
        return new ReviewResponse(
            review.getId(),
            new ReviewResponse.Reviewer(review.getUser().getFullName()),
            review.getRating(),
            review.getComment(),
            review.getCreatedAt().toLocalDate()
        );
    }

    // Bọc kèm message giống UserMapper.toRegisterResponse.
    public CreateReviewResponse toCreateResponse(Review review) {
        return new CreateReviewResponse("Đánh giá thành công", toResponse(review));
    }
}
