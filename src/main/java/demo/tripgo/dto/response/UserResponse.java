package demo.tripgo.dto.response;

// Thông tin user trả ra client theo hợp đồng 6.2: { id, name, email, role }.
// role xuất dạng chữ thường ("user"/"admin") giống cách category/status của tour.
public record UserResponse(
    Long id,
    String name,
    String email,
    String role
) {
}
