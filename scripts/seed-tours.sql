-- Dữ liệu mẫu để thử GET /tours và GET /tours/{id}.
-- Chạy: PGPASSWORD=postgres psql -h localhost -U postgres -d tripgo -f scripts/seed-tours.sql
-- Chạy lại nhiều lần an toàn: xoá sạch dữ liệu tour trước khi seed.

TRUNCATE reviews, tour_departures, tour_images, tour_itinerary_days, tour_highlights, tour_included,
    tour_excluded, tours, destinations RESTART IDENTITY CASCADE;

INSERT INTO destinations (name, slug) VALUES
    ('Da Nang', 'da-nang'),
    ('Ha Noi', 'ha-noi'),
    ('Sa Pa', 'sa-pa'),
    ('Hoi An', 'hoi-an'),
    ('Nha Trang', 'nha-trang'),
    ('Phu Quoc', 'phu-quoc'),
    ('Da Lat', 'da-lat'),
    ('Hue', 'hue');

-- Nhiều tour Da Nang với giá/đánh giá/thời lượng khác nhau để thử lọc + sắp xếp + phân trang.
INSERT INTO tours (title, destination_id, category, duration_days, price, discount_price,
                   rating_avg, review_count, max_guests, thumbnail_url, description, created_at, updated_at)
VALUES
    ('Da Nang Beach Escape', (SELECT id FROM destinations WHERE slug='da-nang'),
     'BEACH', 3, 2500000, 1990000, 4.8, 120, 20, 'https://img/dn-beach.jpg',
     'Relax on My Khe beach with sunrise views.', now(), now()),
    ('Da Nang City Discovery', (SELECT id FROM destinations WHERE slug='da-nang'),
     'CITY', 2, 1500000, NULL, 4.2, 45, 15, 'https://img/dn-city.jpg',
     'Explore Dragon Bridge and local street food.', now(), now()),
    ('Ba Na Hills Trek', (SELECT id FROM destinations WHERE slug='da-nang'),
     'TREKKING', 4, 3200000, 2990000, 4.6, 88, 12, 'https://img/dn-bana.jpg',
     'Golden Bridge and mountain trekking adventure.', now(), now()),
    ('Sa Pa Mountain Retreat', (SELECT id FROM destinations WHERE slug='sa-pa'),
     'MOUNTAIN', 5, 4200000, NULL, 4.9, 210, 10, 'https://img/sapa.jpg',
     'Fansipan peak and terraced rice fields.', now(), now()),
    ('Ha Noi Old Quarter Walk', (SELECT id FROM destinations WHERE slug='ha-noi'),
     'CITY', 1, 800000, NULL, 4.0, 30, 25, 'https://img/hanoi.jpg',
     'Cultural walking tour of the Old Quarter.', now(), now());

-- Bổ sung ảnh/lịch trình/highlights cho 1 tour để thử màn chi tiết.
INSERT INTO tour_images (tour_id, url, position) VALUES
    ((SELECT id FROM tours WHERE title='Da Nang Beach Escape'), 'https://img/dn-beach-1.jpg', 0),
    ((SELECT id FROM tours WHERE title='Da Nang Beach Escape'), 'https://img/dn-beach-2.jpg', 1),
    ((SELECT id FROM tours WHERE title='Da Nang Beach Escape'), 'https://img/dn-beach-3.jpg', 2);

INSERT INTO tour_itinerary_days (tour_id, day_number, title, description) VALUES
    ((SELECT id FROM tours WHERE title='Da Nang Beach Escape'), 1, 'Arrival & Check-in', 'Airport pickup, hotel, free time at the beach.'),
    ((SELECT id FROM tours WHERE title='Da Nang Beach Escape'), 2, 'Marble Mountains', 'Guided tour of Marble Mountains and Hoi An.'),
    ((SELECT id FROM tours WHERE title='Da Nang Beach Escape'), 3, 'Departure', 'Breakfast and airport transfer.');

INSERT INTO tour_highlights (tour_id, highlight) VALUES
    ((SELECT id FROM tours WHERE title='Da Nang Beach Escape'), 'Sunrise on My Khe beach'),
    ((SELECT id FROM tours WHERE title='Da Nang Beach Escape'), 'Hoi An ancient town by night');

INSERT INTO tour_included (tour_id, item) VALUES
    ((SELECT id FROM tours WHERE title='Da Nang Beach Escape'), '2 nights hotel'),
    ((SELECT id FROM tours WHERE title='Da Nang Beach Escape'), 'Daily breakfast');

INSERT INTO tour_excluded (tour_id, item) VALUES
    ((SELECT id FROM tours WHERE title='Da Nang Beach Escape'), 'Airfare'),
    ((SELECT id FROM tours WHERE title='Da Nang Beach Escape'), 'Personal expenses');

-- Ngày khởi hành để thử GET /tours/{id}/availability (ngày tương đối so với hôm nay).
INSERT INTO tour_departures (tour_id, departure_date, total_seats, booked_seats) VALUES
    ((SELECT id FROM tours WHERE title='Da Nang Beach Escape'), CURRENT_DATE + 7,  20, 5),
    ((SELECT id FROM tours WHERE title='Da Nang Beach Escape'), CURRENT_DATE + 14, 20, 18),
    ((SELECT id FROM tours WHERE title='Da Nang Beach Escape'), CURRENT_DATE + 30, 20, 0),
    ((SELECT id FROM tours WHERE title='Sa Pa Mountain Retreat'), CURRENT_DATE + 10, 12, 4);

-- ============================================================================
-- Dữ liệu bổ sung (~50 tour) phủ các case đặc biệt như trong test.
-- ============================================================================

-- 1) Tour edge-case rõ ràng: ký tự wildcard _ và % trong tiêu đề (test escape LIKE),
--    đủ 6 category, biên giá (1tr/5tr), rating cao/thấp/đúng-biên-4.5, có/không discount.
INSERT INTO tours (title, destination_id, category, duration_days, price, discount_price,
                   rating_avg, review_count, max_guests, thumbnail_url, description, created_at, updated_at)
VALUES
    ('Beach_Villa Retreat',      (SELECT id FROM destinations WHERE slug='hoi-an'),   'BEACH',    3, 2200000, 1990000, 4.7, 60,  18, 'https://img/e1.jpg', 'Underscore in title for LIKE-escape test.', now(), now()),
    ('Dive_Snorkel Combo',       (SELECT id FROM destinations WHERE slug='nha-trang'),'BEACH',    2, 1800000, NULL,    4.3, 40,  16, 'https://img/e2.jpg', 'Another underscore title.', now(), now()),
    ('50% Summer Sale Tour',     (SELECT id FROM destinations WHERE slug='phu-quoc'), 'BEACH',    4, 3000000, 1500000, 4.6, 75,  22, 'https://img/e3.jpg', 'Percent sign in title for LIKE-escape test.', now(), now()),
    ('100% Wild Nature Trek',    (SELECT id FROM destinations WHERE slug='sa-pa'),    'TREKKING', 6, 3500000, NULL,    4.9, 150, 12, 'https://img/e4.jpg', 'Another percent title.', now(), now()),
    ('Luxury Halong Cruise',     (SELECT id FROM destinations WHERE slug='ha-noi'),   'CRUISE',   2, 4800000, 4200000, 4.8, 95,  30, 'https://img/e5.jpg', 'CRUISE category sample.', now(), now()),
    ('Hue Imperial Culture',     (SELECT id FROM destinations WHERE slug='hue'),      'CULTURAL', 2, 1200000, NULL,    4.1, 33,  25, 'https://img/e6.jpg', 'CULTURAL category sample.', now(), now()),
    ('Budget Day Escape',        (SELECT id FROM destinations WHERE slug='da-lat'),   'CITY',     1, 1000000, NULL,    3.5, 12,  20, 'https://img/e7.jpg', 'Boundary price 1,000,000 and duration 1.', now(), now()),
    ('Premium Grand Expedition', (SELECT id FROM destinations WHERE slug='sa-pa'),    'MOUNTAIN',14, 5000000, NULL,    5.0, 200, 10, 'https://img/e8.jpg', 'Boundary price 5,000,000, long duration, rating 5.0.', now(), now()),
    ('Top Rated Hidden Gem',     (SELECT id FROM destinations WHERE slug='hoi-an'),   'CULTURAL', 3, 2600000, NULL,    5.0, 300, 15, 'https://img/e9.jpg', 'Highest rating.', now(), now()),
    ('Low Rated Sample Tour',    (SELECT id FROM destinations WHERE slug='ha-noi'),   'CITY',     2, 900000,  NULL,    2.0, 5,   25, 'https://img/e10.jpg','Low rating sample.', now(), now()),
    ('Quiet Retreat No Trips',   (SELECT id FROM destinations WHERE slug='da-lat'),   'MOUNTAIN', 3, 2100000, NULL,    4.4, 20,  14, 'https://img/e11.jpg','No departures seeded -> availability rỗng.', now(), now()),
    ('Da Lat Flower Festival',   (SELECT id FROM destinations WHERE slug='da-lat'),   'CULTURAL', 2, 1600000, 1400000, 4.5, 55,  28, 'https://img/e12.jpg','Rating đúng biên 4.5.', now(), now());

-- 2) Bulk 38 tour trải đều category/điểm đến/giá/thời lượng/rating để test lọc + sắp xếp + phân trang.
--    created_at lệch nhau để sort=newest có ý nghĩa.
INSERT INTO tours (title, destination_id, category, duration_days, price, discount_price,
                   rating_avg, review_count, max_guests, thumbnail_url, description, created_at, updated_at)
SELECT
    'Explore Package ' || g,
    (SELECT id FROM destinations OFFSET (g % (SELECT count(*) FROM destinations)) LIMIT 1),
    (ARRAY['BEACH','MOUNTAIN','CITY','TREKKING','CRUISE','CULTURAL'])[1 + (g % 6)],
    1 + (g % 12),
    500000 + (g % 19) * 250000,
    CASE WHEN g % 3 = 0 THEN 500000 + (g % 19) * 250000 - 150000 ELSE NULL END,
    round((2.6 + (g % 25) * 0.1)::numeric, 1),
    g % 40,
    10 + (g % 15),
    'https://img/pkg-' || g || '.jpg',
    'Sample tour package ' || g || ' for testing filters, sort and pagination.',
    now() - ((g) || ' hours')::interval,
    now()
FROM generate_series(1, 38) AS g;

-- 3) Ngày khởi hành cho một số tour: hiện tại/tương lai/quá khứ + tour cố tình không có.
INSERT INTO tour_departures (tour_id, departure_date, total_seats, booked_seats) VALUES
    ((SELECT id FROM tours WHERE title='50% Summer Sale Tour'),     CURRENT_DATE + 5,  30, 10),
    ((SELECT id FROM tours WHERE title='50% Summer Sale Tour'),     CURRENT_DATE + 12, 30, 30),
    ((SELECT id FROM tours WHERE title='Luxury Halong Cruise'),     CURRENT_DATE + 20, 40, 15),
    ((SELECT id FROM tours WHERE title='Premium Grand Expedition'), CURRENT_DATE + 45, 15, 3),
    ((SELECT id FROM tours WHERE title='Budget Day Escape'),        CURRENT_DATE - 3,  25, 5);

-- Thêm ngày khởi hành cho một phần tour bulk (id chia hết cho 4).
INSERT INTO tour_departures (tour_id, departure_date, total_seats, booked_seats)
SELECT t.id, CURRENT_DATE + (g * 7), 25, (g * 5)
FROM tours t, generate_series(1, 3) AS g
WHERE t.title LIKE 'Explore Package %' AND (t.id % 4 = 0);

-- 4) User seed (chỉ để gán tên người đánh giá; mật khẩu là placeholder, không dùng để đăng nhập)
--    và review thật cho một tour, kèm đồng bộ rating denormalized.
INSERT INTO users (full_name, email, password, role, status, created_at, updated_at) VALUES
    ('Seed Alice', 'seed.alice@example.com', '$2a$10$placeholderplaceholderplaceholderplaceholderph', 'USER', 'ACTIVE', now(), now()),
    ('Seed Bob',   'seed.bob@example.com',   '$2a$10$placeholderplaceholderplaceholderplaceholderph', 'USER', 'ACTIVE', now(), now())
ON CONFLICT (email) DO NOTHING;

INSERT INTO reviews (tour_id, user_id, rating, comment, created_at) VALUES
    ((SELECT id FROM tours WHERE title='Sa Pa Mountain Retreat'),
     (SELECT id FROM users WHERE email='seed.alice@example.com'), 5, 'Amazing views!', now() - interval '2 days'),
    ((SELECT id FROM tours WHERE title='Sa Pa Mountain Retreat'),
     (SELECT id FROM users WHERE email='seed.bob@example.com'),   4, 'Great but cold.', now() - interval '1 day');

-- rating_avg/review_count phải khớp với review thật vừa seed (avg(5,4)=4.5, count=2).
UPDATE tours SET rating_avg = 4.5, review_count = 2 WHERE title = 'Sa Pa Mountain Retreat';
