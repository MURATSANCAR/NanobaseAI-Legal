import http from "k6/http";
import { check } from "k6";

export const options = {
  scenarios: {
    uploads: {
      executor: "constant-arrival-rate",
      rate: 5,
      timeUnit: "1s",
      duration: "2m",
      preAllocatedVUs: 10,
      maxVUs: 50,
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.02"],
    http_req_duration: ["p(95)<3000"],
  },
};

const fixture = open("../fixtures/synthetic-specification.pdf", "b");

export default function () {
  const response = http.post(
    `${__ENV.BASE_URL}/api/v1/tenders/${__ENV.PROJECT_ID}/documents`,
    {
      file: http.file(fixture, `synthetic-${__VU}-${__ITER}.pdf`, "application/pdf"),
      documentType: "TECHNICAL_SPECIFICATION",
      includedInAnalysis: "true",
    },
    { headers: { Authorization: `Bearer ${__ENV.ACCESS_TOKEN}` } },
  );
  check(response, {
    "upload accepted or safely throttled": (value) =>
      [201, 202, 422, 429, 503].includes(value.status),
  });
}
