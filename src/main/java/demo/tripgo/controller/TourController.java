package demo.tripgo.controller;

import demo.tripgo.dto.request.TourListRequest;
import demo.tripgo.dto.response.PageResponse;
import demo.tripgo.dto.response.TourDetailResponse;
import demo.tripgo.dto.response.TourSummaryResponse;
import demo.tripgo.service.TourService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tours")
public class TourController {

    private final TourService tourService;

    public TourController(TourService tourService) {
        this.tourService = tourService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<TourSummaryResponse>> listTours(@Valid TourListRequest request) {
        return ResponseEntity.ok(tourService.listTours(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TourDetailResponse> getTour(@PathVariable Long id) {
        return ResponseEntity.ok(tourService.getTourDetail(id));
    }
}
