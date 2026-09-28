package demo.tripgo.mail;

import demo.tripgo.event.BookingEvent;

// Đưa một việc gửi mail đi xử lý. Có hai bản cài: qua hàng đợi JMS, hoặc chạy nền trực tiếp.
//
// Tách interface để phần còn lại của ứng dụng không cần biết có broker hay không — bật/tắt JMS
// chỉ là đổi cấu hình, hành vi nhìn từ ngoài vẫn y hệt.
public interface BookingMailDispatcher {

    void dispatch(BookingEvent event);
}
