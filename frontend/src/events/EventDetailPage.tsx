import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";

import { useAuth } from "../auth/session";
import { errorMessage, mediaUrl } from "../api/client";
import { useRemote } from "../shared/useRemote";

import {
  registrationApi,
  type Registration,
  type WaitlistEntry,
} from "../registrations/registrationApi";

import { eventApi, eventTime } from "./eventApi";

export default function EventDetailPage() {
  const { id } = useParams();
  const eventId = Number(id);

  const { session } = useAuth();

  const load = useCallback(
      (signal: AbortSignal) =>
          eventApi.detail(eventId, signal),
      [eventId]
  );

  const {
    data: event,
    loading,
    error,
    reload,
  } = useRemote(load);

  const [registering, setRegistering] = useState(false);
  const [waitlistBusy, setWaitlistBusy] = useState(false);

  const [registrationMessage, setRegistrationMessage] =
      useState("");

  const [registrationError, setRegistrationError] =
      useState("");

  const [myRegistration, setMyRegistration] =
      useState<Registration | null>(null);

  const [myWaitlistEntry, setMyWaitlistEntry] =
      useState<WaitlistEntry | null>(null);

  const [checkingParticipation, setCheckingParticipation] =
      useState(false);

  // Load this user's current registration and waitlist status
  // whenever they open a different event or change accounts.
  useEffect(() => {
    let cancelled = false;
    const controller = new AbortController();

    setMyRegistration(null);
    setMyWaitlistEntry(null);
    setRegistrationMessage("");
    setRegistrationError("");

    if (!session || !Number.isFinite(eventId)) {
      setCheckingParticipation(false);

      return () => {
        cancelled = true;
        controller.abort();
      };
    }

    setCheckingParticipation(true);

    Promise.all([
      registrationApi.mine(controller.signal),
      registrationApi.myWaitlist(controller.signal),
    ])
        .then(([registrations, waitlist]) => {
          if (cancelled) return;

          const registration =
              registrations.find(
                  (item) =>
                      item.eventId === eventId &&
                      item.status === "CONFIRMED"
              ) ?? null;

          const waitlistEntry =
              waitlist.entries.find(
                  (entry) => entry.eventId === eventId
              ) ?? null;

          setMyRegistration(registration);
          setMyWaitlistEntry(waitlistEntry);
        })
        .catch((err: unknown) => {
          if (
              !cancelled &&
              !controller.signal.aborted
          ) {
            setRegistrationError(errorMessage(err));
          }
        })
        .finally(() => {
          if (!cancelled) {
            setCheckingParticipation(false);
          }
        });

    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [session?.user.id, eventId]);

  async function register() {
    if (!event) return;

    setRegistering(true);
    setRegistrationError("");
    setRegistrationMessage("");

    try {
      const registration =
          await registrationApi.register(event.id);

      setMyRegistration(registration);
      setMyWaitlistEntry(null);

      setRegistrationMessage(
          "Your registration was successful."
      );

      reload();
    } catch (err) {
      setRegistrationError(errorMessage(err));
    } finally {
      setRegistering(false);
    }
  }

  async function joinWaitlist() {
    if (!event) return;

    setWaitlistBusy(true);
    setRegistrationError("");
    setRegistrationMessage("");

    try {
      const entry =
          await registrationApi.joinWaitlist(event.id);

      setMyWaitlistEntry(entry);

      setRegistrationMessage(
          "You have joined the waiting list."
      );

      reload();
    } catch (err) {
      setRegistrationError(errorMessage(err));
    } finally {
      setWaitlistBusy(false);
    }
  }

  async function leaveWaitlist() {
    if (!event) return;

    setWaitlistBusy(true);
    setRegistrationError("");
    setRegistrationMessage("");

    try {
      await registrationApi.leaveWaitlist(event.id);

      setMyWaitlistEntry(null);

      setRegistrationMessage(
          "You have left the waiting list."
      );

      reload();
    } catch (err) {
      setRegistrationError(errorMessage(err));
    } finally {
      setWaitlistBusy(false);
    }
  }

  const isOrganizer =
      !!session &&
      session.user.id === event?.organizerUserId;

  const isParticipant =
      !!session && !isOrganizer;

  const eventIsFull =
      !!event &&
      event.effectiveStatus === "OPEN" &&
      event.remainingSlots <= 0;

  return (
      <main className="page">
        <p>
          <Link to="/events">All events</Link>
        </p>

        {loading && <p>Loading event…</p>}

        {error && (
            <p role="alert" className="error">
              {error}
            </p>
        )}

        {event && (
            <article className="panel">
              {event.thumbnailUrl && (
                  <img
                      className="event-thumbnail"
                      src={mediaUrl(event.thumbnailUrl)}
                      alt=""
                  />
              )}

              <span className="badge">
            {event.category} · {event.effectiveStatus}
          </span>

              <h1>{event.title}</h1>

              <p>
                {event.organizerName} · {event.city}
              </p>

              <p>
                {eventTime(event.startTime)} —{" "}
                {eventTime(event.endTime)}
              </p>

              <p className="preserve-lines">
                {event.description}
              </p>

              {/* Show useful availability, not internal held counts. */}
              <p>
                {event.remainingSlots} places available
              </p>

              {isOrganizer && (
                  <div className="actions">
                    <Link to={`/my-events/${event.id}/edit`}>
                      Manage event
                    </Link>

                    <Link
                        to={`/my-events/${event.id}/participants`}
                    >
                      Participants
                    </Link>

                    <Link
                        to={`/my-events/${event.id}/audit-logs`}
                    >
                      Audit log
                    </Link>
                  </div>
              )}

              {registrationMessage && (
                  <p role="status">
                    {registrationMessage}{" "}
                    <Link to="/my-registrations">
                      View my registrations
                    </Link>
                  </p>
              )}

              {registrationError && (
                  <p role="alert" className="error">
                    {registrationError}
                  </p>
              )}

              {/* Signed-in participant's current status */}
              {isParticipant && checkingParticipation && (
                  <p>
                    Checking your registration and waiting-list
                    status…
                  </p>
              )}

              {/* Already registered */}
              {isParticipant &&
                  !checkingParticipation &&
                  myRegistration && (
                      <div className="actions">
                <span className="badge">
                  Registration confirmed
                </span>

                        <Link
                            className="button-link"
                            to="/my-registrations"
                        >
                          View my registration
                        </Link>
                      </div>
                  )}

              {/* Waiting-list entry or slot offer */}
              {isParticipant &&
                  !checkingParticipation &&
                  !myRegistration &&
                  myWaitlistEntry && (
                      <section className="panel">
                        <h2>
                          {myWaitlistEntry.status === "OFFERED"
                              ? "Slot offer received"
                              : "You are on the waiting list"}
                        </h2>

                        {myWaitlistEntry.status === "WAITING" && (
                            <p>
                              Waiting-list position:{" "}
                              {myWaitlistEntry.position ??
                                  "Not available"}
                            </p>
                        )}

                        {myWaitlistEntry.status === "OFFERED" && (
                            <>
                              <p>
                                A place has been offered to you.
                                Review the offer and respond before
                                its expiration time.
                              </p>

                              {myWaitlistEntry.offer?.expiresAt && (
                                  <p>
                                    Offer expires:{" "}
                                    {new Date(
                                        myWaitlistEntry.offer.expiresAt
                                    ).toLocaleString("en-GB", {
                                      timeZone: "Asia/Ho_Chi_Minh",
                                    })}{" "}
                                    (UTC+7)
                                  </p>
                              )}
                            </>
                        )}

                        <div className="actions">
                          <Link
                              className="button-link"
                              to="/my-registrations"
                          >
                            View waiting list / slot offer
                          </Link>

                          {myWaitlistEntry.status === "WAITING" && (
                              <button
                                  className="secondary"
                                  onClick={leaveWaitlist}
                                  disabled={waitlistBusy}
                              >
                                {waitlistBusy
                                    ? "Leaving…"
                                    : "Leave waiting list"}
                              </button>
                          )}
                        </div>
                      </section>
                  )}

              {/* No existing participation: direct registration */}
              {isParticipant &&
                  !checkingParticipation &&
                  !myRegistration &&
                  !myWaitlistEntry &&
                  event.canRegister && (
                      <div className="actions">
                        <button
                            onClick={register}
                            disabled={registering}
                        >
                          {registering
                              ? "Registering…"
                              : "Register for this event"}
                        </button>
                      </div>
                  )}

              {/* No existing participation: event is full */}
              {isParticipant &&
                  !checkingParticipation &&
                  !myRegistration &&
                  !myWaitlistEntry &&
                  eventIsFull && (
                      <div className="actions">
                        <p>
                          This event is full. You can join the
                          waiting list.
                        </p>

                        <button
                            onClick={joinWaitlist}
                            disabled={waitlistBusy}
                        >
                          {waitlistBusy
                              ? "Joining…"
                              : "Join waiting list"}
                        </button>
                      </div>
                  )}

              {!session && event.canRegister && (
                  <p>
                    <Link to="/login">Sign in</Link> to register
                    for this event.
                  </p>
              )}

              {isParticipant &&
                  !checkingParticipation &&
                  !myRegistration &&
                  !myWaitlistEntry &&
                  !event.canRegister &&
                  !eventIsFull && (
                      <p>
                        Registration and waiting-list entry are
                        currently unavailable for this event.
                      </p>
                  )}

              <button
                  className="secondary"
                  onClick={reload}
              >
                Refresh availability
              </button>
            </article>
        )}
      </main>
  );
}