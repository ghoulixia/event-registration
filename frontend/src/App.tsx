import { Route, Routes } from "react-router-dom";

import Navbar from "./shared/Navbar";
import LoginPage from "./auth/LoginPage";
import RegisterPage from "./auth/RegisterPage";
import EventsPage from "./events/EventsPage";
import MyRegistrationsPage from "./registrations/MyRegistrationsPage";
import { AuthProvider } from "./auth/AuthContext";
import RequireSignIn from "./shared/RequireSignIn";
import EventDetailPage from "./events/EventDetailPage";
import MyEventsPage from "./events/MyEventsPage";
import EventFormPage from "./events/EventFormPage";
import EventParticipantsPage from "./events/EventParticipantsPage";
import EventAuditPage from "./events/EventAuditPage";

function App() {
  return (
      <AuthProvider>
        <Navbar />

        <Routes>
          <Route path="/" element={<EventsPage />} />
          <Route path="/events" element={<EventsPage />} />
          <Route path="/events/:id" element={<EventDetailPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route
              path="/my-registrations"
              element={<MyRegistrationsPage />}
          />
          <Route path="/my-events" element={<RequireSignIn><MyEventsPage /></RequireSignIn>} />
          <Route path="/my-events/new" element={<RequireSignIn><EventFormPage /></RequireSignIn>} />
          <Route path="/my-events/:id/edit" element={<RequireSignIn><EventFormPage /></RequireSignIn>} />
          <Route path="/my-events/:id/participants" element={<RequireSignIn><EventParticipantsPage /></RequireSignIn>} />
          <Route path="/my-events/:id/audit-logs" element={<RequireSignIn><EventAuditPage /></RequireSignIn>} />
          <Route path="*" element={<main className="page"><h1>Page not found</h1></main>} />
        </Routes>
      </AuthProvider>
  );
}

export default App;
