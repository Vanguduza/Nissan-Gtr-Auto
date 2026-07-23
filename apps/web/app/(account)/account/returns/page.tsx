import { AccountNav } from "@/components/account-nav";
import styles from "../account.module.css";

export const metadata = { title: "Returns" };

export default function ReturnsPage() {
  return (
    <div className={styles.shell}>
      <AccountNav current="/account/returns" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Returns</h1>
        <p className={styles.lede}>
          Request a return against an invoice. Stock always routes to{" "}
          <strong>Quarantine</strong> — never a direct exchange to saleable.
        </p>
        <p className={styles.muted}>
          Portal submits to Phase 5 credit-note / quarantine APIs when signed
          in.
        </p>
        <button type="button" className={styles.btn} disabled>
          Start return (sign in)
        </button>
      </div>
    </div>
  );
}
