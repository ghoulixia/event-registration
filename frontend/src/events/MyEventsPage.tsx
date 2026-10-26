import { useCallback, useState } from "react";
import { Link } from "react-router-dom";
import { useRemote } from "../shared/useRemote";
import { eventApi, eventTime } from "./eventApi";

export default function MyEventsPage() {
  const [page, setPage] = useState(0);
  const load = useCallback((signal: AbortSignal) => eventApi.owned(page, signal), [page]);
  const { data, loading, error } = useRemote(load);

  return <main className="page">
    <div className="actions"><h1>My events</h1><Link className="button-link" to="/my-events/new">Create event</Link></div>
    <p>Manage the events you own. Deleted events retain their audit history.</p>
    {loading && <p>Loading your events…</p>}
    {error && <p className="error" role="alert">{error}</p>}
    {data && <>
      {!data.content.length && <p>No events on this page.</p>}
      <div className="event-grid">{data.content.map(event => <article key={event.id} className="panel">
        <span className="badge">{event.deletedAt ? "DELETED" : event.effectiveStatus}</span>
        <h2>{event.title}</h2>
        <p>{eventTime(event.startTime)}</p>
        <p>{event.registeredCount} confirmed · {event.heldCount} holds · capacity {event.capacity}</p>
        <p>Offer TTL: {event.offerTtlMinutes} minutes</p>
        <div className="actions">
          {!event.deletedAt && <>
            <Link to={`/my-events/${event.id}/edit`}>Manage event</Link>
            <Link to={`/my-events/${event.id}/participants`} state={{ eventTitle: event.title }}>Participants</Link>
            <Link to={`/events/${event.id}`}>Public page</Link>
          </>}
          <Link to={`/my-events/${event.id}/audit-logs`} state={{ eventTitle: event.title }}>Audit log</Link>
        </div>
      </article>)}</div>
      <div className="actions">
        <button disabled={page === 0} onClick={() => setPage(value => value - 1)}>Previous</button>
        <span>Page {page + 1} of {Math.max(1, data.totalPages)}</span>
        <button disabled={page + 1 >= data.totalPages} onClick={() => setPage(value => value + 1)}>Next</button>
      </div>
    </>}
  </main>;
}
