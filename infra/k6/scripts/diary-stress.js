import http from "k6/http";
import { check, group } from "k6";
import { Trend } from "k6/metrics";

const BASE = "http://host.docker.internal:8080";

const flowDuration = new Trend("flow_duration");

export const options = {
    scenarios: {
        diary_stress: {
            // open model: 서버가 느려져도 정해진 속도로 계속 요청을 밀어넣음
            // → 병목이 생기면 대기열이 쌓이면서 latency가 눈에 띄게 폭발
            executor: "ramping-arrival-rate",
            startRate: 10, // 초당 iteration 수 (1 iter = HTTP 4회)
            timeUnit: "1s",
            preAllocatedVUs: 100,
            maxVUs: 500, // 서버가 밀리면 VU를 여기까지 늘려서 rate 유지
            stages: [
                { duration: "30s", target: 30 }, // warm-up (~120 req/s)
                { duration: "1m", target: 80 }, // 풀 고갈 시작 지점 탐색
                { duration: "2m", target: 150 }, // peak (~600 req/s) — 확실히 포화
                { duration: "30s", target: 0 },
            ],
        },
    },
    thresholds: {
        http_req_failed: ["rate<0.05"],
        "http_req_duration{name:diary-list}": ["p(95)<500"],
        "http_req_duration{name:diary-create}": ["p(95)<800"],
        "http_req_duration{name:diary-update}": ["p(95)<800"],
        "http_req_duration{name:diary-delete}": ["p(95)<500"],
        flow_duration: ["p(95)<5000"],
        // maxVUs까지 다 써서 rate를 못 지키면 서버 한계 도달 신호
        dropped_iterations: ["count<100"],
    },
};

export function setup() {
    const res = http.post(
        `${BASE}/api/auth/login`,
        JSON.stringify({ email: "k6@test.com", password: "k6pass1234" }),
        { headers: { "Content-Type": "application/json" } },
    );
    if (res.status !== 200) throw new Error(`login failed: ${res.status}`);
    return { token: res.json("accessToken") };
}

// sleep 전부 제거 — VU가 쉬지 않고 요청해야 동시성이 실제로 올라감
export default function (data) {
    const headers = {
        Authorization: `Bearer ${data.token}`,
        "Content-Type": "application/json",
    };
    const flowStart = Date.now();

    group("list diaries", () => {
        const res = http.get(
            `${BASE}/api/diaries?from=0&to=9999999999999&sort=desc`,
            { headers, tags: { name: "diary-list" } },
        );
        check(res, { "list 200": (r) => r.status === 200 });
    });

    let diaryId;
    group("create diary", () => {
        const body = JSON.stringify({
            date: Date.now(),
            content: `k6 스트레스 ${__VU}/${__ITER}`,
            emotionId: (__ITER % 5) + 1,
        });
        const res = http.post(`${BASE}/api/diaries`, body, {
            headers,
            tags: { name: "diary-create" },
        });
        check(res, {
            "create 201": (r) => r.status === 201,
            "has id": (r) => r.json("id") !== undefined,
        });
        diaryId = res.json("id");
    });

    if (diaryId) {
        group("update diary", () => {
            const body = JSON.stringify({
                date: Date.now(),
                content: `수정됨 ${__VU}`,
                emotionId: 5,
            });
            const res = http.put(`${BASE}/api/diaries/${diaryId}`, body, {
                headers,
                tags: { name: "diary-update" },
            });
            check(res, { "update 200": (r) => r.status === 200 });
        });

        group("delete diary", () => {
            const res = http.del(`${BASE}/api/diaries/${diaryId}`, null, {
                headers,
                tags: { name: "diary-delete" },
            });
            check(res, { "delete 204": (r) => r.status === 204 });
        });
    }

    flowDuration.add(Date.now() - flowStart);
}