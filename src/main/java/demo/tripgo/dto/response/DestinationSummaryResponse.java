package demo.tripgo.dto.response;

// Điểm đến kèm số tour cho dropdown lọc, theo hợp đồng 6.4: { slug, name, image, tourCount }.
// Không trả id vì client lọc tour bằng slug (?destination=da-nang), không dùng tới id.
public record DestinationSummaryResponse(
    String slug,
    String name,
    String image,
    long tourCount
) {
}
