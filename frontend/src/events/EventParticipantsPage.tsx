import { useCallback, useState } from "react";
import type { FormEvent } from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import { errorMessage } from "../api/client";
import { useRemote } from "../shared/useRemote";
import { eventApi, eventTime } from "./eventApi";

export default function EventParticipantsPage() {
  const { id } = useParams();
  const eventId = Number(id);
  const { state } = useLocation();
  const title = typeof state?.eventTitle === "string" ? state.eventTitle : `Event #${eventId}`;
  const [busy, setBusy] = useState<number | null>(null);
  const [actionError, setActionError] = useState("");
  const load = useCallback(async (signal: AbortSignal) => {
    const [participants, waitlist] = await Promise.all([
      eventApi.participants(eventId, signal), eventApi.waitlist(eventId, signal),
    ]);
    return { participants, waitlist };
  }, [eventId]);
  const { data, loading, error, reload } = useRemote(load);

  async function remove(form: FormEvent<HTMLFormElement>, registrationId: number) {
    form.preventDefault();
    const reason = String(new FormData(form.currentTarget).get("reason") ?? "").trim();
    if (!reason) { setActionError("A removal reason is required."); return; }
    setBusy(registrationId);
    setActionError("");
    try { await eventApi.removeParticipant(eventId, registrationId, reason); reload(); }
    catch (exception) { setActionError(errorMessage(exception)); reload(); }
    finally { setBusy(null); }
  }

  return <main className="page">
    <p><Link to="/my-events">My events</Link> · <Link to={`/my-events/${eventId}/edit`}>Manage event</Link></p>
    <h1>Participants · {title}</h1>
    <p>A confirmed participant may be removed before the event starts. A reason is required.</p>
    {loading && <p>Loading participants…</p>}
    {(error || actionError) && <p className="error" role="alert">{actionError || error}</p>}
    {data && <>
      {!data.participants.length && <p>No registrations yet.</p>}
      {data.participants.map(person => <article className="panel" key={person.id}>
        <div className="actions"><h2>{person.displayName}</h2><span className="badge">{person.status}</span></div>
        <p>{person.displayName} · {person.email}</p>
        <p>Registered: {eventTime(person.registeredAt)}</p>
        {person.cancelledAt && <p>Cancelled: {eventTime(person.cancelledAt)}</p>}
        {person.removedAt && <p>Removed: {eventTime(person.removedAt)} · {person.removedReason}</p>}
        {person.status === "CONFIRMED" && <form className="stack section-divider" onSubmit={form => void remove(form, person.id)}>
          <label>Removal reason<textarea name="reason" required maxLength={1000} /></label>
          <button className="danger" disabled={busy !== null}>{busy === person.id ? "Removing…" : "Remove participant"}</button>
        </form>}
      </article>)}
      <h2>Waitlist</h2>
      {!data.waitlist.length && <p>No waitlist entries.</p>}
      {data.waitlist.map(entry => <article className="panel" key={entry.id}>
        <div className="actions"><strong>{entry.displayName}</strong><span className="badge">{entry.status}</span></div>
        <p>Joined: {eventTime(entry.joinedAt)}{entry.status === "WAITING" ? ` · position ${entry.position ?? "—"}` : ""}</p>
      </article>)}
    </>}
  </main>;
}
