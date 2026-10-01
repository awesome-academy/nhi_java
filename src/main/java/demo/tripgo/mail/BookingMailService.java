package demo.tripgo.mail;

import demo.tripgo.event.BookingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Việc thật: soạn và gửi mail cho khách.
//
// Hiện chỉ ghi log — TripGo chưa nối máy chủ mail. Tách thành service riêng để khi nối thật
// (JavaMailSender, SendGrid...) chỉ phải sửa đúng chỗ này, còn phần hàng đợi giữ nguyên.
@Service
public class BookingMailService {

    private static final Logger log = LoggerFactory.getLogger(BookingMailService.class);

    public void send(BookingEvent event) {
        log.info("[MAIL] gửi tới {} — {} (đơn {}, tour {})",
            event.customerName(), subjectFor(event), event.code(), event.tourTitle());
    }

    private String subjectFor(BookingEvent event) {
        return switch (event.kind()) {
            case CREATED -> "TripGo đã nhận đơn đặt tour của bạn";
            case CONFIRMED -> "Đơn đặt tour của bạn đã được xác nhận";
            case CANCELLED -> "Đơn đặt tour của bạn đã bị huỷ";
        };
    }
}
