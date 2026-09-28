package demo.tripgo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

// Kênh báo realtime cho khu quản trị.
//
// Endpoint nằm DƯỚI /admin nên đi qua chain bảo mật của khu quản trị: chỉ phiên đã đăng nhập với
// vai trò ADMIN mới bắt tay được. Đặt ở ngoài (vd /ws) thì phải mở công khai, và ai cũng nghe được
// thông tin đơn hàng của khách.
//
// Cố ý KHÔNG bật SockJS: SockJS dự phòng bằng các transport dùng POST, mà POST thì vướng CSRF của
// chain quản trị, phải mở ngoại lệ CSRF cho cả nhánh đó. WebSocket thuần chỉ cần một GET Upgrade
// nên không đụng tới CSRF, và mọi trình duyệt hiện nay đều hỗ trợ.
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    public static final String ENDPOINT = "/admin/ws";
    public static final String TOPIC_BOOKINGS = "/topic/bookings";

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(ENDPOINT);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Broker trong bộ nhớ là đủ: thông báo chỉ sống trong lúc admin đang mở trang, không cần
        // lưu lại hay phát lại. Cần bền vững thì mới phải gắn broker ngoài (RabbitMQ/ActiveMQ).
        registry.enableSimpleBroker("/topic");
    }
}
