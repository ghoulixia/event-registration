import http from 'k6/http';
import {check, group, sleep} from 'k6';
import {Counter, Rate, Trend} from 'k6/metrics';

const systemFailureRate = new Rate('system_failure_rate');
const registrationSuccessRate = new Rate('registration_success_rate');
const businessConflictRate = new Rate('business_409_conflicts');
const registrationDuration = new Trend('registration_req_duration');
const unexpectedRegistrationResponses = new Counter('unexpected_registration_responses');

const BASE_URL = (__ENV.BASE_URL || 'https://land-blend-belong-dancing.trycloudflare.com/api').replace(/\/$/, '');
const EVENT_ID = __ENV.EVENT_ID || '10001';
const DETAIL_EVENT_ID = __ENV.DETAIL_EVENT_ID || EVENT_ID;
const TOTAL_USERS = Number(__ENV.TOTAL_USERS || 50);
const EMAIL_PREFIX = __ENV.LOGIN_EMAIL_PREFIX || 'k6_user_';
const PASSWORD = __ENV.LOGIN_PASSWORD || 'password123';
const CITY = __ENV.CITY || 'HANOI';

export const options = {
    scenarios: {
        browse_workload: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                {duration: '30s', target: 20},
                {duration: '1m', target: 50},
                {duration: '30s', target: 0},
            ],
            exec: 'browseFlow',
        },

        registration_workload: {
            executor: 'per-vu-iterations',
            vus: TOTAL_USERS,
            iterations: 1,
            maxDuration: '2m',
            startTime: '5s',
            exec: 'registrationFlow',
        },
    },

    thresholds: {
        http_req_duration: ['p(95)<500'],
        system_failure_rate: ['rate<0.01'],
        registration_req_duration: ['p(95)<800'],
        unexpected_registration_responses: ['count==0'],
    },
};

// Lấy JWT token từ phản hồi login.
// Nếu cấu trúc response khác, cần sửa hàm này theo API thực tế.
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

// Chạy một lần trước khi bắt đầu các scenario.
export function setup() {
    const tokens = [];

    for (let i = 1; i <= TOTAL_USERS; i++) {
        const email = `${EMAIL_PREFIX}${i}@test.com`;

        const res = http.post(
            `${BASE_URL}/auth/login`,
            JSON.stringify({
                email: email,
                password: PASSWORD,
            }),
            {
                headers: {'Content-Type': 'application/json'},
                tags: {name: 'POST /auth/login'},
            }
        );

        const token = res.status === 200 ? extractToken(res) : null;

        if (token) {
            tokens.push(token);
        } else {
            console.error(`Login thất bại: ${email}, status=${res.status}`);
        }
    }

    if (tokens.length < TOTAL_USERS) {
        throw new Error(
            `Chỉ lấy được ${tokens.length}/${TOTAL_USERS} token. ` +
            'Kiểm tra tài khoản seed, mật khẩu, endpoint login và cấu trúc response.'
        );
    }

    console.log(`Đăng nhập thành công ${tokens.length} tài khoản.`);
    return {tokens: tokens};
}

// Scenario 1: Đọc danh sách và chi tiết sự kiện.
export function browseFlow() {
    group('Public event browsing', function () {
        const listRes = http.get(
            `${BASE_URL}/events?page=0&size=10&city=${encodeURIComponent(CITY)}`,
            {tags: {name: 'GET /events'}}
        );

        let hasDataField = false;

        try {
            const body = listRes.json();
            hasDataField =
                body.content !== undefined ||
                body.data !== undefined;
        } catch (_) {
            hasDataField = false;
        }

        const listOk = check(listRes, {
            'GET events trả HTTP 200': (r) => r.status === 200,
            'GET events có trường dữ liệu': () => hasDataField,
        });

        systemFailureRate.add(!listOk);

        sleep(1);

        const detailRes = http.get(
            `${BASE_URL}/events/${DETAIL_EVENT_ID}`,
            {tags: {name: 'GET /events/{id}'}}
        );

        const detailOk = check(detailRes, {
            'GET event detail trả HTTP 200': (r) => r.status === 200,
        });

        systemFailureRate.add(!detailOk);

        sleep(1);
    });
}

// Scenario 2: Mỗi VU dùng một tài khoản và thử đăng ký một lần.
export function registrationFlow(data) {
    const token = data.tokens[(__VU - 1) % data.tokens.length];

    const res = http.post(
        `${BASE_URL}/events/${EVENT_ID}/registrations`,
        JSON.stringify({
            notes: 'K6 automated load test',
        }),
        {
            headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${token}`,
            },
            tags: {
                name: 'POST /events/{id}/registrations',
                workload: 'normal_registration',
            },
        }
    );

    registrationDuration.add(res.timings.duration);

    const success = res.status === 200 || res.status === 201;
    const conflict = res.status === 409;
    const unexpected = !success && !conflict;

    registrationSuccessRate.add(success);
    businessConflictRate.add(conflict);
    systemFailureRate.add(unexpected);
    unexpectedRegistrationResponses.add(unexpected ? 1 : 0);

    if (res.status === 409) {
        console.log(
            `Registration conflict: user=${__VU}, body=${res.body}`
        );
    }

    check(res, {
        'Đăng ký trả 200, 201 hoặc 409': () => !unexpected,
    });
}