import api from "../api/client";

export type Registration = {
  id: number;
  status: "CONFIRMED" | "CANCELLED" | "REMOVED" | string;
  eventId: number;
  eventTitle: string;
  registeredAt: string | null;
};

export type SlotOffer = {
  id: number;
  status: "PENDING" | "ACCEPTED" | "DECLINED" | "EXPIRED" | "REVOKED" | string;
  expiresAt: string | null;
};

export type WaitlistEntry = {
  id: number;
  eventId: number;
  eventTitle: string;
  userId: number;
  status: "WAITING" | "OFFERED" | "ACCEPTED" | "LEFT" | "SKIPPED" | "CANCELLED" | string;
  joinedAt: string | null;
  position: number | null;
  offer: SlotOffer | null;
};

export type MyWaitlist = {
  entries: WaitlistEntry[];
  serverNow: string;
};

export const registrationApi = {
  // Current user's registrations
  mine: async (signal?: AbortSignal) =>
      (
          await api.get<Registration[]>("/me/registrations", {
            signal,
          })
      ).data,

  // Register directly for an event
  register: async (eventId: number) =>
      (
          await api.post<Registration>(
              `/events/${eventId}/registrations`
          )
      ).data,

  // Cancel a confirmed registration
  cancel: async (registrationId: number) =>
      api.delete(`/registrations/${registrationId}`),

  // Current user's waiting-list entries and slot offers
  myWaitlist: async (signal?: AbortSignal) =>
      (
          await api.get<MyWaitlist>("/me/waitlist", {
            signal,
          })
      ).data,

  // Join an event's waiting list
  joinWaitlist: async (eventId: number) =>
      (
          await api.post<WaitlistEntry>(
              `/events/${eventId}/waitlist`
          )
      ).data,

  // Leave an event's waiting list
  leaveWaitlist: async (eventId: number) =>
      api.delete(`/events/${eventId}/waitlist/me`),

  // Accept a slot offer
  acceptOffer: async (offerId: number) =>
      api.post(`/offers/${offerId}/accept`),

  // Decline a slot offer
  declineOffer: async (offerId: number) =>
      api.post(`/offers/${offerId}/decline`),
};

export function registrationTime(
    value: string | null | undefined
): string {
  if (!value) return "—";

  return value.replace("T", " ").slice(0, 19);
}

export function offerExpiry(
    value: string | null | undefined
): string {
  if (!value) return "Not available";

  return (
      new Date(value).toLocaleString("en-GB", {
        timeZone: "Asia/Ho_Chi_Minh",
      }) + " (UTC+7)"
  );
}