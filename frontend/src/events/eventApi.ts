import api from "../api/client";
import type { EventItem } from "../api/types";

export type OwnerWaitlist = {
  id: number; eventId: number; eventTitle: string; userId: number; displayName: string;
  status: string; joinedAt: string; position: number | null;
};

export type PageResult<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  empty: boolean;
};

export type EventInput = {
  title: string;
  description: string;
  organizerName: string;
  city: string;
  category: string;
  startTime: string;
  endTime: string;
  capacity: number;
  offerTtlMinutes: number;
};

export type Participant = {
  id: number;
  userId: number;
  displayName: string;
  email: string;
  status: string;
  registeredAt: string;
  cancelledAt?: string | null;
  removedAt?: string | null;
  removedReason?: string | null;
};

export type AuditEntry = {
  id: number;
  action: string;
  actorUserId: number | null;
  actorName: string | null;
  reason: string | null;
  createdAt: string;
  details: unknown;
};

export const eventApi = {
  list: async (params: Record<string, string | number>, signal?: AbortSignal) =>
    (await api.get<PageResult<EventItem>>("/events", { params, signal })).data,
  detail: async (id: number, signal?: AbortSignal) =>
    (await api.get<EventItem>(`/events/${id}`, { signal })).data,
  owned: async (page: number, signal?: AbortSignal) =>
    (await api.get<PageResult<EventItem>>("/me/events", { params: { page, size: 12 }, signal })).data,
  ownedDetail: async (id: number, signal?: AbortSignal) =>
    (await api.get<EventItem>(`/me/events/${id}`, { signal })).data,
  create: async (input: EventInput) => (await api.post<EventItem>("/events", input)).data,
  update: async (id: number, input: EventInput) =>
    (await api.put<EventItem>(`/events/${id}`, input)).data,
  status: async (id: number, status: "OPEN" | "CLOSED") =>
    (await api.patch<EventItem>(`/events/${id}/status`, { status })).data,
  remove: async (id: number, reason: string) =>
    api.delete(`/events/${id}`, { data: { reason } }),
  upload: async (id: number, file: File) => {
    const data = new FormData();
    data.set("file", file);
    return (await api.post<EventItem>(`/events/${id}/thumbnail`, data,
      { headers: { "Content-Type": undefined } })).data;
  },
  participants: async (id: number, signal?: AbortSignal) =>
    (await api.get<Participant[]>(`/events/${id}/registrations`, { signal })).data,
  waitlist: async (id: number, signal?: AbortSignal) =>
    (await api.get<OwnerWaitlist[]>(`/events/${id}/waitlist`, { signal })).data,
  removeParticipant: async (eventId: number, registrationId: number, reason: string) =>
    api.delete(`/events/${eventId}/registrations/${registrationId}`, { data: { reason } }),
  audit: async (id: number, signal?: AbortSignal) =>
    (await api.get<AuditEntry[]>(`/events/${id}/audit-logs`, { signal })).data,
};

export function eventTime(value: string | null | undefined): string {
  if (!value) return "—";
  if (/(?:Z|[+-]\d{2}:\d{2})$/i.test(value)) {
    return new Date(value).toLocaleString("en-GB", { timeZone: "Asia/Ho_Chi_Minh" }) + " (UTC+7)";
  }
  return value.replace("T", " ").slice(0, 19) + " (UTC+7)";
}

export function eventTimeInput(value: string): string {
  if (!/(?:Z|[+-]\d{2}:\d{2})$/i.test(value)) return value.slice(0, 19);
  return new Date(value).toLocaleString("sv-SE", {
    timeZone: "Asia/Ho_Chi_Minh", year: "numeric", month: "2-digit", day: "2-digit",
    hour: "2-digit", minute: "2-digit", second: "2-digit", hourCycle: "h23",
  }).replace(" ", "T");
}
