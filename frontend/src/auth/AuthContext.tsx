import { useEffect, useState } from "react";
import type { ReactNode } from "react";
import api, { AUTH_CHANGED, TOKEN_KEY } from "../api/client";
import { AuthContext } from "./session";
import type { AuthSession } from "./session";

type ProfileResult = { token: string | null; user: AuthSession["user"] | null };
export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setCurrentToken] = useState(() => sessionStorage.getItem(TOKEN_KEY));
  const [profile, setProfile] = useState<ProfileResult>({ token: null, user: null });
  useEffect(() => {
    const refresh = () => setCurrentToken(sessionStorage.getItem(TOKEN_KEY));
    window.addEventListener(AUTH_CHANGED, refresh);
    return () => window.removeEventListener(AUTH_CHANGED, refresh);
  }, []);
  useEffect(() => {
    if (!token) return;
    const controller = new AbortController();
    api.get<AuthSession["user"]>("/users/me", { signal: controller.signal })
      .then(({ data }) => { if (!controller.signal.aborted) setProfile({ token, user: data }); })
      .catch(() => { if (!controller.signal.aborted) setProfile({ token, user: null }); });
    return () => controller.abort();
  }, [token]);
  const session = token && profile.token === token && profile.user ? { accessToken: token, user: profile.user } : null;
  return <AuthContext.Provider value={{ session, loading: !!token && profile.token !== token }}>{children}</AuthContext.Provider>;
}
