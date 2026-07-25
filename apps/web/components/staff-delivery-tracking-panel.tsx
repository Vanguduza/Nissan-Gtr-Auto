"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import { StaffDeliveryLiveMap } from "@/components/staff-delivery-live-map";
import {
  formatEtaLabel,
  type CustomerTrackPoint,
} from "@/lib/customer-delivery-track";
import {
  assignDeliveryJob,
  deliveryLocationInsertChannel,
  dispatchDeliveryJob,
  fetchRecentDeliveryLocations,
  fetchStaffTrackPoint,
  listDeliveryJobs,
  optimizeDriverStops,
  requireSession,
  setDeliveryJobGeo,
  suggestDeliveryAssignees,
  type AssigneeSuggestion,
  type DeliveryJobOption,
  type DeliveryLocationPoint,
} from "@/lib/staff-delivery-tracking";
import { createWebClient } from "@/lib/supabase";

const TRACK_POINT_POLL_MS = 20_000;

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; jobs: DeliveryJobOption[] };

type PointsState =
  | { kind: "idle" }
  | { kind: "loading" }
  | { kind: "error"; message: string }
  | { kind: "ready"; points: DeliveryLocationPoint[] };

function mergePoint(
  prev: DeliveryLocationPoint[],
  next: DeliveryLocationPoint,
): DeliveryLocationPoint[] {
  if (prev.some((p) => p.id === next.id)) return prev;
  const merged = [...prev, next];
  merged.sort(
    (a, b) =>
      new Date(a.recorded_at).getTime() - new Date(b.recorded_at).getTime(),
  );
  return merged;
}

function shortId(id: string) {
  return id.slice(0, 8);
}

function numOrEmpty(v: number | null | undefined): string {
  return v == null || Number.isNaN(v) ? "" : String(v);
}

function parseCoord(raw: string): number | null {
  const t = raw.trim();
  if (!t) return null;
  const n = Number(t);
  return Number.isFinite(n) ? n : Number.NaN;
}

export function StaffDeliveryTrackingPanel() {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [jobId, setJobId] = useState("");
  const [pointsState, setPointsState] = useState<PointsState>({ kind: "idle" });
  const [live, setLive] = useState(false);
  const [realtimeMessage, setRealtimeMessage] = useState<string | null>(null);
  const [suggestions, setSuggestions] = useState<AssigneeSuggestion[]>([]);
  const [suggestError, setSuggestError] = useState<string | null>(null);
  const [manualAssignee, setManualAssignee] = useState("");
  const [override, setOverride] = useState(false);
  const [busy, setBusy] = useState(false);
  const [actionMessage, setActionMessage] = useState<string | null>(null);
  const [shareToken, setShareToken] = useState<string | null>(null);
  const [trackPoint, setTrackPoint] = useState<CustomerTrackPoint | null>(null);
  const [pickupLat, setPickupLat] = useState("");
  const [pickupLng, setPickupLng] = useState("");
  const [dropoffLat, setDropoffLat] = useState("");
  const [dropoffLng, setDropoffLng] = useState("");

  const refreshJobs = useCallback(async () => {
    const client = createWebClient();
    if (!client) {
      setBoot({
        kind: "error",
        message: "Supabase is not configured on this environment.",
      });
      return;
    }
    const session = await requireSession(client);
    if (!session.ok) {
      setBoot({ kind: "auth" });
      return;
    }

    const jobs = await listDeliveryJobs(client);
    if (!jobs.ok) {
      setBoot({ kind: "error", message: jobs.error });
      return;
    }
    setBoot({ kind: "ready", jobs: jobs.data });
    setJobId((prev) => prev || jobs.data[0]?.id || "");
  }, []);

  const loadSuggestions = useCallback(async (id: string) => {
    const client = createWebClient();
    if (!client || !id) {
      setSuggestions([]);
      return;
    }
    setSuggestError(null);
    const res = await suggestDeliveryAssignees(client, id);
    if (!res.ok) {
      setSuggestions([]);
      setSuggestError(res.error);
      return;
    }
    setSuggestions(res.data);
  }, []);

  useEffect(() => {
    void refreshJobs();
  }, [refreshJobs]);

  useEffect(() => {
    if (boot.kind !== "ready" || !jobId) {
      setSuggestions([]);
      return;
    }
    void loadSuggestions(jobId);
  }, [boot.kind, jobId, loadSuggestions]);

  useEffect(() => {
    if (boot.kind !== "ready" || !jobId) {
      setPickupLat("");
      setPickupLng("");
      setDropoffLat("");
      setDropoffLng("");
      setShareToken(null);
      return;
    }
    const job = boot.jobs.find((j) => j.id === jobId);
    if (!job) return;
    setPickupLat(numOrEmpty(job.pickup_lat));
    setPickupLng(numOrEmpty(job.pickup_lng));
    setDropoffLat(numOrEmpty(job.dropoff_lat));
    setDropoffLng(numOrEmpty(job.dropoff_lng));
    setShareToken(null);
  }, [boot, jobId]);

  useEffect(() => {
    if (boot.kind !== "ready" || !jobId) {
      setPointsState({ kind: "idle" });
      setLive(false);
      setRealtimeMessage(null);
      setTrackPoint(null);
      return;
    }

    const client = createWebClient();
    if (!client) return;

    let cancelled = false;
    setRealtimeMessage(null);
    setPointsState({ kind: "loading" });
    setLive(false);
    setTrackPoint(null);

    void (async () => {
      const initial = await fetchRecentDeliveryLocations(client, jobId);
      if (cancelled) return;
      if (!initial.ok) {
        setPointsState({ kind: "error", message: initial.error });
        return;
      }
      setPointsState({ kind: "ready", points: initial.data });
    })();

    const refreshTrackPoint = async () => {
      const res = await fetchStaffTrackPoint(client, jobId);
      if (cancelled || !res.ok) return;
      setTrackPoint(res.data);
    };
    void refreshTrackPoint();
    const pollId = window.setInterval(
      () => void refreshTrackPoint(),
      TRACK_POINT_POLL_MS,
    );

    const channel = deliveryLocationInsertChannel(client, jobId, (point) => {
      setPointsState((prev) => {
        if (prev.kind === "ready") {
          return { kind: "ready", points: mergePoint(prev.points, point) };
        }
        if (prev.kind === "loading" || prev.kind === "idle") {
          return { kind: "ready", points: [point] };
        }
        return prev;
      });
      void refreshTrackPoint();
    });

    channel.subscribe((status) => {
      if (cancelled) return;
      setLive(status === "SUBSCRIBED");
      if (status === "CHANNEL_ERROR" || status === "TIMED_OUT") {
        setRealtimeMessage(
          `Realtime ${status.toLowerCase()} — check staff session / RLS (admin, warehouse, or dispatcher).`,
        );
      }
    });

    return () => {
      cancelled = true;
      setLive(false);
      window.clearInterval(pollId);
      void client.removeChannel(channel);
    };
  }, [boot.kind, jobId]);

  const selectedJob =
    boot.kind === "ready"
      ? boot.jobs.find((j) => j.id === jobId) ?? null
      : null;

  async function onAssign(userId: string, asOverride: boolean) {
    const client = createWebClient();
    if (!client || !jobId || !userId.trim()) return;
    setBusy(true);
    setActionMessage(null);
    const res = await assignDeliveryJob(
      client,
      jobId,
      userId.trim(),
      asOverride,
    );
    setBusy(false);
    if (!res.ok) {
      setActionMessage(res.error);
      return;
    }
    setActionMessage(
      `Assigned ${shortId(userId)}${asOverride ? " (override)" : ""}.`,
    );
    setManualAssignee("");
    await refreshJobs();
    await loadSuggestions(jobId);
  }

  async function onSaveGeo() {
    const client = createWebClient();
    if (!client || !jobId) return;
    const pLat = parseCoord(pickupLat);
    const pLng = parseCoord(pickupLng);
    const dLat = parseCoord(dropoffLat);
    const dLng = parseCoord(dropoffLng);
    if (
      Number.isNaN(pLat) ||
      Number.isNaN(pLng) ||
      Number.isNaN(dLat) ||
      Number.isNaN(dLng)
    ) {
      setActionMessage(
        "Coordinates must be valid numbers (or empty to clear).",
      );
      return;
    }
    if ((pLat == null) !== (pLng == null)) {
      setActionMessage("Pickup lat and lng must both be set or both empty.");
      return;
    }
    if ((dLat == null) !== (dLng == null)) {
      setActionMessage("Dropoff lat and lng must both be set or both empty.");
      return;
    }
    setBusy(true);
    setActionMessage(null);
    const res = await setDeliveryJobGeo(client, jobId, {
      pickupLat: pLat,
      pickupLng: pLng,
      dropoffLat: dLat,
      dropoffLng: dLng,
    });
    setBusy(false);
    if (!res.ok) {
      setActionMessage(res.error);
      return;
    }
    setActionMessage(
      "Pickup/dropoff geo saved (suggest + ETA use these coords).",
    );
    await refreshJobs();
    await loadSuggestions(jobId);
  }

  async function onDispatch() {
    const client = createWebClient();
    if (!client || !jobId) return;
    setBusy(true);
    setActionMessage(null);
    setShareToken(null);
    const res = await dispatchDeliveryJob(client, jobId);
    setBusy(false);
    if (!res.ok) {
      setActionMessage(res.error);
      return;
    }
    const token = res.data.track_token ?? null;
    setShareToken(token);
    if (token) {
      setActionMessage(
        "Marked dispatched. Share the customer track link below — do not remint (that revokes this token). SMS/WA enqueued via sms_outbox when gateway keys are set.",
      );
    } else {
      setActionMessage(
        "Marked dispatched, but no track_token in response. Check update_delivery_job_status migration.",
      );
    }
    await refreshJobs();
  }

  async function onOptimizeStops() {
    const client = createWebClient();
    const driverId = selectedJob?.assignee_user_id;
    if (!client || !driverId) return;
    setBusy(true);
    setActionMessage(null);
    const res = await optimizeDriverStops(client, driverId);
    setBusy(false);
    if (!res.ok) {
      setActionMessage(res.error);
      return;
    }
    const order = res.data
      .map((r) => `#${r.route_sequence} ${shortId(r.delivery_job_id)}`)
      .join(" → ");
    setActionMessage(
      order
        ? `Stop order: ${order}`
        : "No open stops to optimize for this driver.",
    );
    await refreshJobs();
  }

  if (boot.kind === "loading") {
    return <p className={styles.muted}>Loading delivery jobs…</p>;
  }

  if (boot.kind === "auth") {
    return (
      <div className={styles.form}>
        <p className={styles.lede} role="status">
          <Link href="/login">Sign in</Link> as staff with{" "}
          <strong>admin</strong>, <strong>warehouse</strong>, or{" "}
          <strong>dispatcher</strong> role to watch live delivery maps.
        </p>
        <p className={styles.muted}>
          Drivers send GPS from the dedicated delivery Android app (bridge).
          This web page never uses browser geolocation.
        </p>
      </div>
    );
  }

  if (boot.kind === "error") {
    return (
      <p className={styles.lede} role="alert">
        {boot.message}{" "}
        <button
          type="button"
          className={styles.btnGhost}
          onClick={() => void refreshJobs()}
        >
          Retry
        </button>
      </p>
    );
  }

  const points =
    pointsState.kind === "ready"
      ? pointsState.points
      : ([] as DeliveryLocationPoint[]);
  const mapPoints: DeliveryLocationPoint[] =
    points.length > 0
      ? points
      : trackPoint
        ? [
            {
              id: `rpc-last:${trackPoint.delivery_job_id}`,
              delivery_job_id: trackPoint.delivery_job_id,
              lat: trackPoint.lat,
              lng: trackPoint.lng,
              accuracy_m: null,
              recorded_at: trackPoint.recorded_at,
              ingested_at: trackPoint.recorded_at,
              source: "get_delivery_track_point",
            },
          ]
        : [];
  const showMap = pointsState.kind !== "error";
  const etaLabel =
    formatEtaLabel(
      trackPoint?.eta_at ?? selectedJob?.eta_at ?? null,
      trackPoint?.eta_seconds ?? selectedJob?.eta_seconds ?? null,
    ) ?? null;
  const etaSource = selectedJob?.eta_source ?? null;
  const sharePath = shareToken
    ? `/track/${encodeURIComponent(shareToken)}`
    : null;

  return (
    <div className={styles.form}>
      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Delivery job</legend>
        <p className={styles.muted} style={{ marginBottom: "0.75rem" }}>
          Map subscribes to <code>delivery_locations</code> Realtime inserts.
          Drivers publish GPS from the delivery Android app (bridge). Web is
          subscribe-only — no browser geolocation. Panic inbox:{" "}
          <Link href="/staff/logistics/panic">Panic alerts</Link>.
        </p>
        {boot.jobs.length === 0 ? (
          <p className={styles.muted} role="status">
            No delivery jobs yet. Create one under{" "}
            <Link href="/staff/logistics">Logistics</Link>, then return here.
          </p>
        ) : (
          <label className={styles.field}>
            Job
            <select
              value={jobId}
              onChange={(e) => setJobId(e.target.value)}
              aria-label="Select delivery job"
            >
              {boot.jobs.map((job) => (
                <option key={job.id} value={job.id}>
                  {job.document_number ?? shortId(job.id)} · {job.status}
                  {job.assignee_user_id
                    ? ` · ${shortId(job.assignee_user_id)}`
                    : ""}
                </option>
              ))}
            </select>
          </label>
        )}

        {selectedJob ? (
          <p
            className={styles.muted}
            style={{ marginTop: "0.75rem" }}
            role="status"
          >
            <strong>ETA</strong> {etaLabel ?? "—"}
            {etaSource ? ` (${etaSource})` : ""}
            {" · "}
            <strong>Assignee</strong>{" "}
            {selectedJob.assignee_user_id
              ? shortId(selectedJob.assignee_user_id)
              : "unassigned"}
            {trackPoint ? (
              <>
                {" · "}
                <strong>Last (RPC)</strong> {trackPoint.lat.toFixed(5)},{" "}
                {trackPoint.lng.toFixed(5)}
              </>
            ) : null}
          </p>
        ) : null}
      </fieldset>

      {selectedJob ? (
        <fieldset className={styles.fieldset}>
          <legend className={styles.legend}>Pickup / dropoff geo</legend>
          <p className={styles.muted} style={{ marginBottom: "0.75rem" }}>
            Used by <code>suggest_delivery_assignees</code> and Haversine ETA.
            Empty pair clears that endpoint. No browser GPS — enter coords
            manually or from address tools.
          </p>
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "1fr 1fr",
              gap: "0.65rem",
            }}
          >
            <label className={styles.field}>
              Pickup lat
              <input
                type="text"
                inputMode="decimal"
                value={pickupLat}
                onChange={(e) => setPickupLat(e.target.value)}
                disabled={busy}
                placeholder="-17.8292"
                aria-label="Pickup latitude"
              />
            </label>
            <label className={styles.field}>
              Pickup lng
              <input
                type="text"
                inputMode="decimal"
                value={pickupLng}
                onChange={(e) => setPickupLng(e.target.value)}
                disabled={busy}
                placeholder="31.0522"
                aria-label="Pickup longitude"
              />
            </label>
            <label className={styles.field}>
              Dropoff lat
              <input
                type="text"
                inputMode="decimal"
                value={dropoffLat}
                onChange={(e) => setDropoffLat(e.target.value)}
                disabled={busy}
                placeholder="-17.85"
                aria-label="Dropoff latitude"
              />
            </label>
            <label className={styles.field}>
              Dropoff lng
              <input
                type="text"
                inputMode="decimal"
                value={dropoffLng}
                onChange={(e) => setDropoffLng(e.target.value)}
                disabled={busy}
                placeholder="31.05"
                aria-label="Dropoff longitude"
              />
            </label>
          </div>
          <div className={styles.formActions} style={{ marginTop: "0.75rem" }}>
            <button
              type="button"
              className={styles.btn}
              disabled={busy}
              onClick={() => void onSaveGeo()}
            >
              Save geo
            </button>
          </div>
        </fieldset>
      ) : null}

      {selectedJob ? (
        <fieldset className={styles.fieldset}>
          <legend className={styles.legend}>Assign driver</legend>
          <p className={styles.muted} style={{ marginBottom: "0.75rem" }}>
            Suggestions from <code>suggest_delivery_assignees</code> (presence,
            capacity, distance). Use override to force an ineligible driver.
          </p>
          {suggestError ? (
            <p className={styles.lede} role="alert">
              Suggest failed: {suggestError}
            </p>
          ) : null}
          {suggestions.length === 0 && !suggestError ? (
            <p className={styles.muted}>No eligible drivers suggested.</p>
          ) : (
            <ul className={styles.list}>
              {suggestions.map((s) => (
                <li key={s.user_id}>
                  <strong>{shortId(s.user_id)}</strong>
                  <span className={styles.muted}>
                    {" "}
                    · {s.status} · open {s.open_jobs}/{s.capacity}
                    {s.distance_m != null
                      ? ` · ${Math.round(s.distance_m)} m`
                      : ""}
                  </span>
                  <div
                    className={styles.formActions}
                    style={{ marginTop: "0.45rem" }}
                  >
                    <button
                      type="button"
                      className={styles.btn}
                      disabled={busy}
                      onClick={() => void onAssign(s.user_id, false)}
                    >
                      Assign
                    </button>
                    <button
                      type="button"
                      className={styles.btnGhost}
                      disabled={busy}
                      onClick={() => void onAssign(s.user_id, true)}
                    >
                      Override
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          )}

          <label className={styles.field} style={{ marginTop: "0.85rem" }}>
            Manual assignee (user id)
            <input
              type="text"
              value={manualAssignee}
              onChange={(e) => setManualAssignee(e.target.value)}
              disabled={busy}
              placeholder="uuid"
              aria-label="Manual assignee user id"
            />
          </label>
          <label
            className={styles.field}
            style={{
              marginTop: "0.5rem",
              display: "flex",
              flexDirection: "row",
              alignItems: "center",
              gap: "0.5rem",
            }}
          >
            <input
              type="checkbox"
              checked={override}
              onChange={(e) => setOverride(e.target.checked)}
              disabled={busy}
            />
            Manual override (bypass eligibility)
          </label>
          <div className={styles.formActions} style={{ marginTop: "0.75rem" }}>
            <button
              type="button"
              className={styles.btn}
              disabled={busy || !manualAssignee.trim()}
              onClick={() => void onAssign(manualAssignee, override)}
            >
              Assign manual
            </button>
            {selectedJob.assignee_user_id ? (
              <button
                type="button"
                className={styles.btnGhost}
                disabled={busy}
                onClick={() => void onOptimizeStops()}
              >
                Optimize stops
              </button>
            ) : null}
            {selectedJob.status === "pending" ? (
              <button
                type="button"
                className={styles.btn}
                disabled={busy}
                onClick={() => void onDispatch()}
              >
                Mark dispatched
              </button>
            ) : null}
          </div>
          <p className={styles.muted} style={{ marginTop: "0.65rem" }}>
            Dispatch returns plaintext <code>track_token</code> once (SMS +
            share). Do not remint after dispatch. Customer link:{" "}
            <code>/track/[token]</code>.
          </p>
          {shareToken && sharePath ? (
            <div
              className={styles.formStatus}
              role="status"
              style={{ marginTop: "0.75rem" }}
            >
              <p style={{ margin: "0 0 0.35rem" }}>
                <strong>Share token</strong> (copy now — not recoverable later)
              </p>
              <p
                style={{
                  margin: "0 0 0.5rem",
                  fontFamily: "ui-monospace, monospace",
                  wordBreak: "break-all",
                }}
              >
                {shareToken}
              </p>
              <p style={{ margin: 0 }}>
                Link:{" "}
                <Link href={sharePath} target="_blank" rel="noreferrer">
                  {sharePath}
                </Link>
              </p>
            </div>
          ) : null}
        </fieldset>
      ) : null}

      {pointsState.kind === "loading" ? (
        <p className={styles.muted}>Loading location points…</p>
      ) : null}

      {pointsState.kind === "error" ? (
        <p className={styles.lede} role="alert">
          Could not load points: {pointsState.message}
        </p>
      ) : null}

      {pointsState.kind === "ready" &&
      points.length === 0 &&
      !trackPoint &&
      jobId ? (
        <p className={styles.muted} role="status">
          No GPS points for this job yet. When the driver&apos;s delivery app
          ingests locations, the marker and trail appear here via Realtime.
          {selectedJob?.status === "dispatched"
            ? " ETA refreshes via get_delivery_track_point while dispatched."
            : ""}
        </p>
      ) : null}

      {showMap ? (
        <StaffDeliveryLiveMap
          points={mapPoints}
          live={live}
          etaLabel={etaLabel}
        />
      ) : null}

      {realtimeMessage ? (
        <p className={styles.lede} role="alert">
          {realtimeMessage}
        </p>
      ) : null}

      {actionMessage ? (
        <p className={styles.lede} role="status">
          {actionMessage}
        </p>
      ) : null}
    </div>
  );
}
