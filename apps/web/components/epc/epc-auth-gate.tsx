import Link from "next/link";
import styles from "./epc-browse-layout.module.css";

export function EpcAuthGate({ nextPath }: { nextPath: string }) {
  return (
    <div className={styles.layout}>
      <h1 className={styles.title}>Parts catalog</h1>
      <p className={styles.lede}>
        Sign in to browse EPC diagrams and fitment.{" "}
        <Link href={`/login?next=${encodeURIComponent(nextPath)}`}>
          Sign in
        </Link>
      </p>
    </div>
  );
}

export function EpcEmptyState({
  title = "Nothing here yet",
  message,
}: {
  title?: string;
  message: string;
}) {
  return (
    <div className={styles.empty}>
      <h2 className={styles.emptyTitle}>{title}</h2>
      <p className={styles.emptyMsg}>{message}</p>
    </div>
  );
}
