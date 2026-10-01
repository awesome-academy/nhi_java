package demo.tripgo.config;

import demo.tripgo.entity.Role;
import demo.tripgo.entity.User;
import demo.tripgo.entity.UserStatus;
import demo.tripgo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Locale;

// POST /auth/register luôn tạo Role.USER nên không có đường nào tạo admin đầu tiên.
// Bean này tạo tài khoản đó từ ADMIN_EMAIL/ADMIN_PASSWORD lúc khởi động.
//
// Chỉ TẠO MỚI, không bao giờ sửa tài khoản đã có: nếu không, đổi biến môi trường sẽ âm thầm
// ghi đè mật khẩu admin đang dùng, và ai đọc được biến môi trường cũng chiếm được tài khoản.
@Configuration
public class AdminAccountSeeder {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountSeeder.class);

    @Bean
    public ApplicationRunner seedAdminAccount(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        @Value("${admin.seed.email:}") String email,
        @Value("${admin.seed.password:}") String password,
        @Value("${admin.seed.name:Quản trị viên}") String name
    ) {
        return args -> {
            if (email.isBlank() || password.isBlank()) {
                log.info("Bỏ qua tạo tài khoản admin: chưa đặt ADMIN_EMAIL/ADMIN_PASSWORD");
                return;
            }

            String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
            if (userRepository.existsByEmail(normalizedEmail)) {
                log.info("Tài khoản admin {} đã tồn tại, không tạo lại", normalizedEmail);
                return;
            }

            User admin = new User();
            admin.setEmail(normalizedEmail);
            admin.setFullName(name);
            admin.setPassword(passwordEncoder.encode(password));
            admin.setRole(Role.ADMIN);
            admin.setStatus(UserStatus.ACTIVE);
            userRepository.save(admin);

            log.info("Đã tạo tài khoản admin {}", normalizedEmail);
        };
    }
}
