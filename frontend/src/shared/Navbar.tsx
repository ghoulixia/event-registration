import { Link } from "react-router-dom";

function Navbar() {
    return (
        <nav className="navbar">
            <Link to="/" className="brand">
                Campus Event
            </Link>

            <div className="nav-links">
                <Link to="/">Events</Link>
                <Link to="/my-registrations">My Registrations</Link>
                <Link to="/login">Login</Link>
                <Link to="/register">Register</Link>
            </div>
        </nav>
    );
}

export default Navbar;