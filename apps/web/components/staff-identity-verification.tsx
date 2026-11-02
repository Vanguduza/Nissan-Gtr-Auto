"use client";
import { useEffect, useState } from "react";
import { createWebClient } from "@/lib/supabase";
import { staffRpc } from "@/lib/staff-rpc";
import styles from "./account.module.css";

type Identity = { full_name: string; employee_code: string; role_title: string | null; status: string };
export function StaffIdentityVerification({ employeeId }: { employeeId: string }) {
  const [identity, setIdentity] = useState<Identity | null>(null);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    let active = true;
    const client = createWebClient();
    if (!client) { setError("Staff verification is unavailable."); return; }
    void staffRpc(client, "verify_staff_identity", { p_employee_id: employeeId }).then(({data, error}) => {
      if (!active) return;
      if (error || !data || typeof data !== "object") { setError("This staff identity could not be verified."); return; }
      setIdentity(data as Identity);
    });
    return () => { active = false; };
  }, [employeeId]);
  if (error) return <p role="alert">{error}</p>;
  if (!identity) return <p role="status">Checking staff identity…</p>;
  return <div className={styles.pageBody}><h2>{identity.full_name}</h2>
    <p>Employee {identity.employee_code}</p><p>{identity.role_title ?? "Staff"}</p>
    <p>Status: <strong>{identity.status}</strong></p>
    <p className={styles.muted}>This card identifies a staff record. Access still requires the employee’s own sign-in.</p>
  </div>;
}
