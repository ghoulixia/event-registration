import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/session";

export default function RequireSignIn({ children }: { children: ReactNode }) {
  const { session, loading } = useAuth();
  if (loading) return <main className="page"><p>Loading account…</p></main>;
  if (!session) return <main className="page">
    <h1>Sign in to continue</h1>
    <p><Link to="/login">Sign in</Link> to manage your events and registrations.</p>
  </main>;
  return children;
}
