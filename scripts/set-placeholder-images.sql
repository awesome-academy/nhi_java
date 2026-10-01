-- Gán ảnh đã tải lên làm ảnh hiển thị, thay cho các link seed chết (https://img/...).
--
-- Bối cảnh: dữ liệu mẫu ban đầu trỏ tới https://img/<ten>.jpg — tên miền không tồn tại, nên
-- mọi ảnh trong ứng dụng đều vỡ. Script này trỏ chúng về file thật trong thư mục uploads.
--
-- Chạy:  psql -h localhost -p 5432 -U postgres -d tripgo -f scripts/set-placeholder-images.sql
--
-- Yêu cầu: các file tương ứng phải nằm sẵn trong thư mục UPLOAD_DIR (mặc định ./uploads),
-- vì ứng dụng phục vụ chúng qua ResourceHandler ánh xạ /uploads/** -> thư mục đó.

BEGIN;

-- 1) Ảnh đại diện của tour (55 dòng)
UPDATE tours
   SET thumbnail_url = '/uploads/dulich.jpg'
 WHERE deleted_at IS NULL
   AND (thumbnail_url IS NULL OR thumbnail_url LIKE 'https://img/%');

-- 2) Ảnh điểm đến (8 dòng)
UPDATE destinations
   SET image = '/uploads/anh1.png'
 WHERE deleted_at IS NULL
   AND (image IS NULL OR image LIKE 'https://img/%');

-- 3) Ảnh gallery của tour (165 dòng)
UPDATE tour_images
   SET url = '/uploads/anh2.webp'
 WHERE url LIKE 'https://img/%';

COMMIT;

-- Kiểm tra lại: không còn link https://img/ nào sót
SELECT 'tours' AS bang, count(*) FILTER (WHERE thumbnail_url LIKE 'https://img/%') AS con_link_hong FROM tours
UNION ALL SELECT 'destinations', count(*) FILTER (WHERE image LIKE 'https://img/%') FROM destinations
UNION ALL SELECT 'tour_images', count(*) FILTER (WHERE url LIKE 'https://img/%') FROM tour_images;
