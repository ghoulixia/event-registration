BEGIN;

-- Khóa hai sự kiện trong transaction.
SELECT id
FROM events
WHERE id IN (10001, 10002)
    FOR UPDATE;

-- 1. Xóa các slot offer thuộc hai sự kiện.
DELETE FROM slot_offers
WHERE event_id IN (10001, 10002);

-- 2. Xóa waitlist entries thuộc hai sự kiện.
DELETE FROM waitlist_entries
WHERE event_id IN (10001, 10002);

-- 3. Xóa các registration thuộc hai sự kiện.
DELETE FROM registrations
WHERE event_id IN (10001, 10002);

-- 4. Khôi phục trạng thái ban đầu của sự kiện 10001.
UPDATE events
SET registered_count = 0,
    held_count = 0,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 10001;

-- 5. Khôi phục 8 đăng ký CONFIRMED cho sự kiện 10002.
-- Dùng lại 8 người dùng đã được tạo bởi seed.sql.
INSERT INTO registrations (
    user_id,
    event_id,
    status,
    registered_at
)
SELECT
    u.id,
    10002,
    'CONFIRMED',
    CURRENT_TIMESTAMP
FROM users u
WHERE u.email IN (
                  'k6_user_101@test.com',
                  'k6_user_102@test.com',
                  'k6_user_103@test.com',
                  'k6_user_104@test.com',
                  'k6_user_105@test.com',
                  'k6_user_106@test.com',
                  'k6_user_107@test.com',
                  'k6_user_108@test.com'
    );

-- 6. Đồng bộ bộ đếm với dữ liệu thực tế.
UPDATE events e
SET registered_count = (
    SELECT COUNT(*)
    FROM registrations r
    WHERE r.event_id = e.id
      AND r.status = 'CONFIRMED'
),
    held_count = (
        SELECT COUNT(*)
        FROM slot_offers so
        WHERE so.event_id = e.id
          AND so.status = 'PENDING'
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE e.id IN (10001, 10002);

COMMIT;