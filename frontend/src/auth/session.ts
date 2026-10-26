import { createContext, useContext } from "react";
export type AuthSession = {
  accessToken: string;
  user: { id: number; email: string; displayName: string; phone?: string | null };
};
export const AuthContext = createContext<{ session: AuthSession | null; loading: boolean } | null>(null);
export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error("AuthProvider is required.");
  return value;
}
