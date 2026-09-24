package demo.tripgo.dto.response;

// DELETE /wishlist/{tourId} trả kết quả của chính thao tác xoá, không trả lại cả danh sách:
// client cần biết "xoá được hay không", còn danh sách mới thì gọi GET /wishlist khi cần.
public record RemoveWishlistResponse(
    String message,
    boolean removed,
    Long tourId
) {
}
