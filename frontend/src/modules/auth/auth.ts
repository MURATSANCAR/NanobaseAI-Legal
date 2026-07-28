export type AuthSession = {
  access_token: string;
  expires_at: number;
  profile: {
    sub: string;
    email?: string;
    name?: string;
    realm_access?: { roles?: string[] };
    tenant_id?: string;
  };
};

const STORAGE_KEY = "specai.local.session";

function apiBase(): string {
  return process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
}

function readStored(): AuthSession | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as AuthSession;
    if (!parsed?.access_token || !parsed?.expires_at) return null;
    return parsed;
  } catch {
    return null;
  }
}

function writeStored(session: AuthSession | null): void {
  if (!session) {
    window.localStorage.removeItem(STORAGE_KEY);
    return;
  }
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
}

export async function restoreSession(): Promise<AuthSession | null> {
  const session = readStored();
  if (!session) return null;
  if (session.expires_at * 1000 <= Date.now()) {
    writeStored(null);
    return null;
  }
  return session;
}

export async function signIn(email: string, password: string): Promise<AuthSession> {
  const response = await fetch(`${apiBase()}/api/v1/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify({ email, password }),
  });
  if (!response.ok) {
    let detail = "Giriş başarısız.";
    try {
      const body = (await response.json()) as { detail?: string; title?: string };
      detail = body.detail ?? body.title ?? detail;
    } catch {
      /* ignore */
    }
    throw new Error(detail);
  }
  const body = (await response.json()) as {
    accessToken: string;
    expiresInSeconds: number;
    email: string;
    displayName: string;
    roles: string[];
    tenantId: string;
  };
  const session: AuthSession = {
    access_token: body.accessToken,
    expires_at: Math.floor(Date.now() / 1000) + body.expiresInSeconds,
    profile: {
      sub: body.email,
      email: body.email,
      name: body.displayName,
      tenant_id: body.tenantId,
      realm_access: { roles: body.roles },
    },
  };
  writeStored(session);
  return session;
}

export async function signOut(): Promise<void> {
  writeStored(null);
}

export function displayName(session: AuthSession): string {
  const profileName = session.profile.name;
  return typeof profileName === "string" && profileName.trim()
    ? profileName
    : (session.profile.email ?? session.profile.sub);
}

export function realmRoles(session: AuthSession): string[] {
  return session.profile.realm_access?.roles ?? [];
}
