import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/session";
import { errorMessage } from "../api/client";
import { eventTime } from "../events/eventApi";
import { useRemote } from "../shared/useRemote";
import { registrationApi } from "./registrationApi";

export default function MyRegistrationsPage() {
    const { session, loading: authLoading } = useAuth();
    const userId = session?.user.id;
    const [busy, setBusy] = useState<number | null>(null);
    const [actionError, setActionError] = useState("");
    const load = useCallback(async (signal: AbortSignal) => {
        if (!userId) return null;
        const [registrations, waitlist] = await Promise.all([
            registrationApi.mine(signal), registrationApi.waitlist(signal),
        ]);
        return { registrations, waitlist };
    }, [userId]);
    const { data, loading, error, reload } = useRemote(load);

    useEffect(() => {
        if (!userId) return;
        const interval = window.setInterval(reload, 10000);
        return () => window.clearInterval(interval);
    }, [userId, reload]);

    async function cancel(id: number) {
        setBusy(id);
        setActionError("");
        try { await registrationApi.cancel(id); }
        catch (exception) { setActionError(errorMessage(exception)); }
        finally { setBusy(null); reload(); }
    }

    return <main className="page">
        <h1>My registrations</h1>
        {!authLoading && !session && <p><Link to="/login">Sign in</Link> to see your registrations.</p>}
        {session && <>
            <button className="secondary" onClick={reload}>Refresh</button>
            {loading && <p>Loading your registrations…</p>}
            {(error || actionError) && <p role="alert" className="error">{actionError || error}</p>}
            {data && <>
                {!data.registrations.length && <p>No registrations yet.</p>}
                {data.registrations.map(item => <article className="panel" key={item.id}>
                    <div className="actions"><h2><Link to={`/events/${item.eventId}`}>{item.eventTitle}</Link></h2><span className="badge">{item.status}</span></div>
                    <p>Last confirmed: {eventTime(item.registeredAt)}</p>
                    {item.status === "CONFIRMED" && <button className="secondary" disabled={busy !== null}
                        onClick={() => void cancel(item.id)}>{busy === item.id ? "Cancelling…" : "Cancel registration"}</button>}
                    <h3>History</h3>
                    {!item.history.length && <p>No recorded changes.</p>}
                    {item.history.map((entry, index) => <p key={`${entry.createdAt}-${index}`}>
                        {eventTime(entry.createdAt)} · {entry.action.replaceAll("_", " ")}
                        {" · "}{entry.previousStatus ?? "NONE"} → {entry.newStatus}
                    </p>)}
                </article>)}
                <h2>Active waitlist</h2>
                {!data.waitlist.entries.length && <p>No active waitlist entries.</p>}
                {data.waitlist.entries.map(entry => <article className="panel" key={entry.id}>
                    <div className="actions"><Link to={`/events/${entry.eventId}`}>{entry.eventTitle}</Link><span className="badge">{entry.status}</span></div>
                    <p>{entry.status === "WAITING" ? `Position ${entry.position ?? "—"}` : `Offer expires ${eventTime(entry.offer?.expiresAt)}`}</p>
                    <p><Link to={`/events/${entry.eventId}`}>View event to respond or leave</Link></p>
                </article>)}
            </>}
        </>}
    </main>;
}