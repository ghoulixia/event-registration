# Event Registration

Bài tập lớn môn Kiến trúc phần mềm: xây dựng ứng dụng web quản lý sự kiện và đăng ký tham gia bằng React, Spring Boot và PostgreSQL. Đây là dự án Pha 1 để nhóm khác có thể tiếp nhận và phát triển ở Pha 2.

## 1. Bài toán và phạm vi

Một sự kiện có số chỗ giới hạn. Nếu sự kiện đã đầy, người đến sau có thể xếp hàng chờ thay vì phải liên tục kiểm tra xem có ai hủy không. Khi có chỗ, hệ thống mời người đầu hàng và giữ chỗ cho họ trong thời gian giới hạn; hết hạn hoặc từ chối thì chuyển cho người tiếp theo.

- Khách chưa đăng nhập: tìm, lọc và xem sự kiện theo thành phố, danh mục.
- Người dùng đã đăng nhập: đăng ký, hủy đăng ký, xem lịch sử của mình; tham gia/rời hàng chờ, nhận hoặc từ chối lời mời.
- Người tạo sự kiện: tạo và sửa sự kiện của mình, đổi số chỗ, đóng/mở đăng ký, tải ảnh đại diện, xem người tham gia, loại người tham gia có lý do, xem hàng chờ và nhật ký thao tác.

Không có tài khoản quản trị chung; người tạo không được tự đăng ký sự kiện mình tạo. Sự kiện có trạng thái `OPEN`/`CLOSED` và không nhận đăng ký sau giờ bắt đầu.

Hiện chưa có gửi lời mời qua email/SMS: người dùng phải mở trang để theo dõi hàng chờ. Đăng ký lấy thông tin từ tài khoản, chưa có biểu mẫu thông tin riêng cho từng lượt. API hàng chờ trả các lượt đang hoạt động, chưa trả lịch sử lời mời đã kết thúc.

## 2. Nghiệp vụ chính

### 2.1. Tạo sự kiện và đăng ký

Người dùng đăng ký tài khoản, đăng nhập để nhận JWT. Các thao tác cần đăng nhập gửi `Authorization: Bearer <token>`; backend kiểm tra quyền sở hữu đối với thao tác quản lý sự kiện.

Người tạo nhập tên, đơn vị tổ chức, thành phố, danh mục, thời gian và sức chứa; có thể thêm mô tả, ảnh và cấu hình thời gian nhận lời mời. Khách tìm sự kiện, xem số chỗ còn lại. Khi còn chỗ, đăng ký được xác nhận ngay; không cho đăng ký trùng hay vượt sức chứa. Khi chủ sự kiện giảm sức chứa, hệ thống không cho giảm xuống dưới số chỗ đã xác nhận hoặc đang giữ cho lời mời.

Người tham gia có thể hủy trước giờ bắt đầu. Chủ sự kiện có thể loại người tham gia trước giờ bắt đầu nhưng phải ghi lý do. Người bị loại không được tự đăng ký hoặc vào hàng chờ lại sự kiện đó. Bản ghi cũ được giữ lại để xem lịch sử; xóa sự kiện cũng là xóa mềm, không xóa mất nhật ký.

### 2.2. Hàng chờ và lời mời có hạn

1. Khi sự kiện đầy, người dùng có thể vào hàng chờ; vào hàng chờ **chưa giữ chỗ**. Một người không được xếp hàng trùng cho cùng sự kiện.
2. Khi có chỗ do hủy đăng ký, loại người tham gia hoặc tăng sức chứa, hệ thống ưu tiên người đứng đầu hàng chờ. Chỉ lời mời đang chờ trả lời mới giữ một chỗ.
3. Người được mời chấp nhận trước hạn để xác nhận đăng ký, hoặc từ chối để nhường lượt. Nếu quá hạn, tác vụ định kỳ cũng xử lý lời mời và chuyển sang người tiếp theo, kể cả khi không ai mở trang.
4. Người dùng xem hàng chờ và thời hạn lời mời trong tài khoản; chủ sự kiện xem danh sách chờ của sự kiện mình tạo.

**Tình huống demo:** tạo sự kiện một chỗ, thời hạn nhận lời mời 5 phút và giờ bắt đầu trong tương lai. A đăng ký; B rồi C vào hàng chờ. A hủy → B được mời; B từ chối → C được mời và chấp nhận. Cần bốn tài khoản: người tạo, A, B, C.

## 3. Các vấn đề cần xử lý và yêu cầu kỹ thuật

- **Không cấp trùng chỗ:** nhiều người có thể đăng ký hoặc nhận lời mời cùng lúc. Backend khóa bản ghi sự kiện khi thay đổi số chỗ, xử lý đăng ký, lời mời và nhật ký trong giao dịch database. Số chỗ còn lại được tính sau khi trừ cả người đã xác nhận lẫn chỗ đang giữ.
- **Công bằng hàng chờ:** ưu tiên theo thứ tự vào hàng, không cho lượt đăng ký trực tiếp lấy chỗ đang dành cho người chờ. Lời mời từ chối, hết hạn hoặc bị thu hồi phải trả chỗ đúng cách. Kiểm thử tải trên Kaggle CPU và số liệu so sánh trước/sau thay đổi kiến trúc vẫn chưa có.
- **Quyền truy cập và lịch sử:** JWT xác định người dùng; chỉ người tạo xem và quản lý dữ liệu riêng của sự kiện. Người tham gia chỉ xem lịch sử đăng ký của mình, không được đọc toàn bộ nhật ký sự kiện. Lý do loại người tham gia và các thao tác quản lý được lưu để đối chiếu.
- **Vận hành:** Flyway quản lý các lần thay đổi bảng; PostgreSQL và backend chạy bằng Docker Compose. Khóa JWT là biến môi trường, không lưu trong Git. Database và ảnh nằm trong Docker volumes; không xóa volumes nếu cần giữ dữ liệu demo.

## 4. Kiến trúc, mã nguồn và công nghệ

Pha 1 dùng một backend và một database, chưa tách thành nhiều dịch vụ hoặc dùng hệ thống hàng đợi. Giao diện gọi API của backend; backend xử lý quy tắc nghiệp vụ rồi lưu dữ liệu vào PostgreSQL.

```text
frontend/src/                                      Giao diện React, trang và lời gọi API
backend/src/main/java/com/team/eventregistration/
  controller/                                      Nhận yêu cầu HTTP
  dto/                                             Dữ liệu gửi vào và trả về
  service/                                         Quy tắc đăng ký, hàng chờ, lời mời
  repository/                                      Truy cập dữ liệu
  entity/                                          Bản ghi trong database
  security/, config/                               JWT, quyền truy cập và cấu hình ứng dụng
backend/src/main/resources/db/migration/           Các lần cập nhật cấu trúc database
backend/src/test/                                  Kiểm thử backend
compose.yaml, backend/Dockerfile                   Build và chạy backend, PostgreSQL
specs/openapi.yaml                                 Đặc tả API có thể xem trước
```

- React, TypeScript, Vite: xây dựng giao diện, chạy riêng khi phát triển và demo.
- Java 21, Spring Boot 4.1.1, Maven: xây dựng và chạy API; Spring Security kiểm tra JWT và quyền truy cập.
- Spring Data JPA, PostgreSQL: lưu và truy vấn dữ liệu; Flyway cập nhật cấu trúc bảng khi khởi động.
- Docker Compose: chạy backend và PostgreSQL trên máy tiếp nhận dự án. Frontend chạy bằng Node.js/npm.

## 5. Kiểm thử tải
**Kaggle Notebook:** https://www.kaggle.com/code/ndtthanh/event-registration-test/notebook

Sử dụng K6 v2.3.0 trên Kaggle Notebook (CPU) để kiểm thử backend Spring Boot và PostgreSQL chạy bằng Docker Compose trên máy phát triển. Các script kiểm thử và SQL phục vụ việc chuẩn bị dữ liệu được lưu trong thư mục `load-tests/` của repository để thuận tiện cho việc tái sử dụng trong Pha 2. Môi trường Kaggle được cấu hình với **Accelerator: None** và **Internet: On**. K6 truy cập backend thông qua Cloudflare Tunnel. Do đó, kết quả thời gian phản hồi có thể chịu ảnh hưởng của kết nối mạng và môi trường triển khai.

Hai kịch bản được thực hiện bằng `load-test.js` và `contention-test.js`.

### 5.1. Kiểm thử tải thông thường

Tăng tải đến 50 VUs, kiểm tra xem danh sách, chi tiết sự kiện và đăng ký.

| Chỉ số | Kỳ vọng | Kết quả |
|---|---|---:|
| Tổng yêu cầu HTTP | - | 2.712 |
| Thông lượng | - | 19,97 req/s |
| HTTP response time p95 | < 500 ms | 286,82 ms |
| Registration p95 | < 800 ms | 820,55 ms |
| Đăng ký thành công | 50/50 | 50/50 |
| Lỗi hệ thống | < 1% | 0% |
| Phản hồi đăng ký ngoài dự kiến | 0 | 0 |

HTTP response time p95 đạt ngưỡng đề ra. Tuy nhiên, Registration p95 vượt nhẹ ngưỡng 800 ms, cho thấy hiệu năng API đăng ký cần được cải thiện.

### 5.2. Kiểm thử tranh chấp chỗ cuối

50 người dùng cùng đăng ký khi sự kiện chỉ còn hai chỗ.

| Chỉ số | Kỳ vọng | Kết quả |
|---|---|---:|
| Đăng ký thành công | Đúng 2 | 2 |
| HTTP 409 dự kiến | 48 | 48 |
| Registration p95 | < 800 ms | 462,07 ms |
| Lỗi hệ thống | < 1% | 0% |
| Phản hồi ngoài dự kiến | 0 | 0 |

Kết quả phù hợp với sức chứa: hai lượt đăng ký thành công, 48 yêu cầu còn lại nhận HTTP 409 do xung đột nghiệp vụ. Dữ liệu sau kiểm thử đúng như kỳ vọng.

### 5.3. Đánh giá

Hệ thống xử lý đúng tình huống tranh chấp số chỗ trong kịch bản thử nghiệm. Tuy nhiên, Registration p95 ở bài tải thông thường chưa đạt ngưỡng mục tiêu 800 ms. Pha 2 cần tiếp tục phân tích và cải thiện hiệu năng API đăng ký. Ngoài ra, cần mở rộng kiểm thử các nghiệp vụ chính, bao gồm:

- **Danh sách chờ và lời mời:** kiểm tra thứ tự FIFO, chấp nhận/từ chối lời mời và xử lý lời mời hết hạn.
- **Tính nhất quán dữ liệu:** kiểm tra đăng ký đồng thời, hủy đăng ký và giải phóng chỗ để bảo đảm sức chứa không bị vượt quá.
- **Phân quyền và bảo mật:** kiểm tra xác thực JWT, quyền sở hữu sự kiện và quyền truy cập lịch sử đăng ký.
- **Kiểm thử hồi quy:** chạy lại các kiểm thử nghiệp vụ sau khi thay đổi mã nguồn để phát hiện lỗi phát sinh.

**Lưu ý:** Các ngưỡng < 500 ms, < 800 ms và < 1% là mục tiêu dùng để đánh giá kết quả kiểm thử, không phải khẳng định rằng toàn bộ hệ thống đã đáp ứng mọi quy mô tải thực tế.

## 6. Chạy nhanh và tài liệu API

Cần Docker Desktop (Linux containers), Node.js/npm và PowerShell. Tại thư mục gốc, tạo khóa JWT cho lần chạy này rồi bật backend và database:

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($bytes); $env:JWT_SECRET = [Convert]::ToBase64String($bytes) }
finally { $rng.Dispose() }
docker compose up --build -d
docker compose ps
```

Đợi PostgreSQL báo `healthy` và backend chạy. Trong terminal thứ hai, chạy giao diện:

```powershell
cd frontend
npm ci
npm run dev
```

Mở địa chỉ Vite in ra terminal (thường là http://localhost:5173); frontend mặc định gọi API ở `http://localhost:8080/api`. Nếu chạy backend ngoài Docker, cần JDK 21, PostgreSQL và các biến `JWT_SECRET`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`; xem `compose.yaml` và `backend/src/main/resources/application.yaml`. **Không chạy `docker compose down -v` nếu muốn giữ dữ liệu.** Đổi khóa JWT sẽ làm các phiên đăng nhập cũ mất hiệu lực; mật khẩu database trong Compose chỉ dành cho local.

- [Đặc tả OpenAPI độc lập](specs/openapi.yaml) để xem trước; khi backend chạy: [Swagger UI](http://localhost:8080/swagger-ui/index.html) và [OpenAPI JSON](http://localhost:8080/v3/api-docs).
- [Hợp đồng API và ghi chú nghiệp vụ](docs/api-contract.md), [kiến trúc](docs/architecture.md).
- Kiểm tra frontend: trong `frontend/` chạy `npm run build`, `npm run lint`. Kiểm tra backend: trong `backend/` chạy `.\mvnw.cmd test`. Các integration test dùng PostgreSQL 16 Testcontainers và thư mục upload thử nghiệm trong `target`; cần Docker đang hoạt động. Test không dùng datasource của DB phát triển. Scheduler nền được mock trong integration tests; các kịch bản expiry gọi service với thời điểm xác định.
