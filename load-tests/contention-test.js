import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const systemFailureRate = new Rate('contention_system_failure_rate');
const registrationDuration = new Trend('contention_req_duration');
const successCounter = new Counter('contention_successes');
const conflictCounter = new Counter('contention_409_conflicts');
const unexpectedCounter = new Counter('contention_unexpected_responses');

const BASE_URL = (__ENV.BASE_URL || 'https://land-blend-belong-dancing.trycloudflare.com/api').replace(/\/$/, '');
const EVENT_ID = __ENV.CONTENTION_EVENT_ID || '10002';
const VUS = Number(__ENV.VUS || 50);
const EMAIL_PREFIX = __ENV.LOGIN_EMAIL_PREFIX || 'k6_user_';
const PASSWORD = __ENV.LOGIN_PASSWORD || 'password123';

export const options = {
    scenarios: {
        last_two_slots: {
            executor: 'per-vu-iterations',
            vus: VUS,
            iterations: 1,
            maxDuration: '1m',
            exec: 'contentionFlow',
        },
    },

    thresholds: {
        // Phải có đúng 2 lượt đăng ký thành công.
        contention_successes: ['count==2'],

        // Không được có phản hồi ngoài dự kiến.
        contention_unexpected_responses: ['count==0'],

        // 95% request đăng ký hoàn tất dưới 800 ms.
        contention_req_duration: ['p(95)<800'],

        // Tỷ lệ phản hồi ngoài dự kiến phải dưới 1%.
        contention_system_failure_rate: ['rate<0.01'],
    },
};

function extractToken(res) {
    try {
        const body = res.json();
        return body.token
            || body.accessToken
            || body.data?.token
            || body.data?.accessToken;
    } catch (_) {
        return null;
    }
}

// Đăng nhập trước khi chạy bài test.
export function setup() {
    if (VUS < 2) {
        throw new Error('VUS phải ít nhất bằng 2.');
    }

    const tokens = [];

    for (let i = 1; i <= VUS; i++) {
        const email = `${EMAIL_PREFIX}${i}@test.com`;

        const res = http.post(
            `${BASE_URL}/auth/login`,
            JSON.stringify({
                email: email,
                password: PASSWORD,
            }),
            {
                headers: { 'Content-Type': 'application/json' },
                tags: { name: 'POST /auth/login' },
            }
        );

        const token = res.status === 200 ? extractToken(res) : null;

        if (token) {
            tokens.push(token);
        } else {
            console.error(`Login thất bại: ${email}, status=${res.status}`);
        }
    }

    if (tokens.length < VUS) {
        throw new Error(
            `Chỉ lấy được ${tokens.length}/${VUS} token. ` +
'Kiểm tra tài khoản seed, mật khẩu, endpoint login và cấu trúc response.'
);
}

console.log(`Đã lấy ${tokens.length} token.`);
console.log(`Sự kiện ${EVENT_ID} phải có chính xác 2 chỗ trống.`);
console.log('Tất cả tài khoản test phải chưa đăng ký sự kiện này.');

return { tokens: tokens };
}

// Mỗi VU gửi một request đăng ký vào cùng một sự kiện.
export function contentionFlow(data) {
    const token = data.tokens[(__VU - 1) % data.tokens.length];

    const res = http.post(
        `${BASE_URL}/events/${EVENT_ID}/registrations`,
        JSON.stringify({
            notes: 'K6 high contention test',
        }),
        {
            headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${token}`,
            },
            tags: {
                name: 'POST /events/{id}/registrations',
                workload: 'contention',
            },
        }
    );

    registrationDuration.add(res.timings.duration);

    const success = res.status === 200 || res.status === 201;
    const conflict = res.status === 409;
    const unexpected = !success && !conflict;

    successCounter.add(success ? 1 : 0);
    conflictCounter.add(conflict ? 1 : 0);
    unexpectedCounter.add(unexpected ? 1 : 0);
    systemFailureRate.add(unexpected);

    check(res, {
        'Status là 200, 201 hoặc 409': () => !unexpected,
    });
}