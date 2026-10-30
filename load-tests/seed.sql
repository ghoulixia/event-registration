BEGIN;

-- =====================================================
-- 1. CLEAN UP DATA CŨ
-- =====================================================

-- Xóa offer thuộc hai sự kiện test trước.
DELETE FROM slot_offers
WHERE event_id IN (10001, 10002)
   OR user_id IN (
    SELECT id FROM users
    WHERE email LIKE 'k6_user_%@test.com'
       OR email = 'k6_organizer@test.com'
);

-- Xóa waitlist entries của hai sự kiện test.
DELETE FROM waitlist_entries
WHERE event_id IN (10001, 10002)
   OR user_id IN (
    SELECT id FROM users
    WHERE email LIKE 'k6_user_%@test.com'
       OR email = 'k6_organizer@test.com'
);

-- Xóa registrations của hai sự kiện test.
DELETE FROM registrations
WHERE event_id IN (10001, 10002)
   OR user_id IN (
    SELECT id FROM users
    WHERE email LIKE 'k6_user_%@test.com'
       OR email = 'k6_organizer@test.com'
);

-- Xóa audit logs do các tài khoản test tạo.
DELETE FROM audit_logs
WHERE actor_user_id IN (
    SELECT id FROM users
    WHERE email LIKE 'k6_user_%@test.com'
       OR email = 'k6_organizer@test.com'
)
   OR (
    target_type = 'EVENT'
        AND target_id IN (10001, 10002)
    );

-- Xóa sự kiện test.
DELETE FROM events
WHERE id IN (10001, 10002);

-- Xóa organizer test.
DELETE FROM users
WHERE email = 'k6_organizer@test.com';

-- Xóa các tài khoản test cũ.
DELETE FROM users
WHERE email LIKE 'k6_user_%@test.com';


-- =====================================================
-- 2. SEED 200 USERS
-- =====================================================

INSERT INTO users (
    email,
    display_name,
    password_hash,
    phone,
    role
)
SELECT
    'k6_user_' || i || '@test.com',
    'K6 Tester ' || i,
    '$2a$10$FE57efrGd6hMUAwAlEuYB.BHPruFSkqAyBGCPd4ypGBJnCNmf4Dwi',
    '0900000' || LPAD(i::text, 3, '0'),
    'USER'
FROM generate_series(1, 200) AS i;


-- =====================================================
-- 3. SEED ORGANIZER
-- =====================================================

-- Không gán ID 9999 thủ công để tránh đụng identity sequence.
INSERT INTO users (
    email,
    display_name,
    password_hash,
    phone,
    role
)
VALUES (
           'k6_organizer@test.com',
           'K6 Organizer',
           '$2a$10$FE57efrGd6hMUAwAlEuYB.BHPruFSkqAyBGCPd4ypGBJnCNmf4Dwi',
           '0999999999',
           'USER'
       );


-- =====================================================
-- 4. EVENT 10001: AMPLE CAPACITY
-- =====================================================

INSERT INTO events (
    id,
    organizer_user_id,
    organizer_name,
    title,
    description,
    city,
    category,
    start_time,
    end_time,
    capacity,
    registered_count,
    held_count,
    offer_ttl_minutes,
    status
)
VALUES (
           10001,
           (SELECT id FROM users WHERE email = 'k6_organizer@test.com'),
           'K6 Organizer',
           'k6_event_ample_capacity',
           'Event dùng để test throughput đăng ký thông thường',
           'HANOI',
           'TECHNOLOGY',
           NOW() + INTERVAL '7 days',
           NOW() + INTERVAL '8 days',
           1000,
           0,
           0,
           10,
           'OPEN'
       );


-- =====================================================
-- 5. EVENT 10002: CONTENTION - CHÍNH XÁC 2 SLOT TRỐNG
-- =====================================================

INSERT INTO events (
    id,
    organizer_user_id,
    organizer_name,
    title,
    description,
    city,
    category,
    start_time,
    end_time,
    capacity,
    registered_count,
    held_count,
    offer_ttl_minutes,
    status
)
VALUES (
           10002,
           (SELECT id FROM users WHERE email = 'k6_organizer@test.com'),
           'K6 Organizer',
           'k6_event_high_contention',
           'Event dùng để test concurrency và race condition',
           'HCMC',
           'EDUCATION',
           NOW() + INTERVAL '3 days',
           NOW() + INTERVAL '4 days',
           10,
           8,
           0,
           10,
           'OPEN'
       );


-- =====================================================
-- 6. TẠO 8 REGISTRATIONS THẬT CHO EVENT 10002
-- =====================================================

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
    SELECT 'k6_user_' || i || '@test.com'
    FROM generate_series(101, 108) AS i
);


-- =====================================================
-- 7. ĐỒNG BỘ BỘ ĐẾM TỪ DATABASE
-- =====================================================

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


-- =====================================================
-- 8. ĐỒNG BỘ IDENTITY SEQUENCES
-- =====================================================

SELECT setval(
               pg_get_serial_sequence('events', 'id'),
               GREATEST(COALESCE((SELECT MAX(id) FROM events), 1), 1),
               true
       );

SELECT setval(
               pg_get_serial_sequence('users', 'id'),
               GREATEST(COALESCE((SELECT MAX(id) FROM users), 1), 1),
               true
       );


-- =====================================================
-- 9. KIỂM TRA KẾT QUẢ
-- =====================================================

SELECT
    e.id,
    e.title,
    e.capacity,
    e.registered_count,
    e.held_count,
    e.capacity - e.registered_count - e.held_count AS available_slots,
    (
        SELECT COUNT(*)
        FROM registrations r
        WHERE r.event_id = e.id
          AND r.status = 'CONFIRMED'
    ) AS actual_confirmed
FROM events e
WHERE e.id IN (10001, 10002)
ORDER BY e.id;

COMMIT;