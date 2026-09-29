# Event Registration System

Hệ thống đăng ký sự kiện, phát triển theo **Layered Architecture** với một frontend React và một backend Spring Boot.

## 1. Tech stack

### Backend
- Java 21
- Spring Boot 4.1.1
- Maven
- Spring Web
- Spring Data JPA / Hibernate
- Spring Validation
- Spring Security
- Flyway

### Database / Infrastructure
- PostgreSQL 16
- Docker / Docker Compose

### Frontend
- React
- TypeScript
- Vite
- Axios
- React Router

## 2. Yêu cầu trước khi chạy

Cần cài:

```text
Git
Docker Desktop
JDK 21
Node.js 22 LTS
npm
```

Kiểm tra:

```powershell
java -version
node -v
npm -v
docker --version
docker compose version
git --version
```


## 3. Khởi động PostgreSQL

Đứng tại thư mục root có `compose.yaml`:

```powershell
docker compose up -d
```

Kiểm tra:

```powershell
docker compose ps
```

PostgreSQL cần ở trạng thái `Up`.

Cấu hình development hiện tại:

```text
Database: event_registration
Username: event
Password: event123
Host: localhost
Host port: 5433
Container port: 5432
```

Để vào PostgreSQL trực tiếp:

```powershell
docker exec -it event-registration-postgres psql -U event -d event_registration
```

Một số lệnh hữu ích:

```sql
\dt
\d users
\q
```

> Credential trên chỉ dùng cho development local.

## 4. Chạy backend

### Windows PowerShell

Từ root project:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Backend mặc định chạy tại:

```text
http://localhost:8080
```

Khi terminal hiển thị:

```text
Started EventRegistrationApplication
```

backend đã khởi động thành công.

### Flyway

Flyway tự chạy migration khi Spring Boot khởi động.

Migration nằm tại:

```text
backend/src/main/resources/db/migration/
```

## 5. Chạy frontend sau khi backend đã chạy

Giữ terminal backend đang chạy và mở **một terminal PowerShell mới**.

Từ root project:

```powershell
cd frontend
npm install
npm run dev
```

Khi Vite khởi động thành công sẽ hiển thị URL tương tự:

```text
http://localhost:5173/
```

Mở URL đó trên trình duyệt.

Frontend hiện được cấu hình gọi backend tại:

```text
http://localhost:8080/api
```

qua file:

```text
frontend/src/api/client.ts
```

Luồng chạy local:

```text
Browser
  ↓
React / Vite :5173
  ↓ HTTP
Spring Boot :8080
  ↓ JDBC
PostgreSQL Docker :5433
```

### Lần đầu clone project

Sau khi pull hoặc clone frontend, chỉ cần:

```powershell
cd frontend
npm install
npm run dev
```

`npm install` sẽ cài dependency dựa trên `package.json` và `package-lock.json`.

## 6. Kiến trúc backend

Backend sử dụng Layered Architecture:

```text
Client
  ↓
Controller
  ↓
Service
  ↓
Repository
  ↓
PostgreSQL
```

Package root:

```text
com.team.eventregistration
```

Cấu trúc chính:

```text
backend/src/main/java/com/team/eventregistration/
├── EventRegistrationApplication.java
├── controller/
├── service/
├── repository/
├── entity/
├── dto/
├── security/
├── exception/
└── config/
```

Quy ước:
- `Controller`: nhận/trả HTTP, validate DTO và gọi Service.
- `Service`: chứa business logic.
- `Repository`: truy cập dữ liệu qua Spring Data JPA.
- `Entity`: ánh xạ dữ liệu PostgreSQL.
- `DTO`: dữ liệu request/response của API.
- `security`, `exception`, `config`: cấu hình dùng chung.

Controller không gọi Repository trực tiếp.