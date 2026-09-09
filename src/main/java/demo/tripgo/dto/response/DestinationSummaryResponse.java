package demo.tripgo.dto.response;

// Điểm đến kèm số lượng tour, phục vụ dropdown lọc ở client.
// Tách khỏi DestinationResponse (nhúng trong tour) để không kéo tourCount vào response tour.
public record DestinationSummaryResponse(
    Long id,
    String name,
    String slug,
    long tourCount
) {
}
