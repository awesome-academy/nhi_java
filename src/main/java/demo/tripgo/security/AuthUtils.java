package demo.tripgo.security;

import demo.tripgo.entity.User;
import demo.tripgo.exception.InvalidCredentialsException;

// Tiện ích dùng chung cho các endpoint cần user đã đăng nhập.
public final class AuthUtils {

    private AuthUtils() {
    }

    // AuthenticationPrincipalArgumentResolver trả null (không ném) nếu principal không phải kiểu User.
    // Chặn tại đây để trả 401 rõ ràng thay vì để NPE lọt ra ngoài.
    public static User requireUser(User user) {
        if (user == null) {
            throw new InvalidCredentialsException("Vui lòng đăng nhập để tiếp tục");
        }
        return user;
    }
}
