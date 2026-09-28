package demo.tripgo.event;

import demo.tripgo.config.WebSocketConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Đẩy sự kiện đơn đặt xuống trình duyệt của admin đang mở trang.
//
// AFTER_COMMIT chứ không phải @EventListener thường: nghe ngay lúc phát sự kiện thì transaction
// còn chưa commit, một lỗi sau đó làm rollback sẽ để lại thông báo về một đơn không hề tồn tại.
@Component
public class BookingNotifier {

    private static final Logger log = LoggerFactory.getLogger(BookingNotifier.class);

    private final SimpMessagingTemplate messagingTemplate;

    public BookingNotifier(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingEvent(BookingEvent event) {
        try {
            messagingTemplate.convertAndSend(WebSocketConfig.TOPIC_BOOKINGS, event);
        } catch (RuntimeException exception) {
            // Thông báo hỏng không được làm hỏng nghiệp vụ: đơn đã lưu xong rồi. Chỉ ghi log.
            log.warn("Không gửi được thông báo cho đơn {}: {}",
                event.code(), exception.getMessage());
        }
    }
}
