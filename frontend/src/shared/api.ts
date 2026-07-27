export type FieldError = {
  field: string;
  message: string;
};

export type ApiProblem = {
  type?: string;
  title: string;
  status: number;
  code?: string;
  detail: string;
  instance?: string;
  correlationId?: string;
  fieldErrors?: FieldError[];
};

export class ApiError extends Error {
  constructor(public readonly problem: ApiProblem) {
    super(problem.detail);
  }
}

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export async function apiRequest<T>(
  path: string,
  accessToken: string,
  init: RequestInit = {},
): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Authorization", `Bearer ${accessToken}`);
  headers.set("X-Correlation-ID", crypto.randomUUID());
  if (init.body && !(init.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers,
    cache: "no-store",
  });
  if (!response.ok) {
    let problem: ApiProblem;
    try {
      problem = (await response.json()) as ApiProblem;
    } catch {
      problem = {
        title: "İstek tamamlanamadı",
        status: response.status,
        detail: "Sunucu geçerli bir hata yanıtı döndürmedi.",
        correlationId: response.headers.get("X-Correlation-ID") ?? undefined,
      };
    }
    throw new ApiError(problem);
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}
