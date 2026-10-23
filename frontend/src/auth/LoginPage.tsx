import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import axios from "axios";
import apiClient, { setToken } from "../api/client";

function LoginPage() {
    const navigate = useNavigate();
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");

    async function submit(event: FormEvent) {
        event.preventDefault();
        setLoading(true);
        setError("");
        try {
            const { data } = await apiClient.post<{accessToken: string}>("/auth/login", {email, password});
            setToken(data.accessToken);
            navigate("/");
        } catch (failure) {
            setError(axios.isAxiosError(failure) ? failure.response?.data?.message ?? "Login failed" : "Login failed");
        } finally {
            setLoading(false);
        }
    }

    return <main className="page auth-page"><h1>Login</h1>
        <form onSubmit={submit} className="auth-form">
            <label>Email<input type="email" required autoComplete="email" value={email} onChange={e => setEmail(e.target.value)} /></label>
            <label>Password<input type="password" required autoComplete="current-password" value={password} onChange={e => setPassword(e.target.value)} /></label>
            {error && <p role="alert" className="auth-error">{error}</p>}
            <button disabled={loading} type="submit">{loading ? "Signing in…" : "Login"}</button>
        </form><p>New here? <Link to="/register">Register</Link></p>
    </main>;
}

export default LoginPage;
