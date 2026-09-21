-- Chuyển loại hình tour từ enum (cột chuỗi tours.category) sang bảng categories + khoá ngoại.
-- ddl-auto:update KHÔNG làm được việc này: nó có thể thêm cột mới nhưng không điền dữ liệu,
-- không đặt được NOT NULL trên bảng đã có dữ liệu, và không bao giờ xoá cột cũ.
-- Chạy MỘT LẦN trên DB đã có dữ liệu cũ (DB mới thì seed-tours.sql đã tạo đúng cấu trúc).
BEGIN;

CREATE TABLE IF NOT EXISTS categories (
    id   bigserial PRIMARY KEY,
    slug varchar(50)  NOT NULL UNIQUE,
    name varchar(100) NOT NULL
);

INSERT INTO categories (slug, name) VALUES
    ('beach',    'Biển đảo'),
    ('mountain', 'Núi rừng'),
    ('city',     'Thành phố'),
    ('trekking', 'Trekking'),
    ('cruise',   'Du thuyền'),
    ('cultural', 'Văn hoá')
ON CONFLICT (slug) DO NOTHING;

-- Thêm cột cho phép NULL trước, vì bảng đang có dữ liệu.
ALTER TABLE tours ADD COLUMN IF NOT EXISTS category_id bigint;

-- Điền dữ liệu: 'BEACH' (enum cũ) -> id của slug 'beach'.
UPDATE tours t
SET category_id = c.id
FROM categories c
WHERE t.category_id IS NULL AND lower(t.category) = c.slug;

-- Chỉ siết ràng buộc sau khi chắc chắn không còn dòng nào rỗng.
DO $$
DECLARE missing int;
BEGIN
    SELECT count(*) INTO missing FROM tours WHERE category_id IS NULL;
    IF missing > 0 THEN
        RAISE EXCEPTION 'Còn % tour chưa map được category, dừng migration', missing;
    END IF;
END $$;

ALTER TABLE tours ALTER COLUMN category_id SET NOT NULL;
ALTER TABLE tours DROP CONSTRAINT IF EXISTS fk_tour_category;
ALTER TABLE tours ADD CONSTRAINT fk_tour_category
    FOREIGN KEY (category_id) REFERENCES categories (id);

DROP INDEX IF EXISTS idx_tour_category;
ALTER TABLE tours DROP COLUMN IF EXISTS category;
CREATE INDEX IF NOT EXISTS idx_tour_category ON tours (category_id);

COMMIT;
