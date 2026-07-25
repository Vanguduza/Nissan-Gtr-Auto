"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { StaffNav } from "@/components/staff-nav";
import { useStaffAuth } from "@/components/staff-auth-context";
import styles from "@/components/account.module.css";

export default function StaffForbiddenPage() {
  const params = useSearchParams();
  const reason = params.get("reason");
  const ctx = useStaffAuth();

  const title =
    reason === "not-staff" || (ctx && !ctx.isStaff)
      ? "Staff only"
      : "Access denied";

  const lede =
    reason === "not-staff" || (ctx && !ctx.isStaff)
      ? "This account is not marked as staff. Contact an admin if you need access."
      : "Your staff role cannot open this module. Use the hub for surfaces you can access, or ask an admin to assign the right role.";

  return (
    <div className={styles.shell}>
      {ctx?.isStaff ? <StaffNav current="/staff/forbidden" /> : null}
      <div className={styles.panel}>
        <h1 className={styles.title}>{title}</h1>
        <p className={styles.lede}>{lede}</p>
        <p className={styles.muted}>
          {ctx?.isStaff ? (
            <Link href="/staff">Back to staff hub</Link>
          ) : (
            <Link href="/">Back to storefront</Link>
          )}
        </p>
      </div>
    </div>
  );
}
