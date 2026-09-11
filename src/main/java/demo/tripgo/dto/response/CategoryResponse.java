package demo.tripgo.dto.response;

// Một loại hình tour cho dropdown lọc (hợp đồng 6.4): slug để gửi lên ?category=,
// name là nhãn tiếng Việt để hiển thị.
public record CategoryResponse(
    String slug,
    String name
) {
}
