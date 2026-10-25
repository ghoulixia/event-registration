import { useCallback } from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import { useRemote } from "../shared/useRemote";
import { eventApi, eventTime } from "./eventApi";

export default function EventAuditPage() {
  const { id } = useParams();
  const eventId = Number(id);
  const { state } = useLocation();
  const title = typeof state?.eventTitle === "string" ? state.eventTitle : `Event #${eventId}`;
  const load = useCallback((signal: AbortSignal) => eventApi.audit(eventId, signal), [eventId]);
  const { data, loading, error, reload } = useRemote(load);

  return <main className="page">
    <p><Link to="/my-events">My events</Link></p>
    <div className="actions"><h1>Audit log · {title}</h1><button className="secondary" onClick={reload}>Refresh</button></div>
    <p>Only the event owner can view this history. Records remain available after the event is deleted.</p>
    {loading && <p>Loading audit log…</p>}
    {error && <p className="error" role="alert">{error}</p>}
    {data && <>
      {!data.length && <p>No audit records.</p>}
      {data.map(entry => <article className="panel" key={entry.id}>
        <div className="actions"><strong>{entry.action}</strong><span>{eventTime(entry.createdAt)}</span></div>
        <p>By {entry.actorName ?? (entry.actorUserId ? `User #${entry.actorUserId}` : "System")}</p>
        {entry.reason && <p>Reason: {entry.reason}</p>}
        {entry.details != null && <details><summary>Details</summary><pre className="audit-details">{typeof entry.details === "string" ? entry.details : JSON.stringify(entry.details, null, 2)}</pre></details>}
      </article>)}
    </>}
  </main>;
}
