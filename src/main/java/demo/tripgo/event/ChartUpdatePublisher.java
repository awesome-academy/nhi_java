package demo.tripgo.event;

import demo.tripgo.config.WebSocketConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

// Báo cho biểu đồ trên dashboard biết là có dữ liệu mới, qua STOMP topic /topic/chart-updates.
//
// Hai nguồn: đơn đặt thay đổi (nghe BookingEvent) và job nền chạy xong (MaintenanceJobs gọi
// jobFinished). Gửi hỏng chỉ ghi log: biểu đồ không cập nhật thì admin vẫn F5 được, còn đơn
// hàng đã lưu xong rồi, không được vì một thông báo mà hỏng theo.
@Component
public class ChartUpdatePublisher {

    private static final Logger log = LoggerFactory.getLogger(ChartUpdatePublisher.class);

    private final SimpMessagingTemplate messagingTemplate;

    public ChartUpdatePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    // AFTER_COMMIT như BookingNotifier: client nhận tín hiệu là gọi lại API ngay, nên dữ liệu phải
    // đã nằm trong DB. Nghe trước khi commit thì client có thể đọc trúng số liệu CŨ.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingEvent(BookingEvent event) {
        send(new ChartUpdate(ChartUpdate.Source.BOOKING, event.message(), LocalDateTime.now()));
    }

    // Job huỷ đơn chạy xong. Không huỷ đơn nào thì không báo: số liệu không đổi, báo chỉ làm
    // mọi client gọi lại API vô ích mỗi lần job chạy.
    public void jobFinished(String jobTitle, int changedCount, String summary) {
        if (changedCount <= 0) {
            return;
        }
        send(new ChartUpdate(ChartUpdate.Source.JOB, jobTitle + ": " + summary, LocalDateTime.now()));
    }

    private void send(ChartUpdate update) {
        try {
            messagingTemplate.convertAndSend(WebSocketConfig.TOPIC_CHART_UPDATES, update);
        } catch (RuntimeException exception) {
            log.warn("Không gửi được tín hiệu cập nhật biểu đồ: {}", exception.getMessage());
        }
    }
}
