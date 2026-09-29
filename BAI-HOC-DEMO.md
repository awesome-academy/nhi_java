# TripGo — Bản đồ bài học mini project và kịch bản demo

Tài liệu này trả lời hai câu hỏi:

1. Mỗi chức năng của đề bài mini project (ứng dụng theo dõi tương tác Facebook/X) được áp dụng
   vào **chỗ nào** trong TripGo?
2. Demo thế nào và **giải thích ra sao** để trainer thấy mình hiểu chứ không chỉ chạy được.

> **Nói trước hai khác biệt so với đề bài**, để không ai hiểu nhầm là đã làm nhưng thực ra chưa:
>
> - **Spring Boot 4.1.1**, không phải 3.x. TripGo có sẵn từ trước và đã dùng bản này. Hệ quả thật:
>   `@MockBean` đã bị gỡ (dùng `@MockitoBean`), `spring-boot-starter-web` đổi tên thành
>   `spring-boot-starter-webmvc`, Jackson lên phiên bản 3 (`tools.jackson.databind`).
> - **Chỉ có đăng nhập Facebook, chưa có X (Twitter)**. Phần khung đã sẵn sàng cho nhà cung cấp
>   thứ hai (xem mục 1), thêm X chỉ là thêm một `ClientRegistration`.

---

## Bảng đối chiếu nhanh

| # | Bài học trong đề | Áp dụng vào TripGo | Trạng thái |
|---|---|---|---|
| 1 | Social Login (FB, TW) | Đăng nhập Facebook cho khách, đổi sang JWT của TripGo | ✅ (chưa có X) |
| 2 | Import Excel danh sách bài viết | Nhập **tour** hàng loạt từ Excel | ✅ |
| 3 | Background job crawl & update metrics | Job nền: tự huỷ đơn quá hạn, tính lại đánh giá | ⚠️ khác bản chất |
| 4 | Hiển thị biểu đồ (Chart.js) | 2 biểu đồ trên dashboard quản trị | ✅ |
| 5 | Thông báo realtime qua WebSocket | Báo admin khi có đơn mới / đổi trạng thái | ✅ |
| 6 | Xuất kết quả ra Excel | Xuất đơn đặt, tour, doanh thu tháng | ✅ |
| 7 | Bảo mật với CSRF | Bật cho khu quản trị, tắt cho API stateless | ✅ |
| 8 | Multithreading & JMS | `ThreadPoolTaskExecutor` + hàng đợi gửi mail ActiveMQ | ✅ |
| 9 | Reflection cho export/import động | `@ExcelColumn` + `ExcelMapper` | ✅ |

**Mục 3 khác bản chất** — nói thẳng chỗ này với trainer: đề bài crawl dữ liệu từ **API bên ngoài**
(Facebook/X), còn TripGo không có hệ thống ngoài nào để crawl. Nên phần áp dụng là **cơ chế job nền**
(`@Scheduled` + `@Async` + pool riêng + tắt được + một việc hỏng không đổ cả mẻ), còn nội dung công
việc là dọn dẹp dữ liệu nội bộ. Chi tiết ở mục 3.

---

## Bảng đối chiếu yêu cầu kỹ thuật

| Hạng mục | Yêu cầu | TripGo dùng gì | Ở đâu |
|---|---|---|---|
| Framework | Spring Boot 3.x | Spring Boot **4.1.1** | `pom.xml` |
| Security | Spring Security + OAuth2 + CSRF | 3 `SecurityFilterChain` tách biệt | `SecurityConfig`, `AdminSecurityConfig`, `SocialLoginConfig` |
| Database | MySQL / PostgreSQL | PostgreSQL 16 | `docker-compose.yml` |
| Messaging | JMS (ActiveMQ / RabbitMQ) | ActiveMQ Classic | `JmsConfig` |
| Realtime | WebSocket + STOMP | STOMP trên WebSocket thuần | `WebSocketConfig` |
| Scheduler | `@Scheduled` + `@Async` | Cả hai | `MaintenanceScheduler` |
| Multithread | `ThreadPoolTaskExecutor` | Pool riêng, có chính sách từ chối | `AsyncConfig` |
| File Handling | Apache POI | POI 5.5.1, tầng generic | `excel/ExcelMapper` |
| WebService | SOAP (Spring-WS) | Contract-first từ XSD | `WebServiceConfig`, `soap/` |
| Chart | Chart.js hoặc ECharts | Chart.js 4.4.4 | `static/js/admin-charts.js` |
| Testing | JUnit5 + Mockito + MockMvc | **286 test**, cả Testcontainers | `src/test/` |
| API Docs | Swagger / OpenAPI | springdoc-openapi | `/swagger-ui.html` |
| Logging | SLF4J + Logback | Mặc định của Spring Boot | toàn bộ service |
| Frontend | Thymeleaf hoặc React | Thymeleaf + Bootstrap 5 | `templates/admin/` |

---

# 1. Social Login

## Áp dụng vào đâu

Khách đăng nhập TripGo bằng Facebook thay vì phải đăng ký email/mật khẩu.

| File | Việc |
|---|---|
| `config/SocialLoginConfig` | Khai `ClientRegistration` cho Facebook + chain bảo mật riêng |
| `security/SocialLoginUserService` | Sau khi Facebook xác thực xong: tìm-hoặc-tạo user trong bảng `users` |
| `security/OAuth2LoginSuccessHandler` | **Đổi sang JWT của TripGo** |
| `security/SocialUserAttributes` | Chuẩn hoá dữ liệu thô của Facebook |
| `entity/AuthProvider` | `LOCAL` / `FACEBOOK` — thêm Google/X chỉ là thêm hằng số |

## Demo

```bash
# .env phải có FACEBOOK_CLIENT_ID và FACEBOOK_CLIENT_SECRET
set -a; source .env; set +a && ./mvnw spring-boot:run
```

Mở trình duyệt: `http://localhost:8080/oauth2/authorization/facebook`

Sau khi đồng ý trên Facebook, callback trả thẳng JSON:

```json
{ "token": "<JWT của TripGo>", "user": { "id": 42, "email": "...", "name": "..." } }
```

Copy `token` đó rồi gọi một API cần đăng nhập để chứng minh nó dùng được thật:

```bash
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/auth/me
```

## Giải thích gì

**Vấn đề cốt lõi**: OAuth2 cần **session** để giữ tham số `state` giữa hai request (lúc chuyển sang
Facebook và lúc Facebook gọi về). Nhưng API của TripGo là **stateless JWT**. Hai thứ này xung khắc.

**Cách giải quyết**: một `SecurityFilterChain` riêng chỉ nhận `/oauth2/**` và `/login/oauth2/**`,
được phép có session; xong bắt tay thì `OAuth2LoginSuccessHandler` phát JWT và **huỷ session ngay**.
Từ đó trở đi client chỉ dùng JWT như mọi endpoint khác.

**Ba chi tiết đáng nói:**

1. **Email trùng thì gắn vào tài khoản cũ.** Khách từng đăng ký bằng `a@x.com`, giờ đăng nhập
   Facebook cũng `a@x.com` → gắn Facebook vào chính tài khoản đó, giữ nguyên wishlist và đơn hàng.
   Nếu email đã thuộc một tài khoản Facebook **khác** thì dừng lại — không cướp tài khoản người ta.
2. **Không có email vẫn đăng nhập được.** App Facebook mới chưa được duyệt quyền `email`. Cột
   `email` là `NOT NULL + unique`, nên hệ thống tự sinh địa chỉ giữ chỗ `facebook.<id>@facebook.local`.
3. **PKCE** được bật (`code_challenge_method=S256`): kể cả khi mã authorization bị lộ trên đường
   redirect, thiếu `code_verifier` vẫn không đổi được token.

## Trainer hay hỏi

> *"Tài khoản Facebook không có mật khẩu thì `POST /auth/login` xử lý sao?"*

`AuthService.login` kiểm tra `user.getPassword() == null` **trước** khi so khớp, và trả lời rõ
*"Tài khoản này đăng nhập bằng Facebook"*. Không kiểm thì `passwordEncoder.matches(raw, null)` sẽ
nổ hoặc trả sai lệch.

> *"Thêm Twitter/X thì phải sửa gì?"*

Thêm một `ClientRegistration` trong `SocialLoginConfig`, thêm hằng số vào `AuthProvider`, và thêm
một nhánh `case X ->` trong `SocialUserAttributes.of()`. Không đụng tới phần còn lại.

---

# 2. Import Excel

## Áp dụng vào đâu

Đề bài nhập danh sách **bài viết**; TripGo nhập danh sách **tour** — cùng một bài toán.

| File | Việc |
|---|---|
| `admin/AdminTourImportController` | Trang `/admin/tours/import`, nút tải file mẫu |
| `service/TourImportService` | Điều phối: đọc file → lưu từng dòng → tổng kết |
| `service/TourRowImporter` | Lưu **một** dòng, trong transaction riêng |
| `admin/excel/TourImportRow` | Khai các cột bằng `@ExcelColumn` |

## Demo

1. Vào `/admin/tours` → **Nhập Excel**
2. Bấm **Tải file mẫu** → mở ra, thêm vài dòng, **cố tình làm sai 2–3 dòng**
   (sai slug điểm đến, giá KM cao hơn giá gốc, để trống cột Giá)
3. Tải lên → màn hình hiện:

```
Tổng dòng: 5    Đã nhập: 2    Bỏ qua: 3
  dòng 4: không có điểm đến với slug 'khong-ton-tai'
  dòng 5: giá khuyến mãi phải nhỏ hơn giá gốc
  dòng 6: thiếu giá trị ở cột bắt buộc 'Giá'
```

4. **Tải lên lại đúng file đó** → tất cả bị bỏ qua vì trùng slug, không tạo bản sao

## Giải thích gì

**Quyết định 1 — nhập được dòng nào hay dòng đó.** Từ chối cả file chỉ vì một ô gõ nhầm sẽ biến
việc nhập 200 tour thành vòng lặp sửa-thử vô tận. Số dòng báo lỗi **khớp đúng thanh số dòng trong
Excel** để người dùng tìm được chỗ sửa.

**Quyết định 2 — mỗi dòng một transaction** (`REQUIRES_NEW` trong `TourRowImporter`). Để cả file
trong một transaction thì một dòng vi phạm ràng buộc DB sẽ **rollback những dòng đã lưu thành công**.

**Quyết định 3 — trùng slug thì bỏ qua, không ghi đè.** Chạy lại cùng một file không tạo bản sao,
và không lặng lẽ đè lên dữ liệu admin đã sửa tay.

## Trainer hay hỏi

> *"Vì sao `TourRowImporter` là một bean riêng chứ không phải method private?"*

**Đây là câu đáng giá nhất của mục này.** `@Transactional` hoạt động qua proxy. Gọi một method
`@Transactional` từ **chính lớp đó** (self-invocation) sẽ **không mở transaction nào cả** — không
báo lỗi, không có triệu chứng, chỉ âm thầm sai cho tới lúc dữ liệu hỏng. Tách sang bean khác thì
lời gọi đi qua proxy và annotation mới có tác dụng.

> *"Người dùng đảo thứ tự cột thì sao?"*

Cột được khớp theo **tên tiêu đề**, không theo vị trí. Thừa cột cũng không sao.

---

# 3. Background job

## Áp dụng vào đâu — và khác đề bài chỗ nào

Đề bài: mỗi giờ gọi API Facebook/X lấy số like/share về lưu, để vẽ được biểu đồ theo thời gian.

TripGo **không có hệ thống ngoài nào để crawl**. Nên phần áp dụng là **cơ chế**, còn nội dung công
việc là dọn dẹp nội bộ:

| Job | Nhịp | Việc |
|---|---|---|
| Tự huỷ đơn quá hạn | 1 giờ | Đơn `PENDING` quá 72h → huỷ và **hoàn chỗ** cho khách khác |
| Tính lại đánh giá | 6 giờ | So `rating_avg`/`review_count` với bảng `reviews`, sửa tour bị lệch |

| File | Việc |
|---|---|
| `config/MaintenanceScheduler` | Lịch chạy (`@Scheduled` + `@Async`) |
| `service/MaintenanceService` | Việc thật — tách ra để test gọi thẳng, không phải chờ tới giờ |
| `config/AsyncConfig` | `ThreadPoolTaskExecutor` riêng |

## Demo

Job chạy mỗi giờ nên **không ngồi chờ được**. Hai cách demo:

**Cách 1 — rút ngắn chu kỳ** (thuyết phục nhất):

```bash
BOOKING_EXPIRE_HOURS=0 \
  ./mvnw spring-boot:run -Dspring-boot.run.arguments=--tasks.maintenance.initial-delay=PT5S,--tasks.maintenance.cancel-expired-delay=PT10S
```

Đặt một đơn qua API, chờ ~15 giây, xem log:

```
Đã tự huỷ 1 đơn chờ xác nhận quá 0 giờ
```

Rồi mở `/admin/bookings` thấy đơn chuyển sang **Đã huỷ**, và số chỗ trống của ngày khởi hành đã tăng lại.

**Cách 2 — chạy test**, nhanh và chắc chắn:

```bash
./mvnw -Dtest=MaintenanceServiceIntegrationTest test
```

6 test, trong đó có `runningTwiceIsIdempotent` (chạy hai lần không trừ chỗ hai lần).

## Giải thích gì

**`fixedDelay` chứ không `fixedRate`.** `fixedRate` đếm 1 giờ từ lúc **bắt đầu**; job chạy 70 phút
với chu kỳ 60 phút sẽ tự chồng lên chính nó. `fixedDelay` đếm từ lúc **kết thúc**.

**Pool riêng, không dùng executor mặc định.** Mặc định của Spring Boot là `SimpleAsyncTaskExecutor`
— nó tạo **một luồng mới cho mỗi lần gọi, không giới hạn**. Một job chạy lâu gặp lúc nhiều việc dồn
lại sẽ sinh luồng mất kiểm soát. Pool của TripGo giới hạn 2–4 luồng, hàng đợi 50, và khi đầy thì
`CallerRunsPolicy` (chạy ngay trên luồng gọi) — **chậm còn hơn mất việc**.

**Một đơn hỏng không kéo đổ cả mẻ.** Vòng lặp bắt lỗi từng đơn và ghi log, thay vì gom cả mẻ vào
một transaction.

**Job tắt trong profile test.** Nó chạy xen vào giữa test sẽ sửa dữ liệu ngay dưới chân test và
sinh lỗi chập chờn rất khó lần.

## Trainer hay hỏi

> *"Sao không crawl gì cả?"*

Trả lời thẳng: TripGo không có hệ thống ngoài để crawl, nên mình áp dụng đúng **cơ chế** job nền
vào bài toán có thật của dự án. Nếu cần crawl thật, chỗ cắm vào là `MaintenanceService` — thêm một
method và một `@Scheduled`, phần hạ tầng (pool, tắt/bật, chống lỗi) dùng lại nguyên vẹn.

---

# 4. Biểu đồ (Chart.js)

## Áp dụng vào đâu

Hai biểu đồ trên dashboard `/admin`:

- **Doanh thu 6 tháng gần nhất** (cột dọc)
- **5 tour doanh thu cao nhất tháng này** (cột ngang)

| File | Việc |
|---|---|
| `service/DashboardService.charts()` | Gom dữ liệu |
| `admin/AdminDashboardController` | `GET /admin/reports/charts` trả JSON |
| `static/js/admin-charts.js` | Vẽ biểu đồ |

## Demo

Mở `/admin`, chỉ vào hai biểu đồ, rồi bấm **"Xem dạng bảng"** dưới mỗi biểu đồ.

## Giải thích gì

**Mỗi biểu đồ chỉ có MỘT chuỗi số liệu nên dùng MỘT màu.** Tô mỗi cột một màu khác khi chúng cùng
ý nghĩa là gán màu theo **thứ hạng** chứ không theo dữ liệu, và làm người đọc tưởng màu mang thông
tin gì đó.

**Tháng không có đơn nào vẫn hiện cột 0.** Bỏ hẳn cột đi sẽ làm trục thời gian nói dối — nhìn như
tháng đó không tồn tại.

**Mỗi biểu đồ kèm một bảng số liệu.** Biểu đồ không được là cách **duy nhất** đọc được con số —
người dùng trình đọc màn hình, và cả lúc in ra giấy.

**Ba lỗi chỉ phát hiện được khi chụp màn hình ra nhìn**, test không bắt được:

1. Nhãn trục `10.000.000` dài tới mức Chart.js phải xoay chéo, xoay xong thì các nhãn chồng lên
   nhau → rút về `10 tr`
2. Đường lưới cắt ngang qua cột thay vì lùi về sau → thêm `z: -1`
3. Token màu chế độ tối bị áp theo `prefers-color-scheme`, trong khi khu quản trị chỉ có giao diện
   sáng → máy nào để OS dark mode sẽ thấy biểu đồ màu tối trên thẻ trắng

Điểm 3 là bài học đáng kể: **test xanh không có nghĩa là giao diện đúng.**

## Trainer hay hỏi

> *"Sao không dùng biểu đồ tròn cho top tour?"*

Biểu đồ tròn rất khó so sánh các giá trị gần nhau. Cột ngang đọc được ngay thứ tự, và tên tour dài
có chỗ để hiển thị.

---

# 5. WebSocket + STOMP

## Áp dụng vào đâu

Admin đang mở bất kỳ trang quản trị nào sẽ thấy thẻ thông báo ở góc màn hình khi có **đơn mới**,
đơn được **xác nhận** hoặc bị **huỷ**.

| File | Việc |
|---|---|
| `config/WebSocketConfig` | Endpoint `/admin/ws`, kênh `/topic/bookings` |
| `event/BookingEvent` | Bản sao dữ liệu của sự kiện |
| `event/BookingNotifier` | Nghe sự kiện → đẩy xuống trình duyệt |
| `static/js/admin-notifications.js` | Kết nối STOMP, hiện thông báo |

## Demo

**Chuẩn bị 2 cửa sổ:** trình duyệt mở `/admin/bookings`, và một terminal.

Ở terminal, đặt một đơn qua API công khai:

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/register -H 'Content-Type: application/json' \
  -d '{"name":"Demo","email":"demo-'$(date +%s)'@example.com","password":"MatKhau123"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")

curl -s -X POST localhost:8080/api/v1/bookings -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"tourId":1,"date":"2026-10-14","adults":2,"children":0,
       "contact":{"fullName":"Khách demo","email":"d@example.com","phone":"0900000000"}}'
```

Thông báo hiện lên ở trình duyệt **ngay lập tức**, không cần F5.

## Giải thích gì

**Endpoint đặt tại `/admin/ws` chứ không phải `/ws`.** Nằm trong khu quản trị nên chain bảo mật
của admin bảo vệ luôn. Đặt ở ngoài thì phải mở công khai, và **ai cũng nghe được thông tin đơn hàng
của khách**.

**Không dùng SockJS.** SockJS dự phòng bằng các transport dùng POST, mà POST thì vướng CSRF của
chain quản trị — phải mở ngoại lệ CSRF cho cả nhánh đó. WebSocket thuần chỉ cần một `GET Upgrade`
nên không đụng tới CSRF.

**`@TransactionalEventListener(AFTER_COMMIT)` chứ không `@EventListener` thường.** Nghe ngay lúc
phát sự kiện thì transaction **còn chưa commit**; một lỗi sau đó làm rollback sẽ để lại thông báo
về một đơn **không hề tồn tại**. Có test canh đúng chỗ này
(`sendsNothingWhenTransactionRollsBack`).

**Đi qua `ApplicationEvent` chứ không gọi thẳng messaging từ service.** `BookingService` không cần
biết gì về WebSocket — và nhờ vậy cắm thêm JMS vào (mục 8) chỉ là thêm một listener.

## Trainer hay hỏi

> *"`BookingEvent` sao không truyền thẳng entity `Booking`?"*

Người nhận xử lý ở **luồng khác** và **ngoài transaction**, nơi entity lazy đã hết dùng được —
chạm vào quan hệ lazy sẽ ném `LazyInitializationException`. Nên sự kiện là **bản sao dữ liệu** tại
thời điểm đó.

---

# 6. Xuất Excel

## Áp dụng vào đâu

| Trang | Nút | Nội dung |
|---|---|---|
| `/admin/bookings` | Xuất Excel | Đơn theo **đúng bộ lọc đang xem** |
| `/admin/tours` | Xuất Excel | Tour theo từ khoá đang tìm |
| `/admin` | Xuất doanh thu tháng | Gộp theo tour, bỏ qua đơn đã huỷ |

File: `service/ExcelExportService`, `admin/ExcelDownload`.

## Demo

Vào `/admin/bookings`, bấm lọc **"Chờ xác nhận"**, rồi bấm **Xuất Excel**. Mở file ra: chỉ có đơn
đang chờ, không phải toàn bộ.

## Giải thích gì

**Xuất theo bộ lọc đang xem, không phải xuất tất cả.** Admin lọc xong rồi mới muốn gửi file cho ai
đó — đó là thao tác thường gặp hơn nhiều so với xuất toàn bộ.

**Số ghi dạng số, không phải chuỗi**, để Excel tính tổng được. Ngày có định dạng `dd/mm/yyyy`.
Dòng tiêu đề được khoá (`createFreezePane`) để cuộn danh sách dài vẫn thấy tên cột.

**Tên file có dấu tiếng Việt** hiển thị đúng nhờ `ContentDisposition.builder().filename(name, UTF_8)`
— nó tự sinh cả `filename*` theo RFC 5987.

---

# 7. CSRF

## Áp dụng vào đâu

TripGo có **ba** `SecurityFilterChain` với cấu hình ngược nhau — đây chính là chỗ để giải thích
CSRF cho ra đầu ra đũa:

| Chain | Phạm vi | Session | CSRF | Xác thực |
|---|---|---|---|---|
| `AdminSecurityConfig` | `/admin/**` | có | **BẬT** | form login |
| `SocialLoginConfig` | `/oauth2/**`, `/login/oauth2/**` | có | tắt | OAuth2 |
| `SecurityConfig` | còn lại | STATELESS | tắt | Bearer JWT |

## Demo

```bash
# Đăng nhập admin, lấy token CSRF từ form
# Rồi gửi POST KHÔNG kèm token:
curl -X POST localhost:8080/admin/tours/1/delete -b cookie.txt
# → 403 Forbidden
```

Trên giao diện, mở **Xem nguồn trang** bất kỳ form admin nào, chỉ vào
`<input type="hidden" name="_csrf" value="...">` mà Thymeleaf tự chèn.

## Giải thích gì

**Vì sao API tắt CSRF mà không sai?** CSRF lợi dụng việc trình duyệt **tự động gửi cookie** kèm
request. API của TripGo xác thực bằng header `Authorization: Bearer ...` — trình duyệt **không tự
gửi header**, nên không có đường tấn công. Bật CSRF cho API stateless chỉ gây phiền, không tăng an
toàn.

**Vì sao khu quản trị phải bật?** Nó dùng session cookie. Không có CSRF, một trang web độc hại chỉ
cần dụ admin bấm vào link là có thể gửi `POST /admin/tours/5/delete` kèm cookie của admin.

**Một chi tiết tinh tế:** TripGo **tách lỗi CSRF khỏi lỗi phân quyền**:

```java
if (exception instanceof CsrfException) → 403          // lỗi kỹ thuật
else → redirect /admin/login?denied                     // sai quyền
```

Gộp chung thì lỗi CSRF sẽ hiện thành *"bạn không có quyền"* — che mất nguyên nhân thật và làm người
debug đi sai hướng cả buổi. Test bắt được đúng chỗ này.

---

# 8. Multithreading & JMS

## Áp dụng vào đâu

**Multithreading** — `config/AsyncConfig`: `ThreadPoolTaskExecutor` dùng chung cho job nền và gửi mail.

**JMS** — mail xác nhận đặt tour đi qua hàng đợi `tripgo.booking.mail`:

| File | Việc |
|---|---|
| `config/JmsConfig` | Cấu hình hàng đợi + listener |
| `mail/BookingMailDispatcher` | Interface — hai bản cài |
| `mail/DirectBookingMailDispatcher` | Bản dự phòng khi không có broker |

## Demo

**Không có broker (mặc định):**

```bash
./mvnw spring-boot:run
# Đặt một đơn → log hiện: [MAIL] gửi tới ... — TripGo đã nhận đơn đặt tour của bạn
```

**Có broker:**

```bash
docker compose up -d mq
JMS_ENABLED=true ./mvnw spring-boot:run
# Đặt một đơn → cùng dòng log đó, nhưng đã đi qua hàng đợi
# Mở http://localhost:8161 (admin/admin) xem hàng đợi tripgo.booking.mail
```

Hoặc chạy test — nhanh và không cần Docker:

```bash
./mvnw -Dtest=BookingMailDispatchTest test
```

Test này chạy **cả hai nhánh**, trong đó nhánh JMS dùng broker nhúng `vm://` nên gói tin đi qua
hàng đợi **thật**.

## Giải thích gì

**Vì sao dùng hàng đợi mà không gọi thẳng?** Nếu máy chủ mail chập chờn: gọi thẳng thì mail **mất
luôn**; qua hàng đợi thì listener ném lỗi, message **quay lại hàng đợi để thử lại**
(`spring.jms.listener.session.transacted: true`).

**Vì sao mặc định tắt?** Có ActiveMQ trên classpath mà không có broker thì listener thử kết nối lại
liên tục và làm log đầy lỗi. Môi trường dev và test không cần dựng broker chỉ để chạy ứng dụng.

**Vì sao vẫn có bản dự phòng?** Nếu tắt JMS mà không có gì thay thế, việc gửi mail sẽ **lặng lẽ
biến mất** khi ai đó quên bật. Hai bản cài cùng một interface nên phần còn lại của ứng dụng không
cần biết có broker hay không.

**Gửi dạng chuỗi JSON tự serialize** thay vì dùng message converter có sẵn: gói tin đi qua hàng đợi
có thể được đọc lại bởi **phiên bản ứng dụng khác**, nên định dạng phải là thứ mình kiểm soát được,
không phụ thuộc vào cách một thư viện tuần tự hoá đối tượng Java.

## Trainer hay hỏi

> *"`@Async` với JMS khác gì nhau? Đều là chạy nền cả?"*

| | `@Async` | JMS |
|---|---|---|
| Việc nằm ở đâu | Trong bộ nhớ tiến trình | Trong broker, ngoài tiến trình |
| App chết giữa chừng | **Mất việc** | Việc còn nguyên trong hàng đợi |
| Thử lại khi lỗi | Tự viết | Có sẵn |
| Nhiều instance chia việc | Không | Có |

`@Async` đủ cho việc không quan trọng; JMS cho việc **không được phép mất**.

---

# 9. Reflection cho export/import

## Áp dụng vào đâu

Đây là phần **kiến trúc đẹp nhất** trong nhóm này. Package `demo.tripgo.excel`:

```java
@Getter @Setter
public class TourExportRow {
    @ExcelColumn(header = "Tên tour", order = 2)  private String title;
    @ExcelColumn(header = "Giá",      order = 6)  private BigDecimal price;
}
```

Chỉ vậy. Không viết một dòng code đọc/ghi Excel nào.

| File | Việc |
|---|---|
| `excel/ExcelColumn` | Annotation đánh dấu field thành cột |
| `excel/ExcelMapper` | **Quét annotation bằng reflection**, đọc và ghi file |
| `excel/ExcelCellConverter` | Đổi ô Excel sang kiểu Java |
| `excel/ExcelReadResult` | Dòng đọc được + dòng lỗi nằm cạnh nhau |

Hiện có **5 loại** dùng chung tầng này: `BookingExportRow`, `TourExportRow`, `RevenueExportRow`,
`TourImportRow`, và cả file mẫu.

## Demo

Mở song song 2 file cho trainer xem:

1. `TourExportRow.java` — chỉ có field và annotation, **không có logic**
2. `ExcelMapper.java` — đoạn `scan()`:

```java
for (Field field : current.getDeclaredFields()) {
    ExcelColumn column = field.getAnnotation(ExcelColumn.class);
    if (column == null) continue;
    field.setAccessible(true);
    fields.add(new ExcelField(field, column.header(), column.order(), column.required()));
}
```

Nói: *"thêm một loại dữ liệu mới — ví dụ xuất danh sách đánh giá — chỉ cần tạo một lớp và gắn
annotation. Không sửa `ExcelMapper` một dòng nào."*

## Giải thích gì

**Reflection giải quyết vấn đề gì?** Không có nó, mỗi loại dữ liệu phải có một hàm ghi riêng:

```java
// Nếu không dùng reflection — lặp lại cho MỖI loại
sheet.createRow(0).createCell(0).setCellValue("Mã đơn");
sheet.createRow(0).createCell(1).setCellValue("Khách");
// ... 8 cột × 5 loại = 40 dòng lặp, và mỗi lần đổi cột phải sửa 2 chỗ
```

**Giá phải trả:** reflection chậm hơn gọi trực tiếp, nên `ExcelMapper` **cache kết quả quét** theo
lớp (`ConcurrentHashMap`) — mỗi lớp chỉ quét một lần.

**Khớp cột theo tên tiêu đề, không theo vị trí** — người dùng đảo cột hay chèn thêm cột ghi chú vẫn
nhập được.

**Một chi tiết tìm ra nhờ test:** dòng mà người dùng gõ rồi xoá để lại ô **CHUỖI RỖNG** chứ không
phải ô `BLANK`. Không tính cả hai thì file thật đầy những dòng "ma" ở cuối bảng sẽ báo lỗi
*"thiếu giá trị ở cột bắt buộc"*.

---

# 10. Phần ngoài đề bài

Nói thêm nếu còn thời gian — cho thấy đã nghĩ xa hơn yêu cầu:

| Hạng mục | Nội dung |
|---|---|
| **Xoá mềm + thùng rác** | Tour/điểm đến xoá rồi khôi phục được; đơn hàng cũ không vỡ |
| **Upload ảnh** | Lưu ngoài classpath; đuôi file suy ra từ content type chứ **không** lấy từ tên file client gửi |
| **Testcontainers** | 9 test chạy Postgres **thật**, vì H2 chấp nhận nhiều thứ Postgres từ chối |
| **Tìm kiếm không dấu** | Gõ "da nang" ra "Đà Nẵng" |
| **SOAP** | Contract-first từ XSD cho hệ thống đối tác |

**Câu chuyện đắt giá nhất để kể** (nếu trainer hỏi *"em học được gì?"*):

> Bộ test 286 cái đều xanh, nhưng trang điểm đến vẫn lỗi 500 trên Postgres thật:
> `function lower(bytea) does not exist`. Nguyên nhân: truy vấn có tham số `null`, Postgres không
> suy được kiểu nên bind thành `bytea` — còn H2 trong test thì chạy bình thường. Từ đó em thêm
> Testcontainers để các truy vấn viết tay chạy trên đúng loại DB của production.

Đây là câu trả lời cho thấy hiểu **giới hạn của test**, không chỉ biết viết test.

---

# 11. Kịch bản demo 15 phút

| Phút | Việc | Điểm nhấn |
|---|---|---|
| 0–1 | Mở `/admin/login`, đăng nhập | 3 chain bảo mật khác nhau |
| 1–3 | Dashboard: 4 ô số liệu + 2 biểu đồ + bảng kèm theo | Một chuỗi → một màu |
| 3–5 | Đặt đơn từ terminal → **thông báo hiện ngay** | `AFTER_COMMIT` |
| 5–7 | Lọc đơn "Chờ xác nhận" → Xuất Excel → mở file | Xuất theo bộ lọc |
| 7–10 | Nhập Excel có dòng sai → bảng lỗi theo từng dòng | Mỗi dòng một transaction |
| 10–12 | Mở `TourExportRow` + `ExcelMapper.scan()` | Reflection |
| 12–14 | `curl` gọi SOAP, lấy WSDL | Contract-first |
| 14–15 | Chạy `./mvnw test` | 286 test |

**Chuẩn bị trước khi demo:**

```bash
# 1. Bật app với chu kỳ job ngắn để demo được job nền
set -a; source .env; set +a && ./mvnw spring-boot:run

# 2. Có sẵn tài khoản admin
#    .env: ADMIN_EMAIL=... ADMIN_PASSWORD=...

# 3. Có sẵn file Excel mẫu ĐÃ sửa vài dòng sai — đừng sửa live, mất thời gian

# 4. Mở sẵn: trình duyệt (2 tab), terminal, IDE ở ExcelMapper.java
```

**Ba câu dễ bị hỏi nhất — chuẩn bị trước:**

1. *"Vì sao API tắt CSRF?"* → JWT ở header, trình duyệt không tự gửi header
2. *"`@Transactional` trên method private có chạy không?"* → Không. Proxy không chặn được
   self-invocation. Đó là lý do có `TourRowImporter`
3. *"Test chạy H2 mà production chạy Postgres, có vấn đề gì không?"* → Có, và em đã gặp thật.
   Kể câu chuyện `lower(bytea)` ở mục 10
