package demo.tripgo.admin;

import demo.tripgo.entity.AuthProvider;
import demo.tripgo.entity.Role;
import demo.tripgo.entity.UserStatus;

import java.time.LocalDateTime;

// Một dòng trong bảng Người dùng. provider cho admin biết tài khoản này đăng nhập bằng gì —
// tài khoản Facebook không có mật khẩu, nên cấp quyền cho nó thì admin đó chỉ vào bằng Facebook.
public record AdminUserRow(
    Long id,
    String fullName,
    String email,
    AuthProvider provider,
    Role role,
    UserStatus status,
    LocalDateTime createdAt
) {

    public boolean admin() {
        return role == Role.ADMIN;
    }

    public boolean facebookAccount() {
        return provider == AuthProvider.FACEBOOK;
    }
}
