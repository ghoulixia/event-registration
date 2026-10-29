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
TTL trong khoảng 5–30 phút; POST mặc định 10 khi bỏ trường này, gửi null trả 400; PUT yêu cầu cung cấp. PUT thay các trường chỉnh sửa, không chuyển owner.
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

Owner không được tự đăng ký hoặc vào hàng chờ sự kiện của mình. Remove áp dụng cho registration CONFIRMED thuộc đúng Event, trước startTime và
có lý do. Thao tác chuyển REMOVED, lưu người/thời điểm/lý do loại, giảm registeredCount và gọi handover
hook nếu có. Retry trước startTime với registration đã REMOVED trả 204, không giảm count hoặc ghi audit
lần nữa. Người REMOVED bị từ chối đăng ký lại bằng 409. Người dùng được hủy registration của mình trước
startTime, kể cả khi Event đang CLOSED tạm.

Xóa mềm đặt deletedAt và CLOSED, giữ registrations và audit. Owner vẫn đọc được detail, participants và
audit sau xóa; Event bị ẩn khỏi public. Retry DELETE giữ nguyên dữ liệu và lý do cũ. Event seed chưa có
owner được xem công khai, chưa được quản lý cho đến khi xác minh mapping.

## Tích hợp Waitlist

Module Waitlist/SlotOffer đã được tích hợp qua EventOfferLifecycleAdapter. Các API sau yêu cầu JWT:

| Method | Path | Input | Thành công |
|---|---|---|---|
| POST | /api/events/{id}/waitlist | không cần body | 200 WaitlistEntryResponse |
| DELETE | /api/events/{id}/waitlist/me | — | 204 |
| GET | /api/me/waitlist | — | 200 MyWaitlistResponse |
| POST | /api/offers/{id}/accept | không cần body | 204 |
| POST | /api/offers/{id}/decline | không cần body | 204 |

MyWaitlistResponse gồm entries và serverNow. Chỉ trả lượt WAITING/OFFERED; entry WAITING có position và
không có offer, entry OFFERED có offer chứa id/status/expiresAt. Người gọi chỉ được phản hồi offer của mình;
offer không thuộc tài khoản trả 404.

Người REMOVED không được vào hàng chờ. Khi gặp dữ liệu chờ cũ của người đã bị loại, bộ phân bổ kết thúc
lượt không hợp lệ và tiếp tục FIFO. Nếu offer cũ đã được cấp trước đó, accept trả 409 nhưng vẫn commit
việc thu hồi offer, trả chỗ và phân bổ lại cho người đủ điều kiện.

TTL cấu hình 5–30 phút; hạn thực tế không vượt startTime. Quá hạn hoặc từ chối sẽ phân bổ tất cả chỗ còn
trống cho người chờ đủ điều kiện. Scheduler mặc định nghỉ 60 giây sau khi lần chạy trước hoàn tất,
không bảo đảm xử lý đúng ngay giây hết hạn; accept luôn kiểm tra thời hạn tại backend.

EventOfferLifecycle nhận Event đã khóa và now, tham gia transaction của thao tác tăng capacity,
đóng/mở/xóa Event hoặc loại participant. Lỗi phải rollback thay đổi nghiệp vụ và audit tương ứng.
Các API accept/decline xử lý kết quả hết hạn/thu hồi theo transaction của SlotOfferService.

## Thumbnail và audit

Upload dùng field file, MIME image/jpeg, image/png hoặc image/webp, kích thước 1 byte–5 MB. Server sinh
UUID với đuôi theo MIME. Kiểm tra định dạng dựa trên MIME; chưa có quét virus. Ảnh cũ được xóa sau commit;
rollback xóa ảnh mới; lỗi I/O giữa chừng được dọn file viết dở. Filesystem dùng thao tác bù riêng với
transaction DB. thumbnailUrl tương đối với backend; public GET /api/uploads/thumbnails/{file} không cần token.

AuditLogService dùng bảng audit_logs từ V5, ghi details trước/sau thay đổi cho các action
EVENT_CREATE/UPDATE/OPEN/CLOSE/DELETE và PARTICIPANT_REMOVE trong cùng transaction Event.
AuditResponse gồm id, action, reason, createdAt, actorUserId, actorName, details. createdAt là Instant UTC.
Remove lưu registrationId, userId và trạng thái trước/sau trong details; target vẫn là Event.
REGISTRATION_CONFIRM/REGISTRATION_CANCEL được ghi ở module Registration/Offers. GET /api/me/registrations
trả lịch sử của người gọi; GET /api/events/{id}/audit-logs chỉ cho owner đọc nhật ký Event.

Mã lỗi: 400 input/parameter; 401 chưa xác thực; 403 không phải owner; 404 không tồn tại/đã ẩn;
409 xung đột nghiệp vụ; 500 lỗi ngoài dự kiến. Lỗi Auth dùng code/message/traceId; lỗi Event dùng
status/message/timestamp. Token sai/hết hạn được bỏ qua ở endpoint public; endpoint cần đăng nhập vẫn trả
401. Các route /api/admin/** bị chặn; controller Event cũ đã được bỏ.
