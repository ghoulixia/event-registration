# Chạy và kiểm tra phần Auth (TV1)

Hướng dẫn này dành cho PowerShell trên Windows. Mở terminal tại **thư mục gốc repo**; cần Docker Desktop đang chạy, Node.js/npm và JDK để chạy Maven tests. Các lệnh `npm` phải chạy trong `frontend` (repo root không có `package.json`). Không cần chia sẻ `agent/progress.md`.

## 1. JWT_SECRET là gì?

Backend dùng JWT HS256 để ký access token khi đăng nhập và kiểm tra chữ ký token ở những API cần xác thực. `JWT_SECRET` là **khóa bí mật dùng cho cả hai việc**; ai có khóa này có thể tự tạo token hợp lệ. Vì vậy không dùng một khóa mặc định chung, không ghi khóa vào repo, ảnh chụp màn hình hay log. Backend yêu cầu một chuỗi base64 giải mã được thành **ít nhất 32 byte**. Base64 chỉ là cách biểu diễn byte, **không phải mã hóa bảo vệ khóa**.

Tại repo root, tạo khóa ngẫu nhiên và đưa vào biến môi trường của **PowerShell hiện tại** (tương thích Windows PowerShell không có hàm tĩnh `GetBytes(32)`):

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $rng.GetBytes($bytes)
    $env:JWT_SECRET = [Convert]::ToBase64String($bytes)
} finally {
    $rng.Dispose()
}
```

Lệnh này không in khóa. Tạo lại khóa khi khởi động backend sẽ làm các token cũ **không còn hợp lệ**, kể cả khi token chưa hết hạn; logout/login lại trên frontend nếu gặp 401 sau khi đổi khóa. Muốn giữ phiên qua lần khởi động backend, dùng lại cùng một khóa được quản lý an toàn ngoài repo. Mặc định token hết hạn sau 1 giờ (`JWT_EXPIRATION_MS=3600000`). Biến môi trường chỉ tồn tại trong terminal đã đặt nó: hãy chạy `docker compose` ở **cùng terminal**.

## 2. Khởi động backend và frontend

Tại repo root, sau khi đặt `JWT_SECRET`:

```powershell
docker compose up --build -d
docker compose ps
```

Đợi `postgres` là `healthy` và `backend` là `Up` trước khi mở http://localhost:8080/v3/api-docs và http://localhost:8080/swagger-ui/index.html. PostgreSQL giữ dữ liệu trên volume; cổng host của DB là `5433`, backend là `8080`. Tài khoản DB `event/event123` trong Compose chỉ dành cho local development, không dùng để triển khai công khai. **Không chạy `docker compose down -v` trên project đang dùng** vì sẽ xóa volume dữ liệu.

Mở terminal thứ hai tại repo root:

```powershell
cd frontend
npm install
npm run dev
```

Mở URL Vite hiển thị trong terminal, thông thường http://localhost:5173. Giữ terminal này chạy khi kiểm tra giao diện. Nếu frontend không kết nối được backend, kiểm tra `docker compose ps` và URL `http://localhost:8080/v3/api-docs` trước.

## 3. Kiểm tra giao diện

1. Mở **Register**; nhập email chưa dùng, display name, mật khẩu từ 6 ký tự và confirm password giống nhau. Sau đăng ký, app đăng nhập và về `/`; Navbar hiện tên cùng nút **Logout**.
2. Refresh trang: Navbar vẫn hiện tên vì token ở `sessionStorage` được kiểm tra qua `GET /api/users/me`.
3. Bấm **Logout**: Navbar trở lại Login/Register; refresh vẫn không đăng nhập.
4. Mở **Login**, nhập sai mật khẩu: thấy thông báo lỗi. Nhập đúng: về `/` và thấy tên.
5. Đăng ký lại cùng email (không phân biệt chữ hoa/thường): nhận lỗi trùng email. Mật khẩu xác nhận không khớp: nhận lỗi validation.

Không dùng email cũ khi muốn thử đăng ký thành công: dữ liệu PostgreSQL tồn tại sau restart.

## 4. Kiểm tra API bằng PowerShell

Chạy ở repo root hoặc bất kỳ thư mục nào khi backend đang hoạt động:

```powershell
curl.exe -s -o NUL -w "public events: %{http_code}`n" http://localhost:8080/api/events
curl.exe -s -o NUL -w "anonymous me: %{http_code}`n" http://localhost:8080/api/users/me
curl.exe -s -o NUL -w "anonymous legacy POST: %{http_code}`n" -X POST http://localhost:8080/api/admin/events
```

Kết quả mong đợi: **200, 401, 401**. `/api/admin/events` là API legacy đang bị bảo vệ, **không phải owner API chuẩn**; đừng tạo event ở đó để suy ra owner permissions.

Có thể kiểm tra register/login/me bằng request thật mà không in token ra terminal:

```powershell
$email = "auth-$([guid]::NewGuid().ToString('N'))@example.com"
$password = 'local-test-123'
$base = 'http://localhost:8080/api'
$register = @{ email = $email; displayName = 'Auth Tester'; password = $password; confirmPassword = $password } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$base/auth/register" -ContentType 'application/json' -Body $register
$login = @{ email = $email; password = $password } | ConvertTo-Json
$session = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' -Body $login
Invoke-RestMethod -Method Get -Uri "$base/users/me" -Headers @{ Authorization = "Bearer $($session.accessToken)" }
```

Register trả `id/email/displayName/phone` (không có hash); `/users/me` trả đúng email vừa tạo. Login trả `accessToken` và `tokenType=Bearer`; không in `$session.accessToken`, không gửi token cho người khác. Lệnh PowerShell này lưu mật khẩu test trong lịch sử terminal; **chỉ dùng mật khẩu giả dành cho local test**.

Kiểm tra migration và định dạng hash trên PostgreSQL local (thay `your-email@example.com` nếu cần):

```powershell
docker compose exec -T postgres psql -U event -d event_registration -c "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank;"
docker compose exec -T postgres psql -U event -d event_registration -c "SELECT email, role, left(password_hash, 2) = chr(36) || '2' AS bcrypt_prefix FROM users WHERE email = 'your-email@example.com';"
```

Flyway phải có V1–V6 với `success=t`; tài khoản mới có `role=USER`, `bcrypt_prefix=t`. Truy vấn không in toàn bộ password hash.

## 5. Chạy kiểm tra tự động

Mở terminal ở repo root và bảo đảm PostgreSQL đang chạy:

```powershell
cd backend
.\mvnw.cmd test
cd ..\frontend
npm run build
```

Maven tests dùng PostgreSQL thật ở `localhost:5433`; không dùng DB giả để kết luận về Flyway hoặc transaction. Nếu chạy app backend trực tiếp trên Windows thay vì Docker, phải đặt `JWT_SECRET` trong terminal chạy backend và bảo đảm PostgreSQL sẵn sàng.

## Giới hạn hiện tại

Audit V5 và audit writer đã có, nhưng chưa thể kiểm tra owner đọc audit trả 403 hoặc log DELETE/REMOVE end-to-end: phần TV2 hiện chưa có `events.organizer_user_id`/owner mutation từ JWT; phần TV3 chưa có participant removal. Không coi audit owner đã hoàn tất chỉ vì auth và tests ở trên chạy thành công. Khi TV2/TV3 tích hợp xong, cần nối audit hooks trong cùng transaction và chạy lại các kịch bản owner A/B, reason và rollback.
