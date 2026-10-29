import { Link, useNavigate } from "react-router-dom";
import { setToken } from "../api/client";
import { useAuth } from "../auth/session";
export default function Navbar() {
  const { session } = useAuth();
  const navigate = useNavigate();
  return <nav className="navbar">
    <Link to="/" className="brand">Event Registration</Link>
    <div className="nav-links">
      <Link to="/events">Events</Link><Link to="/my-events">My events</Link>
      {session ? <><Link to="/my-registrations">My registrations</Link><span>{session.user.displayName}</span>
        <button onClick={() => { setToken(null); navigate("/"); }}>Sign out</button></> :
        <><Link to="/login">Sign in</Link><Link to="/register">Register</Link></>}
    </div>
  </nav>;
}
