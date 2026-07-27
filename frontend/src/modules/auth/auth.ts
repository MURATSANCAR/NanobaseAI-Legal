import {
  User,
  UserManager,
  WebStorageStateStore,
  type UserManagerSettings,
} from "oidc-client-ts";

let manager: UserManager | undefined;

function settings(): UserManagerSettings {
  const authority =
    process.env.NEXT_PUBLIC_OIDC_ISSUER ??
    "http://localhost:8081/realms/specai";
  return {
    authority,
    client_id: process.env.NEXT_PUBLIC_OIDC_CLIENT_ID ?? "specai-portal",
    redirect_uri: `${window.location.origin}/`,
    post_logout_redirect_uri: `${window.location.origin}/`,
    response_type: "code",
    scope: "openid profile email roles tenant",
    automaticSilentRenew: true,
    userStore: new WebStorageStateStore({ store: window.localStorage }),
  };
}

function userManager(): UserManager {
  if (!manager) manager = new UserManager(settings());
  return manager;
}

export async function restoreSession(): Promise<User | null> {
  const query = new URLSearchParams(window.location.search);
  if (query.has("code") && query.has("state")) {
    const user = await userManager().signinRedirectCallback();
    window.history.replaceState({}, document.title, window.location.pathname);
    return user;
  }
  const user = await userManager().getUser();
  return user && !user.expired ? user : null;
}

export function signIn(): Promise<void> {
  return userManager().signinRedirect();
}

export function signOut(): Promise<void> {
  return userManager().signoutRedirect();
}

export function displayName(user: User): string {
  const profileName = user.profile.name;
  return typeof profileName === "string" && profileName.trim()
    ? profileName
    : (user.profile.email ?? user.profile.sub);
}

export function realmRoles(user: User): string[] {
  const access = user.profile.realm_access as { roles?: string[] } | undefined;
  return access?.roles ?? [];
}
