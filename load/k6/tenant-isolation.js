import http from "k6/http";
import { check } from "k6";

export const options = {
  scenarios: {
    crossTenant: {
      executor: "constant-vus",
      vus: 20,
      duration: "2m",
    },
  },
  thresholds: {
    checks: ["rate==1"],
  },
};

export default function () {
  const response = http.get(
    `${__ENV.BASE_URL}/api/v1/documents/${__ENV.FOREIGN_DOCUMENT_ID}`,
    { headers: { Authorization: `Bearer ${__ENV.ACCESS_TOKEN}` } },
  );
  check(response, {
    "foreign document remains undisclosed": (value) =>
      [403, 404, 422].includes(value.status),
    "foreign document content is absent": (value) =>
      !value.body.includes(__ENV.FOREIGN_SENTINEL || "FOREIGN_TENANT_SECRET"),
  });
}
