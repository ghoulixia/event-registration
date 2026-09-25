## 1. Tech stack

- Java 21
- Spring Boot 4.1.1
- Maven
- PostgreSQL 16
- Spring Data JPA / Hibernate
- Flyway
- Spring Security
- Docker / Docker Compose
- React + Vite (frontend sẽ được hoàn thiện sau)

## 2. Yêu cầu trước khi chạy

Cần cài:

```text
Git
Docker Desktop
JDK 21
```

Không bắt buộc cài PostgreSQL trực tiếp trên Windows vì PostgreSQL chạy bằng Docker.

Kiểm tra:

```powershell
java -version
docker --version
docker compose version
git --version
```

Java nên hiển thị version 21.

## 3. Clone project

```powershell
git clone <REPOSITORY_URL>
cd event-registration
```

## 4. Khởi động PostgreSQL

Đứng tại thư mục root có `compose.yaml`:

```powershell
docker compose up -d
```

Kiểm tra:

```powershell
docker compose ps
```

PostgreSQL cần ở trạng thái `Up`.

Cấu hình local hiện tại:

```text
Database: event_registration
Username: event
Password: event123
Host: localhost
Port: 5432
```

Để vào PostgreSQL trực tiếp:

```powershell
docker exec -it event-registration-postgres psql -U event -d event_registration
```

Một số lệnh hữu ích trong `psql`:

```sql
\dt
\d users
\q
```

## 5. Chạy backend

### Windows PowerShell

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

### macOS / Linux

```bash
cd backend
./mvnw spring-boot:run
```

Backend mặc định chạy tại:

```text
http://localhost:8080
```