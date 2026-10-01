-- Chuẩn bị bảng users cho đăng nhập Facebook.
--
-- BẮT BUỘC chạy trên database đã có dữ liệu. Lý do: ddl-auto=update của Hibernate chỉ THÊM được
-- thứ mới, nó không:
--   - thêm được cột NOT NULL vào bảng đã có dòng (Postgres từ chối vì dòng cũ mang NULL),
--   - gỡ được ràng buộc NOT NULL khỏi cột password.
-- Hibernate gặp lỗi DDL chỉ ghi WARN rồi chạy tiếp, nên ứng dụng vẫn khởi động nhưng mọi truy vấn
-- trên users sẽ hỏng — triệu chứng là không đăng nhập được mà log không báo gì rõ ràng.
--
-- Chạy:  psql -h localhost -p 5432 -U postgres -d tripgo -f scripts/migrate-social-login.sql

BEGIN;

-- 1) Tài khoản Facebook không có mật khẩu.
ALTER TABLE users ALTER COLUMN password DROP NOT NULL;

-- 2) Nguồn gốc tài khoản. Thêm cột cho phép NULL trước, điền dữ liệu cũ, rồi mới siết NOT NULL.
ALTER TABLE users ADD COLUMN IF NOT EXISTS provider VARCHAR(20);
ALTER TABLE users ADD COLUMN IF NOT EXISTS provider_id VARCHAR(255);

UPDATE users SET provider = 'LOCAL' WHERE provider IS NULL;

-- 3) Một tài khoản Facebook chỉ gắn được vào đúng một user. Postgres coi các NULL là khác nhau
--    nên mọi user LOCAL (provider_id = NULL) không đụng ràng buộc này.
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_provider
    ON users (provider, provider_id);

COMMIT;

-- Kiểm tra lại
SELECT provider, count(*) FROM users GROUP BY provider;
