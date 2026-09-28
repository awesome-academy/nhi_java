package demo.tripgo.config;

import demo.tripgo.event.BookingEvent;
import demo.tripgo.mail.BookingMailDispatcher;
import demo.tripgo.mail.BookingMailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

// Gửi mail qua hàng đợi thay vì gọi thẳng.
//
// Cả cụm chỉ được tạo khi tasks.jms.enabled=true. Mặc định TẮT: có dependency ActiveMQ trên
// classpath mà không có broker thì listener sẽ thử kết nối lại liên tục và làm log đầy lỗi —
// môi trường dev và test không cần dựng broker chỉ để chạy ứng dụng.
//
// Khi tắt, DirectBookingMailDispatcher làm thay nên việc gửi mail không biến mất.
@Configuration
@EnableJms
@ConditionalOnProperty(prefix = "tasks.jms", name = "enabled", havingValue = "true")
public class JmsConfig {

    public static final String BOOKING_MAIL_QUEUE = "tripgo.booking.mail";

    @Bean
    public BookingMailDispatcher jmsBookingMailDispatcher(
        JmsTemplate jmsTemplate, ObjectMapper objectMapper
    ) {
        return new JmsBookingMailDispatcher(jmsTemplate, objectMapper);
    }

    // Gửi dạng chuỗi JSON tự serialize thay vì dùng message converter có sẵn: gói tin đi qua hàng
    // đợi có thể được đọc lại bởi phiên bản ứng dụng khác, nên định dạng phải là thứ mình kiểm
    // soát được chứ không phụ thuộc vào cách một thư viện tuần tự hoá đối tượng Java.
    static class JmsBookingMailDispatcher implements BookingMailDispatcher {

        private static final Logger log = LoggerFactory.getLogger(JmsBookingMailDispatcher.class);

        private final JmsTemplate jmsTemplate;
        private final ObjectMapper objectMapper;

        JmsBookingMailDispatcher(JmsTemplate jmsTemplate, ObjectMapper objectMapper) {
            this.jmsTemplate = jmsTemplate;
            this.objectMapper = objectMapper;
        }

        @Override
        public void dispatch(BookingEvent event) {
            try {
                jmsTemplate.convertAndSend(BOOKING_MAIL_QUEUE, objectMapper.writeValueAsString(event));
            } catch (RuntimeException exception) {
                // Broker chết không được làm hỏng việc đặt tour: đơn đã lưu xong rồi.
                log.warn("Không đẩy được mail của đơn {} vào hàng đợi: {}",
                    event.code(), exception.getMessage());
            }
        }
    }

    // Người tiêu thụ hàng đợi. Ném lỗi thì message quay lại hàng đợi để thử lại — đó là lý do
    // chính dùng hàng đợi thay vì gọi thẳng: máy chủ mail chập chờn thì việc không mất.
    @Component
    @ConditionalOnProperty(prefix = "tasks.jms", name = "enabled", havingValue = "true")
    static class BookingMailListener {

        private final BookingMailService mailService;
        private final ObjectMapper objectMapper;

        BookingMailListener(BookingMailService mailService, ObjectMapper objectMapper) {
            this.mailService = mailService;
            this.objectMapper = objectMapper;
        }

        @JmsListener(destination = BOOKING_MAIL_QUEUE)
        public void onMessage(String payload) {
            mailService.send(objectMapper.readValue(payload, BookingEvent.class));
        }
    }
}
