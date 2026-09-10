package demo.tripgo.controller;

import demo.tripgo.dto.request.CreateReviewRequest;
import demo.tripgo.dto.request.PageQuery;
import demo.tripgo.dto.request.TourListRequest;
import demo.tripgo.dto.response.CreateReviewResponse;
import demo.tripgo.dto.response.ListResponse;
import demo.tripgo.dto.response.PageResponse;
import demo.tripgo.dto.response.ReviewPageResponse;
import demo.tripgo.dto.response.TourAvailabilityResponse;
import demo.tripgo.dto.response.TourDetailResponse;
import demo.tripgo.dto.response.TourSummaryResponse;
import demo.tripgo.entity.User;
import demo.tripgo.security.AuthUtils;
import demo.tripgo.service.ReviewService;
import demo.tripgo.service.TourService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tours")
public class TourController {

    private final TourService tourService;
    private final ReviewService reviewService;

    public TourController(TourService tourService, ReviewService reviewService) {
        this.tourService = tourService;
        this.reviewService = reviewService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<TourSummaryResponse>> listTours(@Valid TourListRequest request) {
        return ResponseEntity.ok(tourService.listTours(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TourDetailResponse> getTour(@PathVariable Long id) {
        return ResponseEntity.ok(tourService.getTourDetail(id));
    }

    @GetMapping("/{id}/availability")
    public ResponseEntity<ListResponse<TourAvailabilityResponse>> getAvailability(
        @PathVariable Long id,
        @RequestParam(required = false) String month
    ) {
        return ResponseEntity.ok(tourService.getAvailability(id, month));
    }

    @GetMapping("/{id}/reviews")
    public ResponseEntity<ReviewPageResponse> getReviews(
        @PathVariable Long id,
        @Valid PageQuery request
    ) {
        return ResponseEntity.ok(
            reviewService.getReviews(id, request.pageOrDefault(), request.limitOrDefault()));
    }

    @PostMapping("/{id}/reviews")
    public ResponseEntity<CreateReviewResponse> createReview(
        @PathVariable Long id,
        @AuthenticationPrincipal User user,
        @Valid @RequestBody CreateReviewRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(reviewService.createReview(id, AuthUtils.requireUser(user), request));
    }
}
