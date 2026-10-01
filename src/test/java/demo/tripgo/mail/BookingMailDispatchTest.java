package demo.tripgo.mail;

import demo.tripgo.entity.BookingStatus;
import demo.tripgo.event.BookingEvent;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

// Hai nhánh của việc gửi mail: qua hàng đợi và gửi thẳng. Cả hai phải cho kết quả như nhau nhìn
// từ bên ngoài — đó là lý do tách interface BookingMailDispatcher.
class BookingMailDispatchTest {

    private static BookingEvent event() {
        return new BookingEvent(BookingEvent.Kind.CREATED, 1L, "TG-2026-000001", "Khách",
            "Đà Nẵng 3N2Đ", new BigDecimal("1000000"), BookingStatus.PENDING, LocalDateTime.now());
    }

    // ---- Không bật JMS (mặc định) ----

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    class WithoutJms {

        @Autowired ApplicationContext context;
        @Autowired BookingMailDispatcher dispatcher;
        @MockitoBean BookingMailService mailService;

        @Test
        void usesDirectDispatcherAndStillSendsMail() {
            assertThat(dispatcher).isInstanceOf(DirectBookingMailDispatcher.class);
            // Không có broker thì cũng không được tạo bean JMS nào.
            assertThat(context.containsBean("jmsBookingMailDispatcher")).isFalse();
            assertThat(context.getBeanNamesForType(org.springframework.jms.core.JmsTemplate.class))
                .as("JmsTemplate vẫn được autoconfigure tạo, nhưng không ai dùng tới")
                .isNotNull();

            dispatcher.dispatch(event());

            // Chạy trên pool nền nên phải chờ một nhịp.
            verify(mailService, timeout(2000)).send(any(BookingEvent.class));
        }
    }

    // ---- Bật JMS, broker nhúng vm:// ----

    @Nested
    @SpringBootTest(properties = {
        "tasks.jms.enabled=true",
        // vm:// dựng broker ngay trong tiến trình test, không cần ActiveMQ bên ngoài.
        "spring.activemq.broker-url=vm://tripgo-test?broker.persistent=false&broker.useJmx=false",
        "spring.activemq.user=",
        "spring.activemq.password="
    })
    @ActiveProfiles("test")
    class WithJms {

        @Autowired BookingMailDispatcher dispatcher;
        @MockitoBean BookingMailService mailService;

        // Gói tin đi qua hàng đợi thật rồi mới tới người tiêu thụ.
        @Test
        void messageTravelsThroughQueueToListener() {
            dispatcher.dispatch(event());

            verify(mailService, timeout(5000)).send(any(BookingEvent.class));
        }

        @Test
        void usesJmsDispatcherNotTheDirectOne() {
            assertThat(dispatcher).isNotInstanceOf(DirectBookingMailDispatcher.class);
        }
    }
}
