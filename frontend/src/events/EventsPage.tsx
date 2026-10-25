import { useCallback } from "react";
import type { FormEvent } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useRemote } from "../shared/useRemote";
import { mediaUrl } from "../api/client";
import { eventApi, eventTime } from "./eventApi";

export default function EventsPage() {
  const [search, setSearch] = useSearchParams();
  const rawPage = Number(search.get("page") ?? "0");
  const page = Number.isSafeInteger(rawPage) && rawPage >= 0 ? rawPage : 0;
  const keyword = search.get("keyword") ?? "";
  const city = search.get("city") ?? "";
  const category = search.get("category") ?? "";
  const sort = search.get("sort") ?? "startTime";
  const load = useCallback((signal: AbortSignal) =>
    eventApi.list({ page, size: 12, keyword, city, category, sort }, signal),
  [page, keyword, city, category, sort]);
  const { data, loading, error } = useRemote(load);

  function filter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const fields = new FormData(event.currentTarget);
    const next = new URLSearchParams();
    for (const name of ["keyword", "city", "category", "sort"]) {
      const value = String(fields.get(name) ?? "").trim();
      if (value) next.set(name, value);
    }
    setSearch(next);
  }

  function changePage(value: number) {
    const next = new URLSearchParams(search);
    next.set("page", String(value));
    setSearch(next);
  }

  return <main className="page">
    <h1>Discover events</h1>
    <p>Concerts, workshops, sports and community events.</p>
    <form className="panel form-grid" key={search.toString()} onSubmit={filter}>
      <label>Search<input name="keyword" defaultValue={keyword} placeholder="Event name" /></label>
      <label>City<select name="city" defaultValue={city}>
        <option value="">All cities</option><option value="HANOI">Hanoi</option><option value="HCMC">Ho Chi Minh City</option>
      </select></label>
      <label>Category<select name="category" defaultValue={category}>
        <option value="">All categories</option>{["TECHNOLOGY", "SPORTS", "EDUCATION", "MUSIC"].map(value => <option key={value}>{value}</option>)}
      </select></label>
      <label>Sort by<select name="sort" defaultValue={sort}>
        <option value="startTime">Start time</option><option value="title">Title</option>
        <option value="createdAt">Created time</option><option value="capacity">Capacity</option>
      </select></label>
      <button>Apply filters</button>
    </form>
    {error && <p className="error" role="alert">{error}</p>}
    {loading ? <p>Loading events…</p> : data && <>
      <div className="event-grid">{data.content.map(event => <article key={event.id} className="panel">
        {event.thumbnailUrl && <img className="event-thumbnail" src={mediaUrl(event.thumbnailUrl)} alt="" />}
        <span className="badge">{event.category} · {event.effectiveStatus}</span>
        <h2><Link to={`/events/${event.id}`}>{event.title}</Link></h2>
        <p>{event.organizerName} · {event.city}</p>
        <p>{eventTime(event.startTime)}</p>
        <p>{event.remainingSlots} places available · {event.heldCount} reserved by offers</p>
        <Link to={`/events/${event.id}`}>View event</Link>
      </article>)}</div>
      {!data.content.length && <p>No events found.</p>}
      <div className="actions">
        <button disabled={page === 0} onClick={() => changePage(page - 1)}>Previous</button>
        <span>Page {page + 1} of {Math.max(1, data.totalPages)}</span>
        <button disabled={page + 1 >= data.totalPages} onClick={() => changePage(page + 1)}>Next</button>
      </div>
    </>}
  </main>;
}
