# Tripgo

## Chạy local

Cần Java 21 và PostgreSQL theo cấu hình datasource trong
`src/main/resources/application.yaml`.

Trước lần chạy đầu tiên, sinh khóa JWT ngẫu nhiên và lưu vào file local
đã được gitignore (không ghi đè khóa đang sử dụng nếu không chủ động xoay khóa):

```bash
(umask 077; set -C; openssl rand -base64 32 | sed 's/^/export JWT_SECRET=/' > .env)
```

Nạp biến môi trường rồi khởi động (Spring Boot không tự đọc file `.env`):

```bash
source .env
./mvnw spring-boot:run
```

`JWT_SECRET` là bắt buộc, không có giá trị mặc định.
`JWT_EXPIRATION` tính bằng mili giây, mặc định `86400000` (24 giờ).
JwtService hiện dùng trực tiếp UTF-8 của chuỗi secret làm khóa; chuỗi Base64
từ lệnh trên được dùng nguyên vẹn, không giải mã Base64.

Khóa cũ từng commit phải coi là đã lộ. Mỗi môi trường cần khóa mới riêng,
được cấp qua biến môi trường hoặc secret manager và khởi động lại ứng dụng.
Sau khi đổi khóa, token cũ không còn hợp lệ; người dùng cần đăng nhập lại.
Không dùng khóa công khai của profile test cho môi trường chạy thật.

## Kiểm thử

Profile test có khóa riêng trong `src/test/resources/application-test.yaml`;
không cần đặt `JWT_SECRET` để chạy test.

```bash
./mvnw test
```

Trước khi commit, kiểm tra diff đã stage để phát hiện giá trị bí mật:

```bash
git diff --cached | grep -iE "secret|password|token|key"
```
