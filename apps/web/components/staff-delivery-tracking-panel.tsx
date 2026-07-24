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
  const [points, setPoints] = useState<DeliveryLocationPoint[]>([]);
  const [live, setLive] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

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
      setPoints([]);
      setLive(false);
      return;
    }

    const client = createWebClient();
    if (!client) return;

    let cancelled = false;
    setMessage(null);
    setPoints([]);
    setLive(false);

    void (async () => {
      const initial = await fetchRecentDeliveryLocations(client, jobId);
      if (cancelled) return;
      if (!initial.ok) {
        setMessage(initial.error);
        setPoints([]);
        return;
      }
      setPoints(initial.data);
    })();

    const channel = deliveryLocationInsertChannel(client, jobId, (point) => {
      setPoints((prev) => mergePoint(prev, point));
    });

    channel.subscribe((status) => {
      if (cancelled) return;
      setLive(status === "SUBSCRIBED");
      if (status === "CHANNEL_ERROR" || status === "TIMED_OUT") {
        setMessage(
          `Realtime ${status.toLowerCase()} — check staff session / RLS.`,
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
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> with warehouse/dispatcher/admin staff
        to watch live delivery maps.
      </p>
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

  return (
    <div className={styles.form}>
      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Delivery job</legend>
        <p className={styles.muted} style={{ marginBottom: "0.75rem" }}>
          Map subscribes to <code>delivery_locations</code> Realtime inserts for
          the selected job. GPS is bridge-fed only — this page never uses browser
          geolocation.
        </p>
        {boot.jobs.length === 0 ? (
          <p className={styles.muted}>
            No delivery jobs yet. Create one under{" "}
            <Link href="/staff/logistics">Logistics</Link>.
          </p>
        ) : (
          <label className={styles.field}>
            Job
            <select
              value={jobId}
              onChange={(e) => setJobId(e.target.value)}
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

      {jobId ? <StaffDeliveryLiveMap points={points} live={live} /> : null}

      {message ? (
        <p className={styles.lede} role="status">
          {message}
        </p>
      ) : null}
    </div>
  );
}
