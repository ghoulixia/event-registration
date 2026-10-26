import { useCallback, useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import type { EventItem } from "../api/types";
import { errorMessage, mediaUrl } from "../api/client";
import { useRemote } from "../shared/useRemote";
import { eventApi, eventTime, eventTimeInput } from "./eventApi";
import type { EventInput } from "./eventApi";

function EventForm({ event, onDirty }: { event?: EventItem; onDirty: (dirty: boolean) => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const navigate = useNavigate();

  async function save(form: FormEvent<HTMLFormElement>) {
    form.preventDefault();
    const fields = new FormData(form.currentTarget);
    const value = (name: string) => String(fields.get(name) ?? "").trim();
    const time = (name: "startTime" | "endTime") => {
      const entered = value(name);
      return event && entered === eventTimeInput(event[name]) ? event[name] : entered;
    };
    const input: EventInput = {
      title: value("title"), description: value("description"), organizerName: value("organizerName"),
      city: value("city"), category: value("category"), startTime: time("startTime"), endTime: time("endTime"),
      capacity: Number(value("capacity")), offerTtlMinutes: Number(value("offerTtlMinutes")),
    };
    if (input.endTime <= input.startTime) { setError("End time must be after start time."); return; }
    setBusy(true);
    setError("");
    try {
      if (event) await eventApi.update(event.id, input);
      else await eventApi.create(input);
      navigate("/my-events");
    } catch (exception) { setError(errorMessage(exception)); }
    finally { setBusy(false); }
  }

  return <form className="panel stack" onSubmit={save} onChange={() => onDirty(true)} onReset={() => onDirty(false)}>
    <label>Title<input name="title" required maxLength={255} defaultValue={event?.title ?? ""} /></label>
    <label>Description<textarea name="description" rows={5} defaultValue={event?.description ?? ""} /></label>
    <label>Organizer name<input name="organizerName" required maxLength={255} defaultValue={event?.organizerName ?? ""} /></label>
    <div className="form-grid">
      <label>City<select name="city" defaultValue={event?.city ?? "HANOI"}>
        <option value="HANOI">Hanoi</option><option value="HCMC">Ho Chi Minh City</option>
      </select></label>
      <label>Category<select name="category" defaultValue={event?.category ?? "TECHNOLOGY"}>
        {["TECHNOLOGY", "SPORTS", "EDUCATION", "MUSIC"].map(value => <option key={value}>{value}</option>)}
      </select></label>
      <label>Start time (UTC+7)<input name="startTime" type="datetime-local" step="1" required
        defaultValue={event ? eventTimeInput(event.startTime) : ""} /></label>
      <label>End time (UTC+7)<input name="endTime" type="datetime-local" step="1" required
        defaultValue={event ? eventTimeInput(event.endTime) : ""} /></label>
      <label>Capacity<input name="capacity" type="number" min="1" max="2147483647" step="1" required defaultValue={event?.capacity ?? 100} /></label>
      <label>Offer response time (minutes)<input name="offerTtlMinutes" type="number" min="5" max="30" step="1" required
        defaultValue={event?.offerTtlMinutes ?? 10} /></label>
    </div>
    <p>Waitlist offers are not available yet. All event times use Vietnam time (UTC+7).</p>
    {event && <p>Capacity must cover confirmed registrations and reserved places.</p>}
    {error && <p className="error" role="alert">{error}</p>}
    <div className="actions"><button disabled={busy}>{busy ? "Saving…" : event ? "Save event" : "Create event"}</button>
      <button className="secondary" type="reset" disabled={busy}>Reset form</button></div>
  </form>;
}

function OwnerActions({ event, disabled, onReload }: { event: EventItem; disabled: boolean; onReload: () => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const navigate = useNavigate();
  async function act(action: () => Promise<unknown>, deleted = false) {
    setBusy(true);
    setError("");
    try { await action(); if (deleted) navigate("/my-events"); else onReload(); }
    catch (exception) { setError(errorMessage(exception)); }
    finally { setBusy(false); }
  }
  function upload(form: FormEvent<HTMLFormElement>) {
    form.preventDefault();
    const file = new FormData(form.currentTarget).get("file");
    if (!(file instanceof File) || !file.size) { setError("Choose an image first."); return; }
    if (file.size > 5 * 1024 * 1024) { setError("The image must not exceed 5 MB."); return; }
    void act(() => eventApi.upload(event.id, file));
  }
  function remove(form: FormEvent<HTMLFormElement>) {
    form.preventDefault();
    const reason = String(new FormData(form.currentTarget).get("reason") ?? "").trim();
    if (!reason) { setError("A deletion reason is required."); return; }
    void act(() => eventApi.remove(event.id, reason), true);
  }
  return <section className="panel stack">
    <h2>Registration settings</h2>
    <p>Registration status: <strong>{event.status}</strong>. Current availability: <strong>{event.effectiveStatus}</strong>.</p>
    <p>{event.registeredCount} confirmed · {event.heldCount} active holds · {event.remainingSlots} places available</p>
    {disabled && <p>Save or reset your form changes before performing another action.</p>}
    <button className="secondary" disabled={busy || disabled}
      onClick={() => void act(() => eventApi.status(event.id, event.status === "OPEN" ? "CLOSED" : "OPEN"))}>
      {event.status === "OPEN" ? "Close registrations" : "Open registrations"}
    </button>
    <p>Closing pauses new registrations. Events that have started cannot reopen.</p>
    <form className="stack section-divider" onSubmit={upload}>
      <h2>Event thumbnail</h2>
      {event.thumbnailUrl && <img className="event-thumbnail" src={mediaUrl(event.thumbnailUrl)} alt="Current event thumbnail" />}
      <label>Image (JPEG, PNG or WebP, at most 5 MB)<input type="file" name="file" required accept="image/jpeg,image/png,image/webp" /></label>
      <button disabled={busy || disabled}>Upload thumbnail</button>
    </form>
    <form className="stack section-divider" onSubmit={remove}>
      <h2>Delete event</h2>
      <p>The event will disappear from public browsing. Registrations and event audit are retained.</p>
      <label>Reason<textarea name="reason" required maxLength={1000} /></label>
      <button className="danger" disabled={busy || disabled}>Delete event</button>
    </form>
    {error && <p className="error" role="alert">{error}</p>}
  </section>;
}

export default function EventFormPage() {
  const { id } = useParams();
  const editing = id !== undefined;
  const eventId = Number(id);
  const [dirty, setDirty] = useState(false);
  const load = useCallback((signal: AbortSignal) => editing ? eventApi.ownedDetail(eventId, signal) : Promise.resolve(null), [editing, eventId]);
  const { data: event, loading, error, reload } = useRemote(load);

  return <main className="page">
    <p><Link to="/my-events">My events</Link></p>
    <h1>{editing ? "Manage event" : "Create event"}</h1>
    {editing && loading && <p>Loading event…</p>}
    {error && <p className="error" role="alert">{error}</p>}
    {event && <>
      <h2>{event.title}</h2>
      <p>{eventTime(event.startTime)}</p>
      <div className="actions">
        {!event.deletedAt && <Link to={`/my-events/${event.id}/participants`} state={{ eventTitle: event.title }}>Participants</Link>}
        <Link to={`/my-events/${event.id}/audit-logs`} state={{ eventTitle: event.title }}>Audit log</Link>
      </div>
    </>}
    {event?.deletedAt ? <p>This event was deleted on {eventTime(event.deletedAt)}. Its audit history is still available.</p> :
      (!editing || event) && <>
        <EventForm key={event?.id ?? "new"} event={event ?? undefined} onDirty={setDirty} />
        {event && <OwnerActions event={event} disabled={dirty} onReload={reload} />}
      </>}
  </main>;
}
