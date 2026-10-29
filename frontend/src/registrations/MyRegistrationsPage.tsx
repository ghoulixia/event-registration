import { useCallback, useState } from "react";
import { Link } from "react-router-dom";

import { errorMessage } from "../api/client";
import { useRemote } from "../shared/useRemote";

import {
  registrationApi,
  registrationTime,
  offerExpiry,
} from "./registrationApi";

export default function MyRegistrationsPage() {
  const loadRegistrations = useCallback(
      (signal: AbortSignal) => registrationApi.mine(signal),
      []
  );

  const {
    data: registrations,
    loading,
    error,
    reload,
  } = useRemote(loadRegistrations);

  const loadWaitlist = useCallback(
      (signal: AbortSignal) => registrationApi.myWaitlist(signal),
      []
  );

  const {
    data: waitlist,
    loading: waitlistLoading,
    error: waitlistError,
    reload: reloadWaitlist,
  } = useRemote(loadWaitlist);

  const [cancellingId, setCancellingId] =
      useState<number | null>(null);

  const [offerActionId, setOfferActionId] =
      useState<number | null>(null);

  const [waitlistActionId, setWaitlistActionId] =
      useState<number | null>(null);

  const [actionError, setActionError] = useState("");
  const [notice, setNotice] = useState("");

  function refreshAll() {
    reload();
    reloadWaitlist();
  }

  async function cancelRegistration(
      registrationId: number
  ) {
    if (
        !window.confirm(
            "Are you sure you want to cancel this registration?"
        )
    ) {
      return;
    }

    setCancellingId(registrationId);
    setActionError("");
    setNotice("");

    try {
      await registrationApi.cancel(registrationId);

      setNotice("Your registration has been cancelled.");
      refreshAll();
    } catch (err) {
      setActionError(errorMessage(err));
    } finally {
      setCancellingId(null);
    }
  }

  async function respondToOffer(
      offerId: number,
      accept: boolean
  ) {
    setOfferActionId(offerId);
    setActionError("");
    setNotice("");

    try {
      if (accept) {
        await registrationApi.acceptOffer(offerId);

        setNotice(
            "Slot offer accepted. Check your confirmed registrations."
        );
      } else {
        await registrationApi.declineOffer(offerId);

        setNotice("You declined the slot offer.");
      }

      refreshAll();
    } catch (err) {
      setActionError(errorMessage(err));
    } finally {
      setOfferActionId(null);
    }
  }

  async function leaveWaitlist(
      eventId: number,
      eventTitle: string
  ) {
    if (
        !window.confirm(
            `Leave the waiting list for "${eventTitle}"?`
        )
    ) {
      return;
    }

    setWaitlistActionId(eventId);
    setActionError("");
    setNotice("");

    try {
      await registrationApi.leaveWaitlist(eventId);

      setNotice(
          `You have left the waiting list for "${eventTitle}".`
      );

      reloadWaitlist();
    } catch (err) {
      setActionError(errorMessage(err));
    } finally {
      setWaitlistActionId(null);
    }
  }

  const confirmedRegistrations =
      registrations?.filter(
          (registration) =>
              registration.status === "CONFIRMED"
      ) ?? [];

  return (
      <main className="page">
        <h1>My Registrations</h1>

        <p>
          View your confirmed registrations, waiting-list
          entries, and slot offers.
        </p>

        {notice && (
            <p role="status">{notice}</p>
        )}

        {(actionError || error || waitlistError) && (
            <p role="alert" className="error">
              {actionError || error || waitlistError}
            </p>
        )}

        {/* WAITLIST AND SLOT OFFERS */}
        <section>
          <h2>Waiting List &amp; Slot Offers</h2>

          {waitlistLoading && (
              <p>Loading waiting-list entries…</p>
          )}

          {!waitlistLoading &&
              waitlist &&
              waitlist.entries.length === 0 && (
                  <p>
                    You don't have any active waiting-list entries
                    or slot offers.
                  </p>
              )}

          {waitlist?.entries.map((entry) => (
              <article className="panel" key={entry.id}>
            <span className="badge">
              {entry.status === "OFFERED"
                  ? "SLOT OFFER"
                  : "WAITLISTED"}
            </span>

                <h3>
                  <Link to={`/events/${entry.eventId}`}>
                    {entry.eventTitle}
                  </Link>
                </h3>

                <p>
                  Joined: {registrationTime(entry.joinedAt)}
                </p>

                {entry.status === "WAITING" && (
                    <>
                      <p>
                        Waiting-list position:{" "}
                        {entry.position ?? "Not available"}
                      </p>

                      <button
                          className="secondary"
                          disabled={
                              waitlistActionId === entry.eventId
                          }
                          onClick={() =>
                              leaveWaitlist(
                                  entry.eventId,
                                  entry.eventTitle
                              )
                          }
                      >
                        {waitlistActionId === entry.eventId
                            ? "Leaving…"
                            : "Leave waiting list"}
                      </button>
                    </>
                )}

                {entry.status === "OFFERED" && entry.offer && (
                    <>
                      <p>
                        A place has been offered to you. Accept
                        before the offer expires to secure your
                        registration.
                      </p>

                      <p>
                        Offer expires:{" "}
                        {offerExpiry(entry.offer.expiresAt)}
                      </p>

                      <div className="actions">
                        <button
                            disabled={
                                offerActionId === entry.offer.id
                            }
                            onClick={() =>
                                respondToOffer(
                                    entry.offer!.id,
                                    true
                                )
                            }
                        >
                          {offerActionId === entry.offer.id
                              ? "Processing…"
                              : "Accept offer"}
                        </button>

                        <button
                            className="secondary"
                            disabled={
                                offerActionId === entry.offer.id
                            }
                            onClick={() =>
                                respondToOffer(
                                    entry.offer!.id,
                                    false
                                )
                            }
                        >
                          Decline offer
                        </button>
                      </div>
                    </>
                )}

                {entry.status === "OFFERED" &&
                    !entry.offer && (
                        <p>
                          Offer details are unavailable. Refresh
                          the page to check the latest status.
                        </p>
                    )}
              </article>
          ))}
        </section>

        {/* CONFIRMED REGISTRATIONS */}
        <section>
          <h2>Confirmed Registrations</h2>

          {loading && (
              <p>Loading your registrations…</p>
          )}

          {!loading &&
              confirmedRegistrations.length === 0 && (
                  <p>
                    You don't have any confirmed registrations.
                  </p>
              )}

          {confirmedRegistrations.map((registration) => (
              <article
                  className="panel"
                  key={registration.id}
              >
            <span className="badge">
              CONFIRMED
            </span>

                <h3>
                  <Link
                      to={`/events/${registration.eventId}`}
                  >
                    {registration.eventTitle}
                  </Link>
                </h3>

                <p>
                  Registered at:{" "}
                  {registrationTime(
                      registration.registeredAt
                  )}
                </p>

                <div className="actions">
                  <Link
                      className="button-link"
                      to={`/events/${registration.eventId}`}
                  >
                    View event
                  </Link>

                  <button
                      className="secondary"
                      disabled={
                          cancellingId === registration.id
                      }
                      onClick={() =>
                          cancelRegistration(registration.id)
                      }
                  >
                    {cancellingId === registration.id
                        ? "Cancelling…"
                        : "Cancel registration"}
                  </button>
                </div>
              </article>
          ))}
        </section>

        <button
            className="secondary"
            onClick={refreshAll}
            disabled={loading || waitlistLoading}
        >
          Refresh registrations and offers
        </button>
      </main>
  );
}