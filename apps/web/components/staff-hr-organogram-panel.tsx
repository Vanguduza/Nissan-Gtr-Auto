"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import {
  archiveHrRole,
  createHrGrade,
  createHrRole,
  listHrGrades,
  listHrRoles,
  requireSession,
  type HrGradeOption,
  type HrRoleOption,
} from "@/lib/staff-hr";
import { createWebClient } from "@/lib/supabase";

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; grades: HrGradeOption[]; roles: HrRoleOption[] };

/** Batch 1 §2.1 — grades + organogram roles (admin create/archive). */
export function StaffHrOrganogramPanel() {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [gradeCode, setGradeCode] = useState("");
  const [gradeTitle, setGradeTitle] = useState("");
  const [roleTitle, setRoleTitle] = useState("");
  const [roleGradeId, setRoleGradeId] = useState("");
  const [roleParentId, setRoleParentId] = useState("");
  const [roleDept, setRoleDept] = useState("");
  const [moduleAccess, setModuleAccess] = useState("pos,finance,hr");
  const [defaultStaffRole, setDefaultStaffRole] = useState("");

  const refresh = useCallback(async () => {
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
    const [g, r] = await Promise.all([
      listHrGrades(client),
      listHrRoles(client),
    ]);
    if (!g.ok) {
      setBoot({ kind: "error", message: g.error });
      return;
    }
    if (!r.ok) {
      setBoot({ kind: "error", message: r.error });
      return;
    }
    setBoot({ kind: "ready", grades: g.data, roles: r.data });
    setRoleGradeId((prev) => prev || g.data[0]?.id || "");
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function onCreateGrade(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client) return;
    setBusy(true);
    setMessage(null);
    const res = await createHrGrade(client, {
      code: gradeCode,
      title: gradeTitle,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setGradeCode("");
    setGradeTitle("");
    setMessage(`Grade saved · ${res.data.slice(0, 8)}…`);
    await refresh();
  }

  async function onCreateRole(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client || !roleGradeId) return;
    setBusy(true);
    setMessage(null);
    const modules = moduleAccess
      .split(/[,;\s]+/)
      .map((s) => s.trim())
      .filter(Boolean);
    const res = await createHrRole(client, {
      title: roleTitle,
      gradeId: roleGradeId,
      parentRoleId: roleParentId || null,
      department: roleDept || null,
      moduleAccess: modules,
      defaultStaffRole: defaultStaffRole || null,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setRoleTitle("");
    setRoleDept("");
    setDefaultStaffRole("");
    setMessage(`Role created · ${res.data.slice(0, 8)}…`);
    await refresh();
  }

  async function onArchive(roleId: string) {
    const client = createWebClient();
    if (!client) return;
    setBusy(true);
    setMessage(null);
    const res = await archiveHrRole(client, roleId);
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage("Role archived");
    await refresh();
  }

  if (boot.kind === "loading") {
    return <p className={styles.muted}>Loading organogram…</p>;
  }
  if (boot.kind === "auth") {
    return (
      <p className={styles.lede}>
        Sign in as admin/HR to manage the organogram.
      </p>
    );
  }
  if (boot.kind === "error") {
    return (
      <p className={styles.lede} role="alert">
        {boot.message}
      </p>
    );
  }

  return (
    <div>
      <p className={styles.muted}>
        Grades are admin-editable (A1, B2, …). Roles form a tree;{" "}
        <code>module_access</code> will gate POS/staff nav (Batch 1 §1.6). No
        PAYE/NSSA — pay frequency only drives gross payroll scheduling later.
      </p>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Grades</legend>
        <ul className={styles.list}>
          {boot.grades.map((g) => (
            <li key={g.id}>
              <strong>{g.code}</strong> — {g.title}
            </li>
          ))}
        </ul>
        <form onSubmit={(e) => void onCreateGrade(e)}>
          <div className={styles.formGrid}>
            <label className={styles.field}>
              Code
              <input
                value={gradeCode}
                onChange={(e) => setGradeCode(e.target.value)}
                placeholder="B2"
                disabled={busy}
                required
              />
            </label>
            <label className={styles.field}>
              Title
              <input
                value={gradeTitle}
                onChange={(e) => setGradeTitle(e.target.value)}
                placeholder="Finance manager"
                disabled={busy}
                required
              />
            </label>
          </div>
          <div className={styles.formActions}>
            <button type="submit" className={styles.btn} disabled={busy}>
              Add / update grade
            </button>
          </div>
        </form>
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Roles (tree)</legend>
        <ul className={styles.list}>
          {boot.roles.map((r) => {
            const grade =
              r.hr_grades?.code ??
              boot.grades.find((g) => g.id === r.grade_id)?.code ??
              "?";
            const parent = boot.roles.find((p) => p.id === r.parent_role_id);
            return (
              <li key={r.id}>
                <strong>{r.title}</strong> · {grade}
                {r.department ? ` · ${r.department}` : ""}
                {r.default_staff_role
                  ? ` · staff_role ${r.default_staff_role}`
                  : ""}
                {parent ? ` · reports to ${parent.title}` : " · top"}
                {" · "}
                <button
                  type="button"
                  className={styles.btnGhost}
                  disabled={busy}
                  onClick={() => void onArchive(r.id)}
                >
                  Archive
                </button>
              </li>
            );
          })}
        </ul>
        <form onSubmit={(e) => void onCreateRole(e)}>
          <div className={styles.formGrid}>
            <label className={styles.field}>
              Title
              <input
                value={roleTitle}
                onChange={(e) => setRoleTitle(e.target.value)}
                disabled={busy}
                required
              />
            </label>
            <label className={styles.field}>
              Grade
              <select
                value={roleGradeId}
                onChange={(e) => setRoleGradeId(e.target.value)}
                disabled={busy}
                required
              >
                {boot.grades.map((g) => (
                  <option key={g.id} value={g.id}>
                    {g.code} — {g.title}
                  </option>
                ))}
              </select>
            </label>
            <label className={styles.field}>
              Parent role
              <select
                value={roleParentId}
                onChange={(e) => setRoleParentId(e.target.value)}
                disabled={busy}
              >
                <option value="">(none — top)</option>
                {boot.roles.map((r) => (
                  <option key={r.id} value={r.id}>
                    {r.title}
                  </option>
                ))}
              </select>
            </label>
            <label className={styles.field}>
              Department
              <input
                value={roleDept}
                onChange={(e) => setRoleDept(e.target.value)}
                disabled={busy}
              />
            </label>
            <label className={styles.field}>
              Module access (comma)
              <input
                value={moduleAccess}
                onChange={(e) => setModuleAccess(e.target.value)}
                placeholder="pos,finance,hr"
                disabled={busy}
              />
            </label>
            <label className={styles.field}>
              Default staff role
              <select
                value={defaultStaffRole}
                onChange={(e) => setDefaultStaffRole(e.target.value)}
                disabled={busy}
              >
                <option value="">(none — onboarding picks)</option>
                <option value="driver">driver</option>
                <option value="sales">sales</option>
                <option value="warehouse">warehouse</option>
                <option value="finance">finance</option>
                <option value="dispatcher">dispatcher</option>
                <option value="hr">hr</option>
                <option value="admin">admin</option>
              </select>
            </label>
          </div>
          <div className={styles.formActions}>
            <button type="submit" className={styles.btn} disabled={busy}>
              Create role
            </button>
          </div>
        </form>
      </fieldset>

      {message ? (
        <p className={styles.formStatus} role="status">
          {message}
        </p>
      ) : null}
    </div>
  );
}
