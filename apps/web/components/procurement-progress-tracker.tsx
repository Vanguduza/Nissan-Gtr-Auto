"use client";

import {
  PROCUREMENT_STEP_LABELS,
  PROCUREMENT_TRACKER_STEPS,
  completedTrackerCount,
  type ProcurementProgressStep,
} from "@gtr/procurement";
import styles from "@/components/procurement-tracker.module.css";

type Props = {
  step: ProcurementProgressStep;
  documentLabel?: string;
};

export function ProcurementProgressTracker({ step, documentLabel }: Props) {
  const done = completedTrackerCount(step);
  const terminal = step === "rejected" || step === "cancelled";

  return (
    <div
      className={styles.tracker}
      role="status"
      aria-label={
        documentLabel
          ? `Progress for ${documentLabel}: ${PROCUREMENT_STEP_LABELS[step]}`
          : `Procurement progress: ${PROCUREMENT_STEP_LABELS[step]}`
      }
    >
      {terminal ? (
        <p className={styles.terminal}>{PROCUREMENT_STEP_LABELS[step]}</p>
      ) : (
        <ol className={styles.steps}>
          {PROCUREMENT_TRACKER_STEPS.map((s, i) => {
            const state =
              i < done - 1 ? "done" : i === done - 1 ? "current" : "todo";
            return (
              <li key={s} className={styles[state]} data-step={s}>
                <span className={styles.dot} aria-hidden />
                <span className={styles.label}>{PROCUREMENT_STEP_LABELS[s]}</span>
              </li>
            );
          })}
        </ol>
      )}
    </div>
  );
}
