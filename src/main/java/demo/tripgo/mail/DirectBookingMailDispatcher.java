package demo.tripgo.mail;

import demo.tripgo.config.AsyncConfig;
import demo.tripgo.event.BookingEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

// Bản dự phòng khi KHÔNG bật JMS: gửi thẳng, nhưng vẫn chạy trên pool nền nên request của khách
// không phải chờ gửi mail xong.
//
// Nhờ có bản này mà môi trường dev và test không cần dựng broker, và việc gửi mail không lặng lẽ
// biến mất khi ai đó quên bật JMS.
@Component
@ConditionalOnMissingBean(name = "jmsBookingMailDispatcher")
public class DirectBookingMailDispatcher implements BookingMailDispatcher {

    private final BookingMailService mailService;

    public DirectBookingMailDispatcher(BookingMailService mailService) {
        this.mailService = mailService;
    }

    @Override
    @Async(AsyncConfig.TASK_EXECUTOR)
    public void dispatch(BookingEvent event) {
        mailService.send(event);
    }
}
