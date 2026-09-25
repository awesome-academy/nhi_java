package demo.tripgo.admin;

import java.time.LocalDateTime;

// Một dòng trong thùng rác. Map sang record ngay trong transaction thay vì đẩy entity ra template,
// để view không vô tình chạm vào quan hệ lazy sau khi session đã đóng.
public record TrashTourRow(
    Long id,
    String title,
    String destination,
    LocalDateTime deletedAt
) {
}
