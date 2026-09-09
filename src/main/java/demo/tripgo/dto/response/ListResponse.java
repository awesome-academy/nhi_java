package demo.tripgo.dto.response;

import java.util.List;

// Bọc danh sách không phân trang trong object { data: [...] } để mọi response đều là JSON object,
// đồng nhất với phần còn lại của API và dễ mở rộng thêm field sau này.
public record ListResponse<T>(List<T> data) {

    public static <T> ListResponse<T> of(List<T> data) {
        return new ListResponse<>(data);
    }
}
