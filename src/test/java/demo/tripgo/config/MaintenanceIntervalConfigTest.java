package demo.tripgo.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

// Nhịp chạy của hai job đọc từ biến môi trường trong .env. @Scheduled chỉ nhận chuỗi placeholder,
// nên kiểm đúng chuỗi mà nó sẽ nhận: gõ sai tên biến là job lặng lẽ quay về 1 giờ / 6 giờ.
class MaintenanceIntervalConfigTest {

    @Nested
    @SpringBootTest(properties = {
        "BOOKING_EXPIRE_CHECK_INTERVAL=PT2M",
        "RATING_REFRESH_INTERVAL=PT30S"
    })
    @ActiveProfiles("test")
    class FromEnvironment {

        @Autowired Environment environment;

        @Test
        void intervalsComeFromEnvVariables() {
            assertThat(delay(environment, "tasks.maintenance.cancel-expired-delay"))
                .isEqualTo(Duration.ofMinutes(2));
            assertThat(delay(environment, "tasks.maintenance.refresh-rating-delay"))
                .isEqualTo(Duration.ofSeconds(30));
        }
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    class Defaults {

        @Autowired Environment environment;

        @Test
        void keepsOriginalIntervalsWhenNotConfigured() {
            assertThat(delay(environment, "tasks.maintenance.cancel-expired-delay"))
                .isEqualTo(Duration.ofHours(1));
            assertThat(delay(environment, "tasks.maintenance.refresh-rating-delay"))
                .isEqualTo(Duration.ofHours(6));
        }
    }

    private static Duration delay(Environment environment, String key) {
        return Duration.parse(environment.getRequiredProperty(key));
    }
}
