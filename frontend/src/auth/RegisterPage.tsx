import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import axios from "axios";
import apiClient, { setToken } from "../api/client";

function RegisterPage() {
    const navigate = useNavigate();
    const [email, setEmail] = useState("");
    const [displayName, setDisplayName] = useState("");
    const [phone, setPhone] = useState("");
    const [password, setPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");

    async function submit(event: FormEvent) {
        event.preventDefault();
        if (password !== confirmPassword) { setError("Passwords do not match"); return; }
        setLoading(true);
        setError("");
        try {
            await apiClient.post("/auth/register", {email, displayName, phone, password, confirmPassword});
            const { data } = await apiClient.post<{accessToken: string}>("/auth/login", {email, password});
            setToken(data.accessToken);
            navigate("/");
        } catch (failure) {
            setError(axios.isAxiosError(failure) ? failure.response?.data?.message ?? "Registration failed" : "Registration failed");
        } finally { setLoading(false); }
    }

    return <main className="page auth-page"><h1>Register</h1>
        <form className="auth-form" onSubmit={submit}>
            <label>Email<input type="email" required autoComplete="email" value={email} onChange={e => setEmail(e.target.value)} /></label>
            <label>Display name<input required maxLength={100} value={displayName} onChange={e => setDisplayName(e.target.value)} /></label>
            <label>Phone (optional)<input type="tel" value={phone} onChange={e => setPhone(e.target.value)} /></label>
            <label>Password<input type="password" required minLength={6} autoComplete="new-password" value={password} onChange={e => setPassword(e.target.value)} /></label>
            <label>Confirm password<input type="password" required autoComplete="new-password" value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} /></label>
            {error && <p role="alert" className="auth-error">{error}</p>}
            <button type="submit" disabled={loading}>{loading ? "Creating account…" : "Register"}</button>
        </form><p>Already registered? <Link to="/login">Login</Link></p>
    </main>;
}

export default RegisterPage;
