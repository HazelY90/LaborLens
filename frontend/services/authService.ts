import { apiClient, ApiError } from "./apiClient";
import type { AccessResponse, LoginInput, RegisterInput, PasswordInput, UserProfile } from "../utils/apiTypes";

/** These functions are called from browser components or the future AuthContext. */
export function register(
  input: RegisterInput,
  signal?: AbortSignal
) {
  return apiClient.request<UserProfile>("/auth/register", { method: "POST", auth: false, body: input, signal });
}

export function login(input: LoginInput) {
  return apiClient.startSession(() => apiClient.request<AccessResponse>("/auth/login", {
    method: "POST", auth: false, body: input,
  }));
}

/** Restore the in-memory session after a page reload, or explicitly refresh it. */
export function refresh() {
  return apiClient.refresh();
}

export async function getMe(signal?: AbortSignal) {
  const user = await apiClient.request<UserProfile>("/auth/me", { signal });
  apiClient.updateUser(user);

  return user;
}

export async function rename(username: string) {
  const user = await apiClient.request<UserProfile>("/auth/me/username", { method: "PATCH", body: { username } });
  apiClient.updateUser(user);

  return user;
}

export async function changePassword(input: PasswordInput) {
  await apiClient.request<void>("/auth/me/password", { method: "PATCH", body: input });
  apiClient.clearSession();
}

export async function deleteAccount(currentPassword: string) {
  await apiClient.request<void>("/auth/me", { method: "DELETE", body: { currentPassword } });
  apiClient.clearSession();
}

export function logout() {
  return apiClient.endSession(async () => {
    try {
      await apiClient.request<void>("/auth/logout", { method: "POST", auth: false });
    } catch (error) {
      // An expired/revoked cookie already represents a signed-out session.
      if (!(error instanceof ApiError && error.status === 401)) throw error;
    }
  });
}
