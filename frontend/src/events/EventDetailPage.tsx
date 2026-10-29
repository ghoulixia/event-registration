import { useCallback } from "react";
import { Link, useParams } from "react-router-dom";
import { useAuth } from "../auth/session";
import { mediaUrl } from "../api/client";
import { useRemote } from "../shared/useRemote";
import { eventApi, eventTime } from "./eventApi";


export default function EventDetailPage() {
  const { id } = useParams();
  const eventId = Number(id);
  const { session } = useAuth();
  const load = useCallback((signal: AbortSignal) => eventApi.detail(eventId, signal), [eventId]);
  const { data: event, loading, error, reload } = useRemote(load);

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
        {session?.user.id === event.organizerUserId && <div className="actions">
          <Link to={`/my-events/${event.id}/edit`}>Manage event</Link>
          <Link to={`/my-events/${event.id}/participants`}>Participants</Link>
          <Link to={`/my-events/${event.id}/audit-logs`}>Audit log</Link>
        </div>}
        <button className="secondary" onClick={reload}>Refresh availability</button>
      </article>

    </>}
  </main>;
}
