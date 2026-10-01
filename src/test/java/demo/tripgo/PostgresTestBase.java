package demo.tripgo;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// Nền cho các test phải chạy trên Postgres THẬT thay vì H2.
//
// Vì sao cần: H2 (kể cả ở MODE=PostgreSQL) chấp nhận nhiều thứ Postgres từ chối. Một bug đã lọt
// qua đúng vì chuyện này — truy vấn có tham số null khiến Postgres bind thành bytea rồi ném
// "function lower(bytea) does not exist", trong khi toàn bộ test H2 vẫn xanh.
//
// Không chuyển CẢ bộ test sang Postgres: container nặng hơn H2 nhiều, mà phần lớn test chỉ kiểm
// logic nghiệp vụ chứ không phụ thuộc phương ngữ SQL. Chỉ những test đụng tới truy vấn viết tay
// mới kế thừa lớp này.
//
// @Container tĩnh + không withReuse: container khởi động một lần cho cả class, dọn sau khi xong.
@Testcontainers
@ActiveProfiles("postgres-test")
public abstract class PostgresTestBase {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
        // Trùng phiên bản với docker-compose.yml để test chạy đúng thứ production dùng.
        new PostgreSQLContainer<>("postgres:16-alpine");
}
