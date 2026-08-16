import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  vus: 1,
  duration: "5s",
};

export default function () {
  const res = http.post(
    "http://host.docker.internal:8080/api/auth/login",
    JSON.stringify({ email: "k6@test.com", password: "k6pass1234" }),
    { headers: { "Content-Type": "application/json" } },
  );

  check(res, {
    "status 200": (r) => r.status === 200,
    "has accessToken": (r) => r.json("accessToken") !== undefined,
  });

  sleep(1);
}
