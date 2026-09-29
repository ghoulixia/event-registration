import { Route, Routes } from "react-router-dom";

import Navbar from "./shared/Navbar";
import LoginPage from "./auth/LoginPage";
import RegisterPage from "./auth/RegisterPage";
import EventsPage from "./events/EventsPage";
import MyRegistrationsPage from "./registrations/MyRegistrationsPage";

function App() {
  return (
      <>
        <Navbar />

        <Routes>
          <Route path="/" element={<EventsPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route
              path="/my-registrations"
              element={<MyRegistrationsPage />}
          />
        </Routes>
      </>
  );
}

export default App;