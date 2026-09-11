package demo.tripgo.dto.response;

import java.util.List;

// Mọi thao tác wishlist đều trả về trạng thái hiện tại của danh sách (hợp đồng 6.6),
// để client không phải gọi thêm GET sau mỗi lần thêm/xoá.
public record WishlistResponse(
    List<Long> tourIds
) {
}
