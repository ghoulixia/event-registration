# Tài liệu Ghi chú Nghiệp vụ (Q&A cho BTL)

Tài liệu này lưu trữ các giải thích bằng tiếng Việt về thiết kế và mã nguồn của module Sự kiện (TV2), giúp bạn tham khảo và trả lời vấn đáp (Q&A) với giảng viên.

## 1. Tầng Database & Migration (Flyway)
* **`V2__events.sql`**: Khởi tạo bảng `events`. Thiết lập các ràng buộc dữ liệu (CHECK constraints) ngay từ DB để đảm bảo tính toàn vẹn (Defense in depth) như `capacity > 0`, `end_time > start_time`. Đồng thời đã cấu hình sẵn các index (chỉ mục) cho các trường hay được tìm kiếm và lọc (`city`, `category`, `start_time`, `deleted_at`).
* **`V4__seed_events.sql`**: Chứa dữ liệu mẫu (Seed Data) gồm 12 sự kiện phong phú để thuận tiện cho việc dev, demo, và load test sau này.

## 2. Tầng Entity & Repository (Data Layer)
* **`Event.java`**: Class thực thể mapping với bảng `events`. Sử dụng các Annotation của JPA. Khai thác tính năng `@PrePersist` và `@PreUpdate` để hệ thống tự động gán ngày giờ tạo/cập nhật mà không cần code logic thủ công.
* **`EventRepository.java`**: Nơi thực thi các truy vấn DB. Áp dụng kỹ thuật truy vấn động bằng `@Query` của Spring Data JPA để lọc sự kiện theo `keyword`, `city`, `category`. Các câu query luôn kèm điều kiện `deleted_at IS NULL` để bỏ qua các sự kiện đã bị xóa mềm (Soft Delete).

## 3. Tầng DTO & Exception (Transfer & Error Handling)
* **`CreateEventRequest` & `UpdateEventRequest`**: Sử dụng Bean Validation (`@NotBlank`, `@Min`, `@NotNull`) để kiểm tra tính hợp lệ của dữ liệu đầu vào ngay khi request tới Controller. Các thông báo lỗi (`message`) được định nghĩa sẵn.
* **`EventResponse`**: Trả về dữ liệu cho Client. Thay vì trả Entity, DTO này che giấu các trường nhạy cảm và tính toán thêm các trường "ảo" (như `effectiveStatus` tính dựa trên thời gian thực tại, `remainingSlots` tính bằng sức chứa trừ đi số người đã đăng ký).
* **`ResourceNotFoundException`, `BusinessRuleException`...**: Các Custom Exception dùng để ném ra các lỗi nghiệp vụ riêng biệt.
* **`GlobalExceptionHandler`**: Áp dụng pattern `@RestControllerAdvice` để bắt toàn bộ các Exception văng ra từ hệ thống và chuyển chúng thành format JSON chuẩn (`ErrorResponse`) với mã HTTP Status phù hợp (như 404 cho Không tìm thấy, 400 cho Lỗi nghiệp vụ hoặc Validation).

## 4. Tầng Service (Business Logic Layer)
* **`EventService`**: Là trái tim của ứng dụng (KHÔNG phải là Architecture Sinkhole). Nơi đây xử lý mọi quy tắc nghiệp vụ khắt khe: 
  * Không cho phép sức chứa mới nhỏ hơn số lượng đã đăng ký.
  * Không cho phép đặt ngày bắt đầu trong quá khứ nếu sự kiện đang mở.
  * Tính toán logic phân trang, lọc sự kiện an toàn.
* **`ImageStorageService`**: Chuyên trách quản lý file upload (thumbnail). Kiểm tra định dạng đuôi (JPG, PNG) và dung lượng file không quá 5MB. Tránh việc rò rỉ mã xử lý I/O hệ điều hành ra ngoài Service.

## 5. Tầng Controller (API Layer)
* **`EventController` (Public) & `AdminEventController` (Private)**: Tiếp nhận HTTP Request. Gọi tầng Service và trả về dữ liệu DTO. 
* Tích hợp Swagger / OpenAPI 3: Tất cả API đều được gắn thẻ `@Operation` và `@ApiResponse` để tự động sinh tài liệu phân tích nghiệp vụ rõ ràng, đáp ứng yêu cầu của giảng viên.

## 6. Cấu hình (Config)
* **`WebConfig.java`**: Phục vụ việc mapping thư mục chứa ảnh trên ổ cứng ra đường dẫn URL public `/api/uploads/thumbnails/**` để frontend có thể hiển thị.
