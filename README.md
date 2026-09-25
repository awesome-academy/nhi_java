# TripGo API

REST API đặt tour du lịch: tìm kiếm/lọc tour, xem chi tiết & ngày khởi hành, đặt và huỷ đơn,
đánh giá tour. Xây bằng **Spring Boot 4.1 / Java 21 / PostgreSQL**, xác thực **JWT**.

- Base URL: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/swagger-ui.html` (tắt ở profile `prod`)
- Ứng dụng không dùng `context-path`: tiền tố `/api/v1` do `ApiPathConfig` gắn riêng cho
  package `demo.tripgo.controller`, để khu quản trị `/admin/**` nằm ngoài tiền tố đó.
- Bộ request mẫu: [`docs/api.http`](docs/api.http)

---

## 1. Chức năng

| Nhóm | Mô tả |
|---|---|
| F1 | Đăng ký, đăng nhập (JWT), xem thông tin tài khoản |
| F2 | Danh sách tour: tìm kiếm, lọc (điểm đến/loại/giá/số ngày/đánh giá), sắp xếp, phân trang |
| F3 | Chi tiết tour: điểm đến, ảnh, lịch trình theo ngày, highlights/included/excluded |
| F4 | Ngày khởi hành & số chỗ còn trống (lọc theo tháng) |
| F5 | Đặt tour, danh sách đơn của tôi, chi tiết đơn, huỷ đơn |
| F6 | Đánh giá tour (xem danh sách công khai, tạo đánh giá khi đã đăng nhập) |
| F7 | Danh sách điểm đến (kèm số tour) và loại hình tour (kèm nhãn tiếng Việt) |
| F8 | Wishlist: lưu / bỏ / xem tour quan tâm của riêng mình |

## 2. Danh sách API

| Method | Endpoint | Quyền | Mô tả |
|---|---|---|---|
| POST | `/auth/register` | public | Đăng ký → 201, trả luôn `{ token, user }` |
| POST | `/auth/login` | public | Đăng nhập → `{ token, user }` |
| GET | `/auth/me` | user | Thông tin tài khoản hiện tại |
| GET | `/tours` | public | Danh sách tour (filter/sort/paging) |
| GET | `/tours/{slug}` | public | Chi tiết tour (nhận cả id dạng số), kèm `startDates` |
| GET | `/tours/{id}/availability` | public | Ngày khởi hành & chỗ trống |
| GET | `/tours/{id}/reviews` | public | Đánh giá của tour (phân trang) |
| POST | `/tours/{id}/reviews` | user **đã đặt tour** | Tạo đánh giá → 201 |
| GET | `/destinations` | public | Điểm đến + ảnh + số tour |
| GET | `/categories` | public | Loại hình tour + nhãn tiếng Việt (bảng `categories`) |
| POST | `/bookings` | user | Đặt tour → 201 |
| GET | `/bookings` | user | Đơn của tôi (phân trang) |
| GET | `/bookings/{id}` | user | Chi tiết đơn của tôi |
| PATCH | `/bookings/{id}/cancel` | user | Huỷ đơn |
| GET | `/wishlist` | user | Tour đã lưu của tôi |
| POST | `/wishlist` | user | Thêm tour vào wishlist |
| DELETE | `/wishlist/{tourId}` | user | Bỏ tour khỏi wishlist → `{ message, removed, tourId }` |

**Tham số của `GET /tours`:** `q`, `destination` (slug), `category`
(`beach|mountain|city|trekking|cruise|cultural`), `minPrice`, `maxPrice`, `duration`, `rating`,
`sort` (`newest|price_asc|price_desc|rating`, mặc định `newest`; `rating_desc` là bí danh của `rating`), `page` (từ 1), `limit`
(mặc định 10, tối đa 50).

**Định dạng response.** Danh sách phân trang trả `{ data, total, page, limit }`, danh sách thường
trả `{ data: [...] }`, GET đơn lẻ trả object DTO. Mọi lỗi dùng chung một khuôn:

```json
{ "error": { "code": "VALIDATION", "message": "Dữ liệu không hợp lệ",
             "fields": { "email": "Email không hợp lệ" } } }
```

Message trả về bằng tiếng Việt. `code` dùng tên `HttpStatus`, trừ hai trường hợp hợp đồng chỉ
định riêng: `INVALID_CREDENTIALS` (sai email/mật khẩu) và `VALIDATION` (lỗi bean-validation).

`fields` chỉ xuất hiện ở lỗi bean-validation. Status dùng: 200/201 · 400 sai kiểu tham số ·
401 chưa xác thực · 403 thiếu quyền · 404 không tồn tại **hoặc không sở hữu** · 409 xung đột
(trùng email/đánh giá, hết chỗ, huỷ đơn đã huỷ) · 422 lỗi validate · 429 vượt rate limit.

## 2b. Khu quản trị (giao diện)

Trang quản trị render phía server bằng Thymeleaf, tách hẳn khỏi API:

| | API `/api/v1/**` | Quản trị `/admin/**` |
|---|---|---|
| Xác thực | Bearer JWT | form đăng nhập + session |
| CSRF | tắt (không dùng cookie) | **bật** |
| Quyền | theo từng endpoint | `hasRole('ADMIN')` |

Hai `SecurityFilterChain` riêng biệt (`SecurityConfig` và `AdminSecurityConfig`), phân biệt bằng
`securityMatcher`, nên thay đổi ở khu quản trị không ảnh hưởng API.

| Đường dẫn | Mô tả |
|---|---|
| `/admin/login` | Đăng nhập (công khai) |
| `/admin` | Dashboard |
| `/admin/logout` | Đăng xuất (POST, có token CSRF) |

**Tạo tài khoản admin đầu tiên:** đặt `ADMIN_EMAIL` và `ADMIN_PASSWORD` trong `.env` rồi khởi động
lại. Tài khoản chỉ được **tạo mới**, không bao giờ ghi đè tài khoản đã tồn tại — đổi biến môi
trường sẽ không đổi mật khẩu admin đang dùng.

Giao diện dùng Bootstrap 5 (CDN) + `src/main/resources/static/css/admin.css`.

---

## 2c. Đăng nhập Facebook (tuỳ chọn)

Bỏ trống `FACEBOOK_CLIENT_ID` thì tính năng không được bật và ứng dụng chạy bình thường với
đăng nhập email/mật khẩu.

| Đường dẫn | Mô tả |
|---|---|
| `/oauth2/authorization/facebook` | Bắt đầu đăng nhập (mở bằng trình duyệt) |
| `/login/oauth2/code/facebook` | Callback Facebook gọi về |

Khai trong app Facebook (App settings → Basic) và thêm **Valid OAuth Redirect URI**:
`http://localhost:8080/login/oauth2/code/facebook`

> **Database đã có dữ liệu thì phải chạy migration trước:**
> ```bash
> psql -h localhost -p 5432 -U postgres -d tripgo -f scripts/migrate-social-login.sql
> ```
> `ddl-auto=update` không gỡ được ràng buộc `NOT NULL` khỏi cột `password` (tài khoản Facebook
> không có mật khẩu). Bỏ qua bước này thì tạo tài khoản Facebook sẽ lỗi ở tầng DB.

Xong luồng, TripGo đổi sang **JWT của chính nó** để client dùng chung một loại token cho mọi
endpoint. Đặt `OAUTH2_SUCCESS_REDIRECT_URI` thì callback redirect về đó kèm `?token=`; bỏ trống
thì trả thẳng JSON `{ token, user }` như `POST /auth/login`.

Tài khoản Facebook không có mật khẩu, nên `POST /auth/login` với email đó trả 401 kèm lời nhắn
dùng đăng nhập Facebook. Nếu email đã có tài khoản email/mật khẩu, Facebook được **gắn vào chính
tài khoản đó** để giữ nguyên wishlist và đơn hàng cũ.

---

## 2d. Xuất Excel

| Trang | Nút | Nội dung |
|---|---|---|
| `/admin/bookings` | Xuất Excel | Đơn theo **đúng bộ lọc đang xem** |
| `/admin/tours` | Xuất Excel | Tour theo từ khoá đang tìm |
| `/admin` | Xuất doanh thu tháng | Doanh thu gộp theo tour, bỏ qua đơn đã huỷ |

Tầng Excel (`demo.tripgo.excel`) dùng chung cho mọi loại dữ liệu: gắn `@ExcelColumn` lên field,
`ExcelMapper` đọc bằng reflection rồi tự sinh file. Thêm loại mới không phải viết code đọc/ghi.

## 2e. Nhập tour từ Excel

`/admin/tours/import` — có nút tải **file mẫu** sinh từ chính lớp `TourImportRow`, nên cột trong
file mẫu không bao giờ lệch với cột bộ nhập mong đợi.

| Cột bắt buộc | Ghi chú |
|---|---|
| `Tên tour` | |
| `Điểm đến` | nhập **slug** (vd `da-nang`) |
| `Loại hình` | nhập **slug** (vd `beach`) |
| `Giá`, `Số ngày`, `Số khách tối đa` | |

Cột khớp theo **tên tiêu đề** nên thứ tự cột không quan trọng; thừa cột cũng không sao.

Nguyên tắc xử lý:

- **Nhập được dòng nào hay dòng đó.** Một ô gõ sai chỉ làm hỏng dòng đó, kèm số dòng đúng như
  trong Excel để tìm mà sửa.
- **Slug trùng thì bỏ qua**, không ghi đè — nhập lại cùng một file không tạo bản sao và không
  đè lên dữ liệu admin đã sửa tay.
- Mỗi dòng là một transaction riêng, nên một dòng hỏng không kéo đổ những dòng đã lưu.

---

## 3. Chạy bằng Docker (khuyến nghị)

Yêu cầu Docker + Docker Compose. Compose dựng sẵn cả Postgres lẫn API.

```bash
cp .env.example .env
echo "JWT_SECRET=$(openssl rand -base64 32)" >> .env   # dòng thêm sau ghi đè dòng trống phía trên
docker compose up -d --build
```

API sẵn sàng tại `http://localhost:8080/api/v1`. Xem log: `docker compose logs -f api`.
Dừng: `docker compose down` (thêm `-v` để xoá luôn dữ liệu Postgres).

Nếu máy đã có Postgres chiếm cổng 5432, đặt `DB_PORT=55432` trong `.env`.
Đổi cổng API bằng `SERVER_PORT`.

## 4. Chạy local (không Docker)

Cần Java 21 và một PostgreSQL đang chạy với database `tripgo`.

```bash
cp .env.example .env      # điền JWT_SECRET
set -a; source .env; set +a
./mvnw spring-boot:run
```

`JWT_SECRET` **bắt buộc**, không có giá trị mặc định. Các biến khác đều có default cho dev:
`JWT_EXPIRATION` (ms, mặc định 86400000 = 24h), `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`,
`SERVER_PORT`, `CORS_ALLOWED_ORIGINS` (mặc định `*`, prod phải đặt cụ thể),
`RATE_LIMIT_AUTH_MAX` (20), `RATE_LIMIT_AUTH_WINDOW` (giây, 60).

Schema do Hibernate `ddl-auto: update` tự tạo lúc khởi động — không cần migration thủ công.

## 5. Dữ liệu mẫu

Chạy **sau khi** ứng dụng đã khởi động lần đầu (để Hibernate tạo bảng xong):

```bash
# Local
PGPASSWORD=postgres psql -h localhost -U postgres -d tripgo -f scripts/seed-tours.sql
# Docker (DB_PORT theo .env)
PGPASSWORD=postgres psql -h localhost -p 5432 -U postgres -d tripgo -f scripts/seed-tours.sql
```

Script chạy lại nhiều lần an toàn (xoá sạch dữ liệu tour trước khi seed). Sau khi seed có:

- **55 tour** thuộc **8 điểm đến**, tất cả đều có ảnh, lịch trình và ngày khởi hành
  (riêng *Tĩnh dưỡng Đà Lạt chưa mở chuyến* cố ý để trống, dùng thử case `availability` rỗng)
- **347 đánh giá** rải trên mọi tour, tour nhiều nhất có 12 — đủ để thử `?page=2&limit=10`
- **10 đơn đặt tour** (8 `pending`, 2 `cancelled`) trên 10 tour khác nhau, mỗi đơn một tài khoản
  `seed.*` — dùng để thử luồng F8: chủ đơn đánh giá được (201), người chưa đặt bị chặn (403),
  và người có đơn **đã huỷ** cũng bị chặn
- **1 admin + 14 tài khoản người dùng**, mật khẩu là hash BCrypt thật nên đăng nhập được ngay:

  | Tài khoản | Mật khẩu | Role |
  |---|---|---|
  | `admin@tripgo.vn` | `admin123` | ADMIN |
  | `seed.mai@example.com` (và các `seed.*`) | `password123` | USER |

`rating_avg`/`review_count` trên bảng `tours` được đồng bộ đúng bằng dữ liệu thật trong bảng
`reviews`, nhưng vẫn giữ phân hoá (2.3 → 4.7) để lọc `?rating=` và `sort=rating_desc` có ý nghĩa.

## 6. Thử API

Mở [`docs/api.http`](docs/api.http) trong IntelliJ IDEA (HTTP Client) hoặc VS Code
(extension *REST Client*) rồi bấm **Send Request**. File phủ đủ 17 endpoint kèm các case lỗi
(401/403/404/409/422/429); token được gán tự động sau request đăng nhập.

Hoặc dùng Swagger UI tại `/swagger-ui.html`.

## 7. Kiểm thử

```bash
./mvnw test
```

94 test chạy trên H2 (chế độ PostgreSQL), profile `test` có sẵn khoá JWT riêng nên **không cần**
đặt `JWT_SECRET`. Gồm integration test MockMvc cho auth, tour, review, availability, booking,
destination và security config; cộng unit test rate limit và test race-condition khi huỷ đơn.

## 8. Thiết kế

**Tầng:** `Controller` (mỏng, chỉ delegate) → `Service` (nghiệp vụ, `@Transactional`) →
`Repository` (Spring Data JPA). `Mapper` chuyển entity ↔ DTO; DTO là Java `record`.

**Quan hệ dữ liệu:**

```
Category    1─* Tour                       Tour 1─* TourImage
Destination 1─* Tour 1─* Departure
                 │  1─* ItineraryDay        Tour 1─* Review *─1 User
                 │  *─* User (wishlist, bảng user_wishlist)
                 └──────── 1─* Booking *─1 User   (Booking embeds ContactInfo)
```

**Điểm đáng chú ý:**

- **Không N+1**: `@EntityGraph` ở `BookingRepository`/`ReviewRepository`, `join fetch` ở
  `TourRepository.findDetailById`, và `root.fetch("destination")` trong `TourSpecifications`
  (tách nhánh riêng cho truy vấn count để không fetch thừa).
- **Lọc/sắp xếp/phân trang chạy ở DB** qua `Specification` + `Pageable`, không tải hết rồi lọc.
  Có `@Index` cho mọi cột dùng để lọc/sắp xếp; mọi quan hệ đều `LAZY`.
- **Quyền sở hữu** kiểm ở service bằng `findByIdAndUserId` → trả **404** thay vì 403 để không
  lộ sự tồn tại của đơn người khác. `userId` lấy từ JWT, `totalPrice` server tự tính — không
  nhận từ client.
- **Chống oversell**: khoá bi quan (`@Lock(PESSIMISTIC_WRITE)`) trên hàng `Departure` khi đặt,
  và `entityManager.refresh(..., PESSIMISTIC_WRITE)` khi huỷ để tránh lost update.
- **Rating denormalized** (`rating_avg`, `review_count` trên `Tour`) để lọc/sắp xếp theo đánh giá
  chạy được ở DB; cập nhật lại mỗi khi có review mới.
- **Chỉ người đã đặt tour mới được đánh giá**: `POST /tours/{id}/reviews` kiểm tra user có
  booking cho tour đó với trạng thái khác `CANCELLED`, nếu không trả **403**. Bám theo user story
  F8 ("là người dùng đã đặt tour"), chặn tài khoản ảo spam điểm. Đơn đã huỷ không tính, để không
  ai đặt rồi huỷ ngay chỉ nhằm lấy quyền đánh giá.
- **Rate limit** `POST /auth/**` chống brute-force; lỗi 401/403/429 phát sinh trong filter cũng
  dùng chung khuôn `{error:{...}}` qua `SecurityErrorResponder`.

## 9. Bảo mật khi vận hành

Mật khẩu băm BCrypt; JWT có `exp`; secret chỉ đến từ biến môi trường.

Khoá từng bị commit phải coi là đã lộ — mỗi môi trường dùng một khoá riêng, cấp qua biến môi
trường hoặc secret manager. Sau khi xoay khoá, token cũ hết hiệu lực và người dùng phải đăng
nhập lại. Không dùng khoá công khai của profile `test` cho môi trường chạy thật.

Trước khi commit, soát diff đã stage để phát hiện secret lọt vào:

```bash
git diff --cached | grep -iE "secret|password|token|key"
```
