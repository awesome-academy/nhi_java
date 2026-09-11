-- Dữ liệu mẫu để thử GET /tours và GET /tours/{slug}. Toàn bộ nội dung bằng tiếng Việt.
-- Chạy: PGPASSWORD=postgres psql -h localhost -U postgres -d tripgo -f scripts/seed-tours.sql
-- Chạy lại nhiều lần an toàn: xoá sạch dữ liệu tour trước khi seed.

-- ddl-auto:update không cập nhật check constraint của enum khi thêm giá trị mới (vd BookingStatus.PENDING).
-- Gỡ constraint cũ để tránh lỗi "bookings_status_check" trên DB dev đã tồn tại từ trước (idempotent).
ALTER TABLE IF EXISTS bookings DROP CONSTRAINT IF EXISTS bookings_status_check;

TRUNCATE reviews, tour_departures, tour_images, tour_itinerary_days, tour_highlights, tour_included,
    tour_excluded, tours, destinations RESTART IDENTITY CASCADE;

-- Tên hiển thị có dấu; slug giữ dạng không dấu vì đó là thứ client gửi lên khi lọc (?destination=da-nang).
INSERT INTO destinations (name, slug, image) VALUES
    ('Đà Nẵng',   'da-nang',   'https://img/diem-den/da-nang.jpg'),
    ('Hà Nội',    'ha-noi',    'https://img/diem-den/ha-noi.jpg'),
    ('Sa Pa',     'sa-pa',     'https://img/diem-den/sa-pa.jpg'),
    ('Hội An',    'hoi-an',    'https://img/diem-den/hoi-an.jpg'),
    ('Nha Trang', 'nha-trang', 'https://img/diem-den/nha-trang.jpg'),
    ('Phú Quốc',  'phu-quoc',  'https://img/diem-den/phu-quoc.jpg'),
    ('Đà Lạt',    'da-lat',    'https://img/diem-den/da-lat.jpg'),
    ('Huế',       'hue',       'https://img/diem-den/hue.jpg');

-- Nhiều tour Đà Nẵng với giá/đánh giá/thời lượng khác nhau để thử lọc + sắp xếp + phân trang.
INSERT INTO tours (title, destination_id, category, duration_days, price, discount_price,
                   rating_avg, review_count, max_guests, thumbnail_url, description, created_at, updated_at)
VALUES
    ('Nghỉ dưỡng biển Đà Nẵng', (SELECT id FROM destinations WHERE slug='da-nang'),
     'BEACH', 3, 2500000, 1990000, 4.8, 120, 20, 'https://img/dn-beach.jpg',
     'Thư giãn tại bãi biển Mỹ Khê, ngắm bình minh và thưởng thức hải sản tươi sống.', now(), now()),
    ('Khám phá thành phố Đà Nẵng', (SELECT id FROM destinations WHERE slug='da-nang'),
     'CITY', 2, 1500000, NULL, 4.2, 45, 15, 'https://img/dn-city.jpg',
     'Dạo Cầu Rồng về đêm và thưởng thức ẩm thực đường phố miền Trung.', now(), now()),
    ('Trekking Bà Nà Hills', (SELECT id FROM destinations WHERE slug='da-nang'),
     'TREKKING', 4, 3200000, 2990000, 4.6, 88, 12, 'https://img/dn-bana.jpg',
     'Chinh phục Cầu Vàng và cung đường trekking giữa rừng già Bà Nà.', now(), now()),
    ('Nghỉ dưỡng núi Sa Pa', (SELECT id FROM destinations WHERE slug='sa-pa'),
     'MOUNTAIN', 5, 4200000, NULL, 4.9, 210, 10, 'https://img/sapa.jpg',
     'Đỉnh Fansipan hùng vĩ và những thửa ruộng bậc thang mùa lúa chín.', now(), now()),
    ('Dạo bộ phố cổ Hà Nội', (SELECT id FROM destinations WHERE slug='ha-noi'),
     'CITY', 1, 800000, NULL, 4.0, 30, 25, 'https://img/hanoi.jpg',
     'Tour đi bộ khám phá 36 phố phường và văn hoá phố cổ Hà Nội.', now(), now());

-- Ngày khởi hành để thử GET /tours/{id}/availability (ngày tương đối so với hôm nay).
INSERT INTO tour_departures (tour_id, departure_date, total_seats, booked_seats) VALUES
    ((SELECT id FROM tours WHERE title='Nghỉ dưỡng biển Đà Nẵng'), CURRENT_DATE + 7,  20, 5),
    ((SELECT id FROM tours WHERE title='Nghỉ dưỡng biển Đà Nẵng'), CURRENT_DATE + 14, 20, 18),
    ((SELECT id FROM tours WHERE title='Nghỉ dưỡng biển Đà Nẵng'), CURRENT_DATE + 30, 20, 0),
    ((SELECT id FROM tours WHERE title='Nghỉ dưỡng núi Sa Pa'),    CURRENT_DATE + 10, 12, 4);

-- ============================================================================
-- Dữ liệu bổ sung (~50 tour) phủ các case đặc biệt như trong test.
-- ============================================================================

-- 1) Tour edge-case: ký tự wildcard _ và % NẰM TRONG tiêu đề (test escape LIKE) — phải giữ nguyên,
--    đủ 6 category, biên giá (1tr/5tr), rating cao/thấp/đúng-biên-4.5, có/không discount.
INSERT INTO tours (title, destination_id, category, duration_days, price, discount_price,
                   rating_avg, review_count, max_guests, thumbnail_url, description, created_at, updated_at)
VALUES
    ('Beach_Villa Hội An',              (SELECT id FROM destinations WHERE slug='hoi-an'),   'BEACH',    3, 2200000, 1990000, 4.7, 60,  18, 'https://img/e1.jpg', 'Tiêu đề chứa dấu gạch dưới để kiểm tra escape LIKE.', now(), now()),
    ('Dive_Snorkel Nha Trang',          (SELECT id FROM destinations WHERE slug='nha-trang'),'BEACH',    2, 1800000, NULL,    4.3, 40,  16, 'https://img/e2.jpg', 'Một tiêu đề chứa gạch dưới khác.', now(), now()),
    ('Giảm 50% tour hè Phú Quốc',       (SELECT id FROM destinations WHERE slug='phu-quoc'), 'BEACH',    4, 3000000, 1500000, 4.6, 75,  22, 'https://img/e3.jpg', 'Tiêu đề chứa dấu phần trăm để kiểm tra escape LIKE.', now(), now()),
    ('100% thiên nhiên hoang dã Sa Pa', (SELECT id FROM destinations WHERE slug='sa-pa'),    'TREKKING', 6, 3500000, NULL,    4.9, 150, 12, 'https://img/e4.jpg', 'Một tiêu đề chứa phần trăm khác.', now(), now()),
    ('Du thuyền Hạ Long cao cấp',       (SELECT id FROM destinations WHERE slug='ha-noi'),   'CRUISE',   2, 4800000, 4200000, 4.8, 95,  30, 'https://img/e5.jpg', 'Mẫu cho category CRUISE.', now(), now()),
    ('Văn hoá cung đình Huế',           (SELECT id FROM destinations WHERE slug='hue'),      'CULTURAL', 2, 1200000, NULL,    4.1, 33,  25, 'https://img/e6.jpg', 'Mẫu cho category CULTURAL.', now(), now()),
    ('Tour trong ngày tiết kiệm Đà Lạt',(SELECT id FROM destinations WHERE slug='da-lat'),   'CITY',     1, 1000000, NULL,    3.5, 12,  20, 'https://img/e7.jpg', 'Biên giá 1.000.000 và thời lượng 1 ngày.', now(), now()),
    ('Hành trình Fansipan dài ngày',    (SELECT id FROM destinations WHERE slug='sa-pa'),    'MOUNTAIN',14, 5000000, NULL,    5.0, 200, 10, 'https://img/e8.jpg', 'Biên giá 5.000.000, thời lượng dài, đánh giá 5.0.', now(), now()),
    ('Viên ngọc ẩn Hội An',             (SELECT id FROM destinations WHERE slug='hoi-an'),   'CULTURAL', 3, 2600000, NULL,    5.0, 300, 15, 'https://img/e9.jpg', 'Đánh giá cao nhất.', now(), now()),
    ('Tour mẫu đánh giá thấp Hà Nội',   (SELECT id FROM destinations WHERE slug='ha-noi'),   'CITY',     2, 900000,  NULL,    2.0, 5,   25, 'https://img/e10.jpg','Mẫu đánh giá thấp.', now(), now()),
    ('Tĩnh dưỡng Đà Lạt chưa mở chuyến',(SELECT id FROM destinations WHERE slug='da-lat'),   'MOUNTAIN', 3, 2100000, NULL,    4.4, 20,  14, 'https://img/e11.jpg','Cố ý không seed ngày khởi hành -> availability rỗng.', now(), now()),
    ('Lễ hội hoa Đà Lạt',               (SELECT id FROM destinations WHERE slug='da-lat'),   'CULTURAL', 2, 1600000, 1400000, 4.5, 55,  28, 'https://img/e12.jpg','Đánh giá đúng biên 4.5.', now(), now());

-- 2) Bulk 38 tour trải đều category/điểm đến/giá/thời lượng/rating để test lọc + sắp xếp + phân trang.
--    created_at lệch nhau để sort=newest có ý nghĩa.
INSERT INTO tours (title, destination_id, category, duration_days, price, discount_price,
                   rating_avg, review_count, max_guests, thumbnail_url, description, created_at, updated_at)
SELECT
    'Gói khám phá ' || g,
    (SELECT id FROM destinations OFFSET (g % (SELECT count(*) FROM destinations)) LIMIT 1),
    (ARRAY['BEACH','MOUNTAIN','CITY','TREKKING','CRUISE','CULTURAL'])[1 + (g % 6)],
    1 + (g % 12),
    500000 + (g % 19) * 250000,
    CASE WHEN g % 3 = 0 THEN 500000 + (g % 19) * 250000 - 150000 ELSE NULL END,
    round((2.6 + (g % 25) * 0.1)::numeric, 1),
    g % 40,
    10 + (g % 15),
    'https://img/pkg-' || g || '.jpg',
    'Gói tour mẫu số ' || g || ' dùng để kiểm thử bộ lọc, sắp xếp và phân trang.',
    now() - ((g) || ' hours')::interval,
    now()
FROM generate_series(1, 38) AS g;

-- 3) Ngày khởi hành cho một số tour: hiện tại/tương lai/quá khứ + tour cố tình không có.
INSERT INTO tour_departures (tour_id, departure_date, total_seats, booked_seats) VALUES
    ((SELECT id FROM tours WHERE title='Giảm 50% tour hè Phú Quốc'),    CURRENT_DATE + 5,  30, 10),
    ((SELECT id FROM tours WHERE title='Giảm 50% tour hè Phú Quốc'),    CURRENT_DATE + 12, 30, 30),
    ((SELECT id FROM tours WHERE title='Du thuyền Hạ Long cao cấp'),    CURRENT_DATE + 20, 40, 15),
    ((SELECT id FROM tours WHERE title='Hành trình Fansipan dài ngày'), CURRENT_DATE + 45, 15, 3),
    ((SELECT id FROM tours WHERE title='Tour trong ngày tiết kiệm Đà Lạt'), CURRENT_DATE - 3, 25, 5);

-- Thêm ngày khởi hành cho một phần tour bulk (id chia hết cho 4).
INSERT INTO tour_departures (tour_id, departure_date, total_seats, booked_seats)
SELECT t.id, CURRENT_DATE + (g * 7), 25, (g * 5)
FROM tours t, generate_series(1, 3) AS g
WHERE t.title LIKE 'Gói khám phá %' AND (t.id % 4 = 0);

-- 4) User seed (chỉ để gán tên người đánh giá; mật khẩu là placeholder, không dùng để đăng nhập)
--    và review thật cho một tour, kèm đồng bộ rating denormalized.
INSERT INTO users (full_name, email, password, role, status, created_at, updated_at) VALUES
    ('Nguyễn Thị Mai', 'seed.mai@example.com',  '$2a$10$placeholderplaceholderplaceholderplaceholderph', 'USER', 'ACTIVE', now(), now()),
    ('Trần Văn Bình',  'seed.binh@example.com', '$2a$10$placeholderplaceholderplaceholderplaceholderph', 'USER', 'ACTIVE', now(), now())
ON CONFLICT (email) DO NOTHING;

INSERT INTO reviews (tour_id, user_id, rating, comment, created_at) VALUES
    ((SELECT id FROM tours WHERE title='Nghỉ dưỡng núi Sa Pa'),
     (SELECT id FROM users WHERE email='seed.mai@example.com'),  5, 'Cảnh đẹp ngoài sức tưởng tượng, hướng dẫn viên nhiệt tình.', now() - interval '2 days'),
    ((SELECT id FROM tours WHERE title='Nghỉ dưỡng núi Sa Pa'),
     (SELECT id FROM users WHERE email='seed.binh@example.com'), 4, 'Chuyến đi đáng giá nhưng buổi tối hơi lạnh.', now() - interval '1 day');

-- rating_avg/review_count phải khớp với review thật vừa seed (avg(5,4)=4.5, count=2).
UPDATE tours SET rating_avg = 4.5, review_count = 2 WHERE title = 'Nghỉ dưỡng núi Sa Pa';

-- ============================================================================
-- 5) Phủ chi tiết (ảnh / lịch trình / highlights / included / excluded) cho MỌI tour,
--    để màn chi tiết tour nào cũng đầy đủ khi demo. Nội dung đổi theo category & số ngày.
-- ============================================================================

INSERT INTO tour_highlights (tour_id, highlight)
SELECT t.id,
       (CASE t.category
            WHEN 'BEACH'    THEN ARRAY['Bãi tắm riêng yên tĩnh', 'Ngắm bình minh trên vịnh', 'Tiệc hải sản tươi sống']
            WHEN 'MOUNTAIN' THEN ARRAY['Toàn cảnh núi non hùng vĩ', 'Ruộng bậc thang mùa lúa chín', 'Ghé bản làng dân tộc']
            WHEN 'CITY'     THEN ARRAY['Đi bộ khám phá phố cổ', 'Ngắm thành phố từ tầng thượng', 'Ẩm thực đường phố về đêm']
            WHEN 'TREKKING' THEN ARRAY['Xuyên rừng cùng hướng dẫn viên', 'Tắm thác giữa rừng già', 'Cắm trại dưới bầu trời sao']
            WHEN 'CRUISE'   THEN ARRAY['Nghỉ đêm trên du thuyền', 'Chèo kayak giữa vịnh đá vôi', 'Tiệc hoàng hôn trên boong']
            ELSE                 ARRAY['Di sản được UNESCO công nhận', 'Trải nghiệm làng nghề truyền thống', 'Đêm nhạc dân gian']
        END)[g]
FROM tours t, generate_series(1, 3) AS g
WHERE NOT EXISTS (SELECT 1 FROM tour_highlights h WHERE h.tour_id = t.id);

INSERT INTO tour_included (tour_id, item)
SELECT t.id,
       (ARRAY[
           CASE WHEN t.duration_days > 1
                THEN 'Khách sạn ' || (t.duration_days - 1) || ' đêm'
                ELSE 'Tour trong ngày, không nghỉ đêm' END,
           'Bữa sáng hằng ngày',
           'Hướng dẫn viên tiếng Việt',
           'Vé vào cửa các điểm tham quan'
        ])[g]
FROM tours t, generate_series(1, 4) AS g
WHERE NOT EXISTS (SELECT 1 FROM tour_included i WHERE i.tour_id = t.id);

INSERT INTO tour_excluded (tour_id, item)
SELECT t.id,
       (ARRAY['Vé máy bay', 'Chi phí cá nhân', 'Tiền tip cho hướng dẫn viên và tài xế'])[g]
FROM tours t, generate_series(1, 3) AS g
WHERE NOT EXISTS (SELECT 1 FROM tour_excluded e WHERE e.tour_id = t.id);

INSERT INTO tour_images (tour_id, url, position)
SELECT t.id, 'https://img/tour-' || t.id || '-' || g || '.jpg', g - 1
FROM tours t, generate_series(1, 3) AS g
WHERE NOT EXISTS (SELECT 1 FROM tour_images im WHERE im.tour_id = t.id);

-- Lịch trình sinh đúng bằng duration_days: ngày đầu đón khách, ngày cuối tiễn khách.
INSERT INTO tour_itinerary_days (tour_id, day_number, title, description)
SELECT t.id, g,
       CASE WHEN t.duration_days = 1 THEN 'Tham quan trong ngày'
            WHEN g = 1               THEN 'Đón khách và nhận phòng'
            WHEN g = t.duration_days THEN 'Tiễn khách'
            ELSE 'Ngày ' || g || ' - Khám phá' END,
       CASE WHEN t.duration_days = 1 THEN 'Đón khách, tham quan theo lịch và trả khách trong ngày.'
            WHEN g = 1               THEN 'Đón khách tại sân bay, nhận phòng khách sạn và dạo quanh khu trung tâm.'
            WHEN g = t.duration_days THEN 'Ăn sáng, mua sắm đặc sản và tiễn khách ra sân bay.'
            ELSE 'Tham quan cùng hướng dẫn viên, ăn trưa theo chương trình, buổi tối tự do.' END
-- LATERAL để số ngày sinh ra bám đúng duration_days của từng tour (có tour dài 14 ngày),
-- thay vì đặt một trần cứng rồi cắt cụt lịch trình của tour dài.
FROM tours t, LATERAL generate_series(1, t.duration_days) AS g
WHERE NOT EXISTS (SELECT 1 FROM tour_itinerary_days d WHERE d.tour_id = t.id);

-- Sinh slug thân thiện URL, duy nhất. translate() bỏ dấu tiếng Việt TRƯỚC khi lọc ký tự,
-- nếu không thì regexp [^a-z0-9] sẽ xoá sạch chữ có dấu ("Đà Nẵng" -> "-ng").
UPDATE tours
SET slug = regexp_replace(
             regexp_replace(
               translate(lower(title), 'àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđÀÁẢÃẠĂẰẮẲẴẶÂẦẤẨẪẬÈÉẺẼẸÊỀẾỂỄỆÌÍỈĨỊÒÓỎÕỌÔỒỐỔỖỘƠỜỚỞỠỢÙÚỦŨỤƯỪỨỬỮỰỲÝỶỸỴĐ', 'aaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyydAAAAAAAAAAAAAAAAAEEEEEEEEEEEIIIIIOOOOOOOOOOOOOOOOOUUUUUUUUUUUYYYYYD'),
               '[^a-z0-9]+', '-', 'g'),
             '(^-|-$)', '', 'g')
           || '-' || id
WHERE slug IS NULL;
