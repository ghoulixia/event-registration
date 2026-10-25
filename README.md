# Event Registration System

Backend đăng ký sự kiện dùng Java 21, Spring Boot 4, PostgreSQL và Spring Data JPA, hỗ trợ sự kiện của
trường, câu lạc bộ và các đơn vị bên ngoài.

## Chức năng

- Xem danh sách, chi tiết Event; lọc theo từ khóa, thành phố, loại; sắp xếp và phân trang.
- Đăng ký tài khoản, đăng nhập JWT; người tạo Event trở thành owner.
- Owner sửa thông tin, capacity và TTL; đóng/mở đăng ký, upload thumbnail, xem participants và audit.
- Owner loại participant trước giờ bắt đầu, ghi REMOVED cùng lý do và trả chỗ.
- Xóa mềm Event có lý do, giữ registrations và audit.
- Đăng ký thường được xác nhận ngay; hủy trước startTime, kể cả khi Event đang CLOSED tạm thời.

Module Waitlist/SlotOffer chưa được merge vào main. Bản hiện tại lưu cấu hình TTL và cung cấp
EventOfferLifecycle để nối module này vào transaction của Event. FIFO, cấp/accept offer và scheduler
chưa hoạt động trong main.

## Cấu trúc

```text
Controller -> Service (@Transactional) -> Repository/data adapter -> PostgreSQL
```

Controller xử lý HTTP, DTO và principal. EventService kiểm tra owner, thời gian và capacity. Các thao tác
cập nhật Event và Registration dùng chung khóa PESSIMISTIC_WRITE trên Event, rồi kiểm tra lại dữ liệu
và thời gian. EventStore/PageResult giữ các kiểu Spring Data ở lớp adapter. Baseline xử lý đồng bộ trong
một backend và một PostgreSQL.

Xem [API](docs/api-contract.md) và [thiết kế](docs/architecture.md).

## Chạy bằng Docker

Cần Docker Desktop ở chế độ Linux containers. Tại thư mục gốc:

```powershell
$env:JWT_SECRET = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
docker compose up --build -d
```

Backend: http://localhost:8080. Swagger: http://localhost:8080/swagger-ui/index.html.
PostgreSQL dùng cổng 5433; dữ liệu và thumbnail được lưu trong named volume. Giữ JWT_SECRET ổn định
giữa các lần chạy để token cũ tiếp tục hợp lệ; không đưa khóa vào Git.

## Chạy Java và frontend riêng

Cần JDK 21, Maven Wrapper và PostgreSQL. Ví dụ với DB mới dành riêng cho dự án:

```powershell
$env:JWT_SECRET = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$env:DB_URL = 'jdbc:postgresql://localhost:5433/event_registration'
$env:DB_USERNAME = 'event'
$env:DB_PASSWORD = 'event123'
cd backend
.\mvnw.cmd spring-boot:run
```

UPLOAD_DIR đặt thư mục thumbnail, mặc định uploads/thumbnails. Event datetime dùng giờ
Asia/Ho_Chi_Minh; audit dùng Instant/TIMESTAMPTZ.

Frontend hỗ trợ demo quản lý Event. Trong frontend, chạy npm ci rồi npm run dev. VITE_API_URL mặc định
là http://localhost:8080/api. CORS mặc định cho localhost/127.0.0.1 cổng 5173; dùng
app.cors.allowed-origins khi chạy cổng khác. Demo owner chưa có trang lịch sử đăng ký cá nhân.

## Migration và dữ liệu cũ

V1–V6 giữ nguyên từ main GitHub. V11 bổ sung owner, heldCount/TTL và thông tin REMOVED, sử dụng bảng
audit hiện có. V7–V10 thuộc nhánh waitlist nên không tái sử dụng. Flyway bật out-of-order để ghép các
migration đó sau khi kiểm tra tương thích.

Event cũ giữ organizer_user_id=NULL: vẫn được xem công khai, chỉ được quản lý sau khi có mapping owner
đã xác minh.

Nếu DB đã chạy V5/V6 thử nghiệm local khác nội dung trên GitHub, hãy sao lưu và kiểm tra
flyway_schema_history trước khi chuyển đổi. Không sửa checksum hoặc dùng Flyway repair để bỏ qua xung
đột; không xóa volume cũ để khắc phục lỗi migration. Kiểm thử và demo nên dùng DB riêng.

## Kiểm thử

Khởi động Docker Desktop, rồi chạy trong backend:

```powershell
.\mvnw.cmd clean test
```

Integration test dùng PostgreSQL 16 Testcontainers và thư mục upload trong target. Bộ kiểm thử bao gồm
owner, thời gian sau khóa, counter, REMOVED/rejoin, rollback audit/ảnh và API. Contract test dùng provider
giả lập để kiểm tra việc gọi hook waitlist trong cùng transaction; FIFO và scheduler thuộc module chưa
merge. Unit test kiểm tra các nhánh nghiệp vụ bằng mock.

Frontend: npm run build và npm run lint. Bản này chưa có kết quả kiểm thử tải Kaggle. Đánh giá baseline
và cải tiến Pha 2 cần số liệu trên cùng cấu hình phần cứng.
