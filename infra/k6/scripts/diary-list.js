import http from "k6/http";
import { check, sleep } from "k6";

const BASE = "http://host.docker.internal:8080";

export const options = {
  vus: 10,
  duration: "30s",
  thresholds: {
    http_req_failed: ["rate<0.01"],
    "http_req_duration{name:diary-list}": ["p(95)<500"],
  },
};

// 테스트 시작 시 1회만 실행
export function setup() {
  const res = http.post(
    `${BASE}/api/auth/login`,
    JSON.stringify({ email: "k6@test.com", password: "k6pass1234" }),
    { headers: { "Content-Type": "application/json" } },
  );
  if (res.status !== 200) {
    throw new Error(`login failed: ${res.status} ${res.body}`);
  }
  return { token: res.json("accessToken") };
}

export default function (data) {
  const headers = {
    Authorization: `Bearer ${data.token}`,
    "Content-Type": "application/json",
  };

  const res = http.get(
    `${BASE}/api/diaries?from=0&to=9999999999999&sort=desc`,
    { headers, tags: { name: "diary-list" } }, // tags 로 지표 쪼개기
  );

  check(res, {
    "list 200": (r) => r.status === 200,
    "has items": (r) => Array.isArray(r.json("items")),
  });

  sleep(1);
}
