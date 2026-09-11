package demo.tripgo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Giới hạn số request POST /auth/** theo IP để chống brute-force (login/register).
// Fixed-window in-memory; vượt ngưỡng trả 429 theo format lỗi thống nhất.
// Chạy sớm (trước Spring Security) để chặn trước khi tốn công xác thực.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private final SecurityErrorResponder securityErrorResponder;
    private final int maxRequests;
    private final long windowMillis;
    private final Map<String, Window> counters = new ConcurrentHashMap<>();

    public RateLimitFilter(
        SecurityErrorResponder securityErrorResponder,
        @Value("${ratelimit.auth.max-requests:20}") int maxRequests,
        @Value("${ratelimit.auth.window-seconds:60}") long windowSeconds
    ) {
        this.securityErrorResponder = securityErrorResponder;
        this.maxRequests = maxRequests;
        this.windowMillis = windowSeconds * 1000L;
    }

    // Chỉ áp cho POST /auth/** (login/register); còn lại bỏ qua.
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !("POST".equalsIgnoreCase(request.getMethod())
            && path != null && path.startsWith("/auth/"));
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (!allow(clientKey(request))) {
            securityErrorResponder.write(response, 429,
                "Too many requests, please try again later");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean allow(String key) {
        long now = System.currentTimeMillis();
        Window window = counters.computeIfAbsent(key, k -> new Window());
        synchronized (window) {
            if (now - window.windowStart >= windowMillis) {
                window.windowStart = now;
                window.count = 1;
                return true;
            }
            if (window.count < maxRequests) {
                window.count++;
                return true;
            }
            return false;
        }
    }

    private String clientKey(HttpServletRequest request) {
        // Ưu tiên X-Forwarded-For nếu chạy sau proxy/load balancer.
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static final class Window {
        private long windowStart = System.currentTimeMillis();
        private int count;
    }
}
