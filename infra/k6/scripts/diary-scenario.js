import http from "k6/http";
import { check, sleep, group } from "k6";
import { Trend } from "k6/metrics";

const BASE = "http://host.docker.internal:8080";

// 커스텀 지표 — E2E 플로우 한 바퀴 시간을 별도 측정
const flowDuration = new Trend("flow_duration");

export const options = {
  scenarios: {
    diary_crud: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "30s", target: 10 }, // warm-up
        { duration: "1m", target: 50 }, // ramp to 50
        { duration: "2m", target: 100 }, // peak load
        { duration: "30s", target: 0 }, // ramp-down
      ],
      gracefulRampDown: "10s",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    "http_req_duration{name:diary-list}": ["p(95)<500"],
    "http_req_duration{name:diary-create}": ["p(95)<800"],
    "http_req_duration{name:diary-update}": ["p(95)<800"],
    "http_req_duration{name:diary-delete}": ["p(95)<500"],
    flow_duration: ["p(95)<5000"],
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

export default function (data) {
  const headers = {
    Authorization: `Bearer ${data.token}`,
    "Content-Type": "application/json",
  };
  const flowStart = Date.now();

  // 1) 목록 조회
  group("list diaries", () => {
    const res = http.get(
      `${BASE}/api/diaries?from=0&to=9999999999999&sort=desc`,
      { headers, tags: { name: "diary-list" } },
    );
    check(res, { "list 200": (r) => r.status === 200 });
  });
  sleep(1);

  // 2) 작성
  let diaryId;
  group("create diary", () => {
    const body = JSON.stringify({
      date: Date.now(),
      content: `k6 부하 테스트 ${__VU}/${__ITER}`, // VU·iter 번호 포함
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
  sleep(2);

  // 3) 수정 (작성이 실패했으면 스킵)
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
    sleep(1);

    // 4) 삭제
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
