export type EventItem = {
  id: number; title: string; description: string | null; organizerName: string;
  organizerUserId: number | null; city: string; category: string;
  startTime: string; endTime: string; capacity: number; registeredCount: number; heldCount: number;
  remainingSlots: number; effectiveStatus: string; status: string; offerTtlMinutes: number;
  thumbnailUrl: string | null; canRegister: boolean; deletedAt: string | null;
};
