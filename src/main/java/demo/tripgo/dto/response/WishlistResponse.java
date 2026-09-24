package demo.tripgo.dto.response;

import java.util.List;

// GET và POST trả về trạng thái hiện tại của danh sách, để client không phải gọi thêm GET
// sau khi thêm. Riêng DELETE trả RemoveWishlistResponse (kết quả xoá), không trả danh sách.
public record WishlistResponse(
    List<Long> tourIds
) {
}
