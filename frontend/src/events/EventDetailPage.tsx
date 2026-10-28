import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useAuth } from "../auth/session";
import { errorMessage, mediaUrl } from "../api/client";
import { useRemote } from "../shared/useRemote";
import { registrationApi } from "../registrations/registrationApi";
import { eventApi, eventTime } from "./eventApi";

export default function EventDetailPage() {
  const { id } = useParams();
  const eventId = Number(id);
  const { session, loading: authLoading } = useAuth();
  const userId = session?.user.id;
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState("");
  const [notice, setNotice] = useState("");
  const [currentTime, setCurrentTime] = useState(Date.now);
  const load = useCallback((signal: AbortSignal) => eventApi.detail(eventId, signal), [eventId]);
  const { data: event, loading, error, reload } = useRemote(load);
  const loadParticipation = useCallback(async (signal: AbortSignal) => {
    if (!userId) return null;
    const [registrations, waitlist] = await Promise.all([
      registrationApi.mine(signal), registrationApi.waitlist(signal),
    ]);
    return { registrations, waitlist, receivedAt: Date.now() };
  }, [userId]);
  const { data: participation, loading: participationLoading, error: participationError,
    reload: reloadParticipation } = useRemote(loadParticipation);

  useEffect(() => {
    if (!userId) return;
    const interval = window.setInterval(() => { reload(); reloadParticipation(); }, 10000);
    return () => window.clearInterval(interval);
  }, [userId, eventId, reload, reloadParticipation]);
  useEffect(() => {
    const interval = window.setInterval(() => setCurrentTime(Date.now()), 1000);
    return () => window.clearInterval(interval);
  }, []);

  const registration = participation?.registrations.find(item => item.eventId === eventId);
  const entry = participation?.waitlist.entries.find(item => item.eventId === eventId);
  const offer = entry?.status === "OFFERED" ? entry.offer : null;
  const serverTime = participation ? Date.parse(participation.waitlist.serverNow) + currentTime - participation.receivedAt : 0;
  const remainingSeconds = offer?.expiresAt ? Math.max(0, Math.ceil((Date.parse(offer.expiresAt) - serverTime) / 1000)) : 0;
  const upcoming = event?.effectiveStatus === "OPEN";
  const owner = userId === event?.organizerUserId;

  async function act(request: () => Promise<unknown>, success: string) {
    setBusy(true);
    setActionError("");
    setNotice("");
    try {
      await request();
      setNotice(success);
    } catch (exception) {
      setActionError(errorMessage(exception));
    } finally {
      reload();
      reloadParticipation();
      setBusy(false);
    }
  }

  return <main className="page">
    <p><Link to="/events">All events</Link></p>
    {loading && <p>Loading event…</p>}
    {error && <p role="alert" className="error">{error}</p>}
    {event && <>
      <article className="panel">
        {event.thumbnailUrl && <img className="event-thumbnail" src={mediaUrl(event.thumbnailUrl)} alt="" />}
        <span className="badge">{event.category} · {event.effectiveStatus}</span>
        <h1>{event.title}</h1>
        <p>{event.organizerName} · {event.city}</p>
        <p>{eventTime(event.startTime)} — {eventTime(event.endTime)}</p>
        <p className="preserve-lines">{event.description}</p>
        <p>{event.remainingSlots} places available · {event.registeredCount} confirmed · {event.heldCount} held</p>
        {owner && <div className="actions">
          <Link to={`/my-events/${event.id}/edit`}>Manage event</Link>
          <Link to={`/my-events/${event.id}/participants`}>Participants</Link>
          <Link to={`/my-events/${event.id}/audit-logs`}>Audit log</Link>
        </div>}
        <button className="secondary" onClick={() => { reload(); reloadParticipation(); }}>Refresh availability</button>
      </article>

      {!authLoading && !session && <div className="panel"><p><Link to="/login">Sign in</Link> to register or join the waitlist.</p></div>}
      {session && !owner && <section className="panel">
        <h2>Your participation</h2>
        {participationLoading && <p>Loading your status…</p>}
        {(participationError || actionError) && <p role="alert" className="error">{actionError || participationError}</p>}
        {notice && <p role="status">{notice}</p>}
        {participation && <>
          {registration?.status === "CONFIRMED" ? <>
            <p>Registration confirmed · {session.user.displayName} ({session.user.email})</p>
            <button className="secondary" disabled={busy || !upcoming}
              onClick={() => void act(() => registrationApi.cancel(registration.id), "Registration cancelled.")}>
              {busy ? "Working…" : "Cancel registration"}
            </button>
          </> : entry ? <>
            <p>Waitlist: <strong>{entry.status}</strong>{entry.status === "WAITING" && entry.position != null ? ` · position ${entry.position}` : ""}</p>
            {offer && <>
              {remainingSeconds > 0 ? <>
                <p>Reserved offer expires at {eventTime(offer.expiresAt)}.</p>
                <p className="countdown" role="timer">{Math.floor(remainingSeconds / 60)}:{String(remainingSeconds % 60).padStart(2, "0")}</p>
                <div className="actions">
                  <button disabled={busy || !upcoming} onClick={() => void act(() => registrationApi.accept(offer.id), "Offer accepted. Registration confirmed.")}>Accept offer</button>
                  <button className="secondary" disabled={busy} onClick={() => void act(() => registrationApi.decline(offer.id), "Offer declined.")}>Decline offer</button>
                </div>
              </> : <p role="status">Offer deadline passed. Refresh to see the updated waitlist status; an expired offer cannot be accepted.</p>}
            </>}
            <button className="secondary" disabled={busy} onClick={() => void act(() => registrationApi.leave(eventId), "Left the waitlist.")}>Leave waitlist</button>
            <p>There is no email or SMS notification. Keep this page open and respond to any offer before it expires.</p>
          </> : upcoming && registration?.status !== "REMOVED" ? <>
            {registration?.status === "CANCELLED" && <p>Your previous registration was cancelled. You may join again.</p>}
            {event.canRegister ? <>
              <p>Register as {session.user.displayName} ({session.user.email}). Registration is confirmed immediately; no place is held before submitting.</p>
              <button disabled={busy} onClick={() => void act(() => registrationApi.register(eventId), "Registration confirmed.")}>{busy ? "Working…" : "Register"}</button>
            </> : event.remainingSlots === 0 ? <>
              <p>This event is full. Join the waitlist for a chance to receive a time-limited offer. If a previous offer expired, it can no longer be accepted; join again at the end of the queue.</p>
              <button disabled={busy} onClick={() => void act(() => registrationApi.join(eventId), "Joined the waitlist.")}>{busy ? "Working…" : "Join waitlist"}</button>
              <p>There is no email or SMS notification. Return to this page to respond to an offer in time.</p>
            </> : <p>Available places are being offered to the waitlist first. Refresh before registering.</p>}
          </> : <p>{registration?.status === "REMOVED" ? "You were removed from this event and cannot register again." : "Registration and waitlist are closed."}</p>}
          <p><Link to="/my-registrations">View registration history</Link></p>
        </>}
      </section>}
    </>}
  </main>;
}
