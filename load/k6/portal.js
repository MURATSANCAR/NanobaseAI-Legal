import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  scenarios: {
    portal: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "30s", target: 20 },
        { duration: "3m", target: 20 },
        { duration: "30s", target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<1000"],
  },
};

const baseUrl = __ENV.BASE_URL;
const token = __ENV.ACCESS_TOKEN;
const headers = { Authorization: `Bearer ${token}` };

export default function () {
  const projects = http.get(`${baseUrl}/api/v1/tenders`, { headers });
  check(projects, { "project list succeeds": (response) => response.status === 200 });
  if (projects.status === 200) {
    const body = projects.json();
    const first = body.content?.[0];
    if (first) {
      check(http.get(`${baseUrl}/api/v1/tenders/${first.id}`, { headers }), {
        "project detail succeeds": (response) => response.status === 200,
      });
      check(http.get(`${baseUrl}/api/v1/tenders/${first.id}/documents`, { headers }), {
        "document list succeeds": (response) => response.status === 200,
      });
      check(http.get(`${baseUrl}/api/v1/tenders/${first.id}/risks`, { headers }), {
        "risk list is authorized": (response) => [200, 404].includes(response.status),
      });
    }
  }
  sleep(1);
}
