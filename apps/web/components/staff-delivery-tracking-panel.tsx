"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import { StaffDeliveryLiveMap } from "@/components/staff-delivery-live-map";
import {
  deliveryLocationInsertChannel,
  fetchRecentDeliveryLocations,
  listDeliveryJobs,
  requireSession,
  type DeliveryJobOption,
  type DeliveryLocationPoint,
} from "@/lib/staff-delivery-tracking";
import { createWebClient } from "@/lib/supabase";

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

export function StaffDeliveryTrackingPanel() {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [jobId, setJobId] = useState("");
  const [pointsState, setPointsState] = useState<PointsState>({ kind: "idle" });
  const [live, setLive] = useState(false);
  const [realtimeMessage, setRealtimeMessage] = useState<string | null>(null);

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

  useEffect(() => {
    void refreshJobs();
  }, [refreshJobs]);

  useEffect(() => {
    if (boot.kind !== "ready" || !jobId) {
      setPointsState({ kind: "idle" });
      setLive(false);
      setRealtimeMessage(null);
      return;
    }

    const client = createWebClient();
    if (!client) return;

    let cancelled = false;
    setRealtimeMessage(null);
    setPointsState({ kind: "loading" });
    setLive(false);

    void (async () => {
      const initial = await fetchRecentDeliveryLocations(client, jobId);
      if (cancelled) return;
      if (!initial.ok) {
        setPointsState({ kind: "error", message: initial.error });
        return;
      }
      setPointsState({ kind: "ready", points: initial.data });
    })();

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
      void client.removeChannel(channel);
    };
  }, [boot.kind, jobId]);

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
          Drivers send GPS from the Android management app. This web page never
          uses browser geolocation.
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
    pointsState.kind === "ready" ? pointsState.points : ([] as DeliveryLocationPoint[]);
  const showMap = Boolean(jobId) && pointsState.kind !== "error";

  return (
    <div className={styles.form}>
      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Delivery job</legend>
        <p className={styles.muted} style={{ marginBottom: "0.75rem" }}>
          Map subscribes to <code>delivery_locations</code> Realtime inserts for
          the selected job. Drivers publish GPS from the{" "}
          <strong>Android management</strong> app (bridge). Web is
          subscribe-only — no browser geolocation and no{" "}
          <code>ingest_delivery_location</code>.
        </p>
        {boot.jobs.length === 0 ? (
          <p className={styles.muted} role="status">
            No delivery jobs yet. Create one under{" "}
            <Link href="/staff/logistics">Logistics</Link>, then return here to
            watch the trail.
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
                  {job.document_number ?? job.id.slice(0, 8)} · {job.status}
                </option>
              ))}
            </select>
          </label>
        )}
      </fieldset>

      {pointsState.kind === "loading" ? (
        <p className={styles.muted}>Loading location points…</p>
      ) : null}

      {pointsState.kind === "error" ? (
        <p className={styles.lede} role="alert">
          Could not load points: {pointsState.message}
        </p>
      ) : null}

      {pointsState.kind === "ready" && points.length === 0 && jobId ? (
        <p className={styles.muted} role="status">
          No GPS points for this job yet. When the driver&apos;s Android
          management device ingests locations, the marker and trail appear
          here via Realtime.
        </p>
      ) : null}

      {showMap ? (
        <StaffDeliveryLiveMap points={points} live={live} />
      ) : null}

      {realtimeMessage ? (
        <p className={styles.lede} role="alert">
          {realtimeMessage}
        </p>
      ) : null}
    </div>
  );
}
