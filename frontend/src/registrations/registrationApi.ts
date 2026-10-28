import api from "../api/client";

export type RegistrationHistory = {
  action: "REGISTRATION_CONFIRM" | "REGISTRATION_CANCEL" | "PARTICIPANT_REMOVE";
  createdAt: string;
  previousStatus: string | null;
  newStatus: string;
};

export type Registration = {
  id: number;
  eventId: number;
  eventTitle: string;
  status: "CONFIRMED" | "CANCELLED" | "REMOVED";
  registeredAt: string;
  history: RegistrationHistory[];
};

export type SlotOffer = { id: number; status: string; expiresAt: string | null };
export type WaitlistEntry = {
  id: number;
  eventId: number;
  eventTitle: string;
  status: "WAITING" | "OFFERED";
  joinedAt: string;
  position: number | null;
  offer: SlotOffer | null;
};
export type MyWaitlist = { entries: WaitlistEntry[]; serverNow: string };

export const registrationApi = {
  mine: async (signal?: AbortSignal) =>
    (await api.get<Registration[]>("/me/registrations", { signal })).data,
  waitlist: async (signal?: AbortSignal) =>
    (await api.get<MyWaitlist>("/me/waitlist", { signal })).data,
  register: async (eventId: number) =>
    (await api.post<Registration>(`/events/${eventId}/registrations`)).data,
  cancel: async (registrationId: number) => api.delete(`/registrations/${registrationId}`),
  join: async (eventId: number) => api.post(`/events/${eventId}/waitlist`),
  leave: async (eventId: number) => api.delete(`/events/${eventId}/waitlist/me`),
  accept: async (offerId: number) => api.post(`/offers/${offerId}/accept`),
  decline: async (offerId: number) => api.post(`/offers/${offerId}/decline`),
};
