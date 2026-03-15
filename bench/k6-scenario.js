// k6 scenario: full user flow benchmark
// Usage: k6 run bench/k6-scenario.js --env BASE_URL=http://localhost:8080 --env PREFIX=/api/v1
//        k6 run bench/k6-scenario.js --env BASE_URL=http://localhost:3000 --env PREFIX=/api

import http from "k6/http";
import { check, sleep } from "k6";
import { Rate, Trend } from "k6/metrics";

const BASE = __ENV.BASE_URL || "http://localhost:8080";
const PREFIX = __ENV.PREFIX || "/api/v1";

// Custom metrics
const errorRate = new Rate("errors");
const registerDuration = new Trend("register_duration");
const loginDuration = new Trend("login_duration");
const videoListDuration = new Trend("video_list_duration");

export const options = {
  stages: [
    { duration: "30s", target: 50 }, // ramp up
    { duration: "1m", target: 200 }, // steady
    { duration: "30s", target: 500 }, // peak
    { duration: "30s", target: 0 }, // ramp down
  ],
  thresholds: {
    http_req_duration: ["p(95)<2000"],
    errors: ["rate<0.1"],
  },
};

export default function () {
  const uniqueId = `${__VU}_${__ITER}_${Date.now()}`;
  const username = `bench_${uniqueId}`;
  const password = "Bench1234!";
  const headers = { "Content-Type": "application/json" };

  // 1. Register
  const registerRes = http.post(
    `${BASE}${PREFIX}/auth/register`,
    JSON.stringify({ username, password }),
    { headers }
  );
  registerDuration.add(registerRes.timings.duration);

  const registerOk = check(registerRes, {
    "register: status 200": (r) => r.status === 200,
  });
  errorRate.add(!registerOk);

  if (!registerOk) {
    sleep(0.5);
    return;
  }

  // Extract token (handle both Go and Rust response formats)
  let token;
  try {
    const body = JSON.parse(registerRes.body);
    token = body.data?.accessToken || body.data?.access_token;
  } catch {
    errorRate.add(true);
    return;
  }

  const authHeaders = {
    "Content-Type": "application/json",
    Authorization: `Bearer ${token}`,
  };

  // 2. Get video list
  const videosRes = http.get(`${BASE}${PREFIX}/videos`, {
    headers: authHeaders,
  });
  videoListDuration.add(videosRes.timings.duration);
  check(videosRes, { "videos: status 200": (r) => r.status === 200 });

  // 3. Get profile
  const meRes = http.get(`${BASE}${PREFIX}/auth/me`, {
    headers: authHeaders,
  });
  check(meRes, { "me: status 200": (r) => r.status === 200 });

  // 4. Login (with same credentials)
  const loginRes = http.post(
    `${BASE}${PREFIX}/auth/login`,
    JSON.stringify({ username, password }),
    { headers }
  );
  loginDuration.add(loginRes.timings.duration);
  check(loginRes, { "login: status 200": (r) => r.status === 200 });

  sleep(0.3);
}
