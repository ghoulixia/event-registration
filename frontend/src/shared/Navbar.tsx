import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import apiClient, { AUTH_CHANGED, setToken, TOKEN_KEY } from "../api/client";

function Navbar() {
    const navigate = useNavigate();
    const [name, setName] = useState<string | null>(null);

    useEffect(() => {
        let active = true;
        async function refresh() {
            const token = sessionStorage.getItem(TOKEN_KEY);
            if (!token) { setName(null); return; }
            try {
                const {data} = await apiClient.get<{displayName: string}>("/users/me");
                if (active && sessionStorage.getItem(TOKEN_KEY) === token) setName(data.displayName);
            } catch { if (active) setName(null); }
        }
        void refresh();
        window.addEventListener(AUTH_CHANGED, refresh);
        return () => { active = false; window.removeEventListener(AUTH_CHANGED, refresh); };
    }, []);

    function logout() {
        setToken(null);
        navigate("/");
    }

    return <nav className="navbar">
        <Link to="/" className="brand">Campus Event</Link>
        <div className="nav-links">
            <Link to="/">Events</Link>
            <Link to="/my-registrations">My Registrations</Link>
            {name ? <><span>Hi, {name}</span><button type="button" onClick={logout}>Logout</button></>
                : <><Link to="/login">Login</Link><Link to="/register">Register</Link></>}
        </div>
    </nav>;
}

export default Navbar;
