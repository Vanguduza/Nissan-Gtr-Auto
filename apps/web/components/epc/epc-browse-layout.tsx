"use client";

import type { ReactNode } from "react";
import styles from "./epc-browse-layout.module.css";

export function EpcBrowseLayout({
  children,
  title,
  lede,
}: {
  children: ReactNode;
  title?: string;
  lede?: string;
}) {
  return (
    <div className={styles.layout}>
      {title ? <h1 className={styles.title}>{title}</h1> : null}
      {lede ? <p className={styles.lede}>{lede}</p> : null}
      {children}
    </div>
  );
}

export function EpcSkeleton({ label = "Loading…" }: { label?: string }) {
  return (
    <div className={styles.skeleton} role="status">
      {label}
    </div>
  );
}
