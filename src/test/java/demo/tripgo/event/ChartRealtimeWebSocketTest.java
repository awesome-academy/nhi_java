package demo.tripgo.event;

import demo.tripgo.TestDataCleaner;
import demo.tripgo.dto.request.ContactRequest;
import demo.tripgo.dto.request.CreateBookingRequest;
import demo.tripgo.entity.Category;
import demo.tripgo.entity.Departure;
import demo.tripgo.entity.Destination;
import demo.tripgo.entity.Role;
import demo.tripgo.entity.Tour;
import demo.tripgo.entity.User;
import demo.tripgo.entity.UserStatus;
import demo.tripgo.repository.CategoryRepository;
import demo.tripgo.repository.DepartureRepository;
import demo.tripgo.repository.DestinationRepository;
import demo.tripgo.repository.TourRepository;
import demo.tripgo.repository.UserRepository;
import demo.tripgo.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

// Đi trọn đường thật, không mock: đăng nhập admin bằng form -> bắt tay WebSocket /admin/ws bằng
// cookie phiên (qua chain bảo mật /admin/**) -> subscribe /topic/chart-updates -> tạo đơn -> nhận
// tín hiệu. ChartRealtimeIntegrationTest kiểm KHI NÀO gửi; test này kiểm gói tin thật sự tới được
// trình duyệt của admin (security, broker, chuyển JSON).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChartRealtimeWebSocketTest {

    private static final String ADMIN_EMAIL = "ws-admin@tripgo.local";
    private static final String ADMIN_PASSWORD = "MatKhau123";

    @LocalServerPort int port;
    @Autowired TestDataCleaner cleaner;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired DestinationRepository destinations;
    @Autowired CategoryRepository categories;
    @Autowired TourRepository tours;
    @Autowired DepartureRepository departures;
    @Autowired BookingService bookingService;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @Test
    void adminBrowserReceivesChartUpdateWhenBookingIsCreated() throws Exception {
        cleaner.clean();
        saveUser(ADMIN_EMAIL, passwordEncoder.encode(ADMIN_PASSWORD), Role.ADMIN);
        User customer = saveUser("khach@example.com", "x", Role.USER);
        Departure departure = saveDeparture();

        BlockingQueue<Map<?, ?>> received = new LinkedBlockingQueue<>();
        StompSession session = connect(loginAsAdmin());
        session.subscribe("/topic/chart-updates", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((Map<?, ?>) payload);
            }
        });
        // subscribe là bất đồng bộ: chờ broker ghi nhận đăng ký trước khi phát sự kiện.
        Thread.sleep(300);

        bookingService.createBooking(customer, new CreateBookingRequest(
            departure.getTour().getId(), departure.getDepartureDate(), 1, 0,
            new ContactRequest("Khách", "khach@example.com", "0900000000", null)));

        Map<?, ?> update = received.poll(5, TimeUnit.SECONDS);
        assertThat(update).as("không nhận được tín hiệu qua WebSocket").isNotNull();
        assertThat(update.get("source")).isEqualTo("BOOKING");
        assertThat((String) update.get("message")).startsWith("Đơn mới TG-");
        session.disconnect();
    }

    // Form login thật: lấy token CSRF trên trang đăng nhập, POST, giữ cookie phiên mới
    // (Spring đổi session id sau khi đăng nhập để chống session fixation).
    private String loginAsAdmin() throws Exception {
        String base = "http://localhost:" + port;
        HttpResponse<String> page = http.send(
            HttpRequest.newBuilder(URI.create(base + "/admin/login")).build(), HttpResponse.BodyHandlers.ofString());
        String cookie = sessionCookie(page);
        Matcher csrf = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body());
        assertThat(csrf.find()).isTrue();

        String form = "email=" + encode(ADMIN_EMAIL) + "&password=" + encode(ADMIN_PASSWORD)
            + "&_csrf=" + encode(csrf.group(1));
        HttpResponse<String> login = http.send(HttpRequest.newBuilder(URI.create(base + "/admin/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cookie", cookie)
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build(),
            HttpResponse.BodyHandlers.ofString());
        assertThat(login.headers().firstValue("location")).hasValue(base + "/admin");
        return sessionCookie(login);
    }

    private StompSession connect(String sessionCookie) throws Exception {
        WebSocketStompClient stomp = new WebSocketStompClient(new StandardWebSocketClient());
        stomp.setMessageConverter(new JacksonJsonMessageConverter());
        WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
        handshake.add("Cookie", sessionCookie);
        return stomp.connectAsync("ws://localhost:" + port + "/admin/ws", handshake, new StompHeaders(),
            new StompSessionHandlerAdapter() { }).get(5, TimeUnit.SECONDS);
    }

    private static String sessionCookie(HttpResponse<?> response) {
        return response.headers().firstValue("set-cookie").orElseThrow().split(";")[0];
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private User saveUser(String email, String password, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setFullName("Tài khoản " + role);
        user.setPassword(password);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return users.save(user);
    }

    private Departure saveDeparture() {
        Destination destination = new Destination();
        destination.setName("Đà Nẵng");
        destination.setSlug("da-nang");
        destination = destinations.save(destination);

        Category category = new Category();
        category.setSlug("beach");
        category.setName("Biển đảo");
        category = categories.save(category);

        Tour tour = new Tour();
        tour.setTitle("Đà Nẵng 3N2Đ");
        tour.setSlug("da-nang-3n2d");
        tour.setDestination(destination);
        tour.setCategory(category);
        tour.setDurationDays(3);
        tour.setPrice(new BigDecimal("1000000"));
        tour.setMaxGuests(20);
        tour = tours.save(tour);

        Departure departure = new Departure();
        departure.setTour(tour);
        departure.setDepartureDate(LocalDate.now().plusDays(30));
        departure.setTotalSeats(20);
        departure.setBookedSeats(0);
        return departures.save(departure);
    }
}
