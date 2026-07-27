import { apiRequest } from "@/src/shared/api";

export type AuditEvent = {
  id: string;
  userId: string;
  eventType: string;
  entityType: string;
  entityId: string;
  beforeJson?: string;
  afterJson: string;
  correlationId: string;
  createdAt: string;
};

export const auditApi = {
  list: (token: string) =>
    apiRequest<{ content: AuditEvent[] }>(
      "/api/v1/audit-events?size=20&sort=createdAt,desc",
      token,
    ),
};
