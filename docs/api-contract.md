# Event API

REST API dùng JSON; thumbnail dùng multipart/form-data. Bearer JWT xác định tài khoản, không lấy userId
hoặc ownerId từ request để thay danh tính. USER là role tài khoản; owner là quan hệ với từng Event.
Các API quản lý Event yêu cầu đăng nhập và kiểm tra owner.

## Endpoint

| Method | Path | Input | Thành công |
|---|---|---|---|
| GET | /api/events | keyword, city, category, sort, page, size | 200 PageResult<Event> |
| GET | /api/events/{id} | — | 200 Event; Event đã xóa trả 404 |
| POST | /api/events | EventInput | 201 Event; người tạo là owner |
| GET | /api/me/events | page, size | 200 owned PageResult, gồm Event đã xóa |
| GET | /api/me/events/{id} | — | 200 owner detail, gồm Event đã xóa |
| PUT | /api/events/{id} | EventInput | 200 Event |
| PATCH | /api/events/{id}/status | OPEN/CLOSED | 200 Event |
| DELETE | /api/events/{id} | reason | 204 |
| POST | /api/events/{id}/thumbnail | multipart file | 200 Event |
| GET | /api/events/{id}/registrations | — | 200 Participant[] |
| GET | /api/events/{id}/waitlist | — | 200 owner waitlist view |
| GET | /api/events/{id}/audit-logs | — | 200 AuditResponse[] |
| DELETE | /api/events/{id}/registrations/{registrationId} | reason | 204 |

Các API Registration yêu cầu đăng nhập và dùng profile của người gọi:

| Method | Path | Input | Thành công |
|---|---|---|---|
| POST | /api/events/{id}/registrations | không cần body | 200 RegistrationResponse |
| DELETE | /api/registrations/{id} | — | 204; registration của người gọi |
| GET | /api/me/registrations | — | 200 RegistrationResponse[] |

Auth: POST /api/auth/register trả 201 UserResponse; POST /api/auth/login trả accessToken/tokenType.
GET /api/users/me trả profile. Frontend gọi /users/me sau khi nhận token để nạp thông tin tài khoản.

## EventInput

```json
{
  "title": "Community workshop",
  "description": "An open event",
  "organizerName": "Community",
  "city": "HANOI",
  "category": "EDUCATION",
  "startTime": "2040-11-15T09:00:00",
  "endTime": "2040-11-15T17:00:00",
  "capacity": 100,
  "offerTtlMinutes": 10
}
```

Title/organizerName bắt buộc, tối đa 255 ký tự; description có thể trống. City: HANOI/HCMC. Category:
TECHNOLOGY/SPORTS/EDUCATION/MUSIC. capacity >= 1, endTime > startTime. Khi tạo, startTime phải ở tương lai.
TTL trong khoảng 5–30 phút; POST mặc định 10, PUT yêu cầu cung cấp. PUT thay các trường chỉnh sửa, không chuyển owner.
PATCH nhận {"status":"CLOSED"}; DELETE và remove nhận {"reason":"Venue unavailable"}, lý do dài 1–1000 ký tự.
Datetime Event là giờ địa phương Asia/Ho_Chi_Minh, không có hậu tố Z.

Response gồm id, title, description, organizerName, organizerUserId, city, category, startTime, endTime,
capacity, registeredCount, heldCount, remainingSlots, status, effectiveStatus, offerTtlMinutes, thumbnailUrl,
deletedAt, createdAt, updatedAt, canRegister. remainingSlots = capacity - registeredCount - heldCount.
effectiveStatus = CLOSED nếu bị đóng, đã xóa hoặc now >= startTime. canRegister thể hiện khả dụng chung;
server vẫn kiểm tra điều kiện của từng user. Public không trả thông tin participants hoặc audit.

Page bắt đầu từ 0; size mặc định 12, tối đa 50. sort hỗ trợ startTime, title, createdAt, capacity tăng dần,
với id để phân định khi bằng nhau. PageResult gồm content, totalElements, totalPages, number, size,
first, last, empty.

## Quy tắc Event và participant

Các thao tác cập nhật lấy khóa Event trước khi kiểm tra thời gian, quyền và số chỗ. Capacity mới phải
>= registeredCount + heldCount. Có lịch sử đăng ký vẫn được sửa metadata/capacity nếu đáp ứng điều kiện.
Event đã bắt đầu không được thay startTime hoặc mở lại đăng ký. Trước giờ bắt đầu, startTime mới phải ở
tương lai và không sớm hơn deadline pending do provider waitlist trả về. Đổi TTL giữ nguyên deadline đã cấp.

Owner được tự đăng ký. Remove áp dụng cho registration CONFIRMED thuộc đúng Event, trước startTime và
có lý do. Thao tác chuyển REMOVED, lưu người/thời điểm/lý do loại, giảm registeredCount và gọi handover
hook nếu có. Retry trước startTime với registration đã REMOVED trả 204, không giảm count hoặc ghi audit
lần nữa. Người REMOVED bị từ chối đăng ký lại bằng 409. Người dùng được hủy registration của mình trước
startTime, kể cả khi Event đang CLOSED tạm.

Xóa mềm đặt deletedAt và CLOSED, giữ registrations và audit. Owner vẫn đọc được detail, participants và
audit sau xóa; Event bị ẩn khỏi public. Retry DELETE giữ nguyên dữ liệu và lý do cũ. Event seed chưa có
owner được xem công khai, chưa được quản lý cho đến khi xác minh mapping.

## Tích hợp Waitlist

Main chưa có module Waitlist/SlotOffer. offerTtlMinutes được kiểm tra trong khoảng 5–30 phút và lưu vào Event; owner waitlist
view hiện rỗng. EventOfferLifecycle nhận Event đã khóa và now, chạy trong transaction của thao tác Event:

- allocateAvailable: tăng capacity, mở lại hoặc remove giải phóng chỗ;
- close: Event đã được đặt CLOSED;
- delete: Event đã có deletedAt và CLOSED;
- latestPendingExpiry, waitingCount, waitingList: đọc trạng thái liên quan.

Provider của module Waitlist thực hiện FIFO, revoke/restore, expiry và deadline. Provider không mở
REQUIRES_NEW khi caller đang giữ khóa Event. Lỗi provider làm rollback Event, registration và audit.
Nếu heldCount > 0 mà thiếu provider, thao tác cần xử lý offer trả 409; sửa metadata không tác động offer
vẫn được phép. Main chưa có queue nên heldCount = 0 và Event CRUD/remove hoạt động bình thường.
Contract test dùng provider giả lập để kiểm tra việc gọi hook và rollback. FIFO, accept/decline, expiry
và scheduler cần được kiểm thử khi tích hợp module Waitlist.

## Thumbnail và audit

Upload dùng field file, MIME image/jpeg, image/png hoặc image/webp, kích thước 1 byte–5 MB. Server sinh
UUID với đuôi theo MIME. Kiểm tra định dạng dựa trên MIME; chưa có quét virus. Ảnh cũ được xóa sau commit;
rollback xóa ảnh mới; lỗi I/O giữa chừng được dọn file viết dở. Filesystem dùng thao tác bù riêng với
transaction DB. thumbnailUrl tương đối với backend; public GET /api/uploads/thumbnails/{file} không cần token.

AuditLogService dùng bảng audit_logs từ V5, ghi details trước/sau thay đổi cho các action
EVENT_CREATE/UPDATE/OPEN/CLOSE/DELETE và PARTICIPANT_REMOVE trong cùng transaction Event.
AuditResponse gồm id, action, reason, createdAt, actorUserId, actorName, details. createdAt là Instant UTC.
Remove lưu registrationId, userId và trạng thái trước/sau trong details; target vẫn là Event.
Nhật ký xác nhận/hủy đăng ký cần được bổ sung ở module Registration và Audit.

Mã lỗi: 400 input/parameter; 401 chưa xác thực; 403 không phải owner; 404 không tồn tại/đã ẩn;
409 xung đột nghiệp vụ; 500 lỗi ngoài dự kiến. Lỗi Auth dùng code/message/traceId; lỗi Event dùng
status/message/timestamp. Token sai/hết hạn được bỏ qua ở endpoint public; endpoint cần đăng nhập vẫn trả
401. Các route /api/admin/** bị chặn; controller Event cũ đã được bỏ.
