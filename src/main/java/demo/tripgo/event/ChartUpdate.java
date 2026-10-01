package demo.tripgo.event;

import java.time.LocalDateTime;

// Tín hiệu "dữ liệu biểu đồ vừa đổi" gửi qua WebSocket tới /topic/chart-updates.
//
// Cố ý KHÔNG mang số liệu: mỗi admin có thể đang xem một tháng khác nhau, nên client nhận tín hiệu
// rồi tự gọi lại GET /admin/reports/chart-data cho đúng tháng mình đang xem. Gói tin nhỏ, và chỉ có
// một nơi tính số liệu tổng hợp.
public record ChartUpdate(Source source, String message, LocalDateTime at) {

    public enum Source {
        // Có đơn được tạo / xác nhận / huỷ.
        BOOKING,
        // Job nền chạy xong và có làm đổi dữ liệu.
        JOB
    }
}
