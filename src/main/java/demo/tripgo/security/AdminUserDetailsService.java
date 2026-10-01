package demo.tripgo.security;

import demo.tripgo.entity.User;
import demo.tripgo.entity.UserStatus;
import demo.tripgo.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

// Form login của khu quản trị cần một UserDetailsService để đọc bảng users; API dùng JWT nên
// trước đây không có bean nào, khiến Spring Boot tự tạo user mặc định (dòng "Using generated
// security password" lúc khởi động). Có bean này là hết cảnh báo đó.
//
// Ở đây KHÔNG chặn theo vai trò: mọi tài khoản đều xác thực được, còn quyền vào /admin do
// hasRole("ADMIN") trong SecurityConfig quyết định — tách xác thực khỏi phân quyền.
@Service
public class AdminUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AdminUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
            .orElseThrow(() -> new UsernameNotFoundException("Email hoặc mật khẩu không đúng"));

        return org.springframework.security.core.userdetails.User
            .withUsername(user.getEmail())
            .password(user.getPassword())
            .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
            // Tài khoản bị khoá/chưa kích hoạt không đăng nhập được vào khu quản trị.
            .disabled(user.getStatus() != UserStatus.ACTIVE)
            .build();
    }
}
