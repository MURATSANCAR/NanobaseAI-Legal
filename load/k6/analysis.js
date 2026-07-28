import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  scenarios: {
    analysis: {
      executor: "per-vu-iterations",
      vus: 10,
      iterations: 5,
      maxDuration: "10m",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.05"],
    http_req_duration: ["p(95)<5000"],
  },
};

export default function () {
  const headers = {
    Authorization: `Bearer ${__ENV.ACCESS_TOKEN}`,
    "Content-Type": "application/json",
  };
  for (const path of [
    `documents/${__ENV.DOCUMENT_ID}/requirement-extractions`,
    `tenders/${__ENV.PROJECT_ID}/compliance-analyses`,
    `tenders/${__ENV.PROJECT_ID}/risk-analyses`,
  ]) {
    const response = http.post(`${__ENV.BASE_URL}/api/v1/${path}`, "{}", { headers });
    check(response, {
      "analysis accepted or backpressured": (value) =>
        [200, 201, 202, 409, 422, 429, 503].includes(value.status),
    });
    sleep(0.5);
  }
}
