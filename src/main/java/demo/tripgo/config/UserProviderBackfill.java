package demo.tripgo.config;

import demo.tripgo.entity.AuthProvider;
import demo.tripgo.entity.User;
import demo.tripgo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Điền provider = LOCAL cho tài khoản có từ trước khi thêm cột.
//
// scripts/migrate-social-login.sql cũng làm việc này, nhưng chạy script là thao tác thủ công dễ
// quên; bean này bảo đảm dữ liệu luôn nhất quán kể cả khi ai đó bỏ qua bước đó.
// Idempotent: chỉ đụng hàng đang NULL.
@Configuration
public class UserProviderBackfill {

    private static final Logger log = LoggerFactory.getLogger(UserProviderBackfill.class);

    @Bean
    public ApplicationRunner backfillUserProvider(UserRepository userRepository) {
        return args -> run(userRepository);
    }

    @Transactional
    void run(UserRepository userRepository) {
        List<User> users = userRepository.findByProviderIsNull();
        if (users.isEmpty()) {
            return;
        }
        users.forEach(user -> user.setProvider(AuthProvider.LOCAL));
        userRepository.saveAll(users);
        log.info("Đã điền provider = LOCAL cho {} tài khoản cũ", users.size());
    }
}
