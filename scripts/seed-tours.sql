-- Dữ liệu mẫu để thử GET /tours và GET /tours/{id}.
-- Chạy: PGPASSWORD=postgres psql -h localhost -U postgres -d tripgo -f scripts/seed-tours.sql
-- Chạy lại nhiều lần an toàn: xoá sạch dữ liệu tour trước khi seed.

TRUNCATE tour_images, tour_itinerary_days, tour_highlights, tour_included, tour_excluded, tours, destinations
    RESTART IDENTITY CASCADE;

INSERT INTO destinations (name, slug) VALUES
    ('Da Nang', 'da-nang'),
    ('Ha Noi', 'ha-noi'),
    ('Sa Pa', 'sa-pa');

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
