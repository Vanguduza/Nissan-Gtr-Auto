"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import styles from "@/components/account.module.css";
import {
  completeHrOnboarding,
  createHrOnboardingAuthUser,
  downloadBrandedBusinessCardPdf,
  downloadBrandedIdCardPdf,
  listHrGrades,
  listHrOnboardingDrafts,
  listHrRoles,
  saveHrOnboardingStage,
  type HrGradeOption,
  type HrOnboardingDraft,
  type HrOnboardingStage,
  type HrRoleOption,
} from "@/lib/staff-hr";
import { requireSession } from "@/lib/customer-storefront";
import { createWebClient } from "@/lib/supabase";

/** UI order per brief: personal → banking/health → biometrics → contract → completion */
const WIZARD_STAGES: Array<{
  key: HrOnboardingStage;
  label: string;
}> = [
  { key: "personal", label: "Personal" },
  { key: "banking_health", label: "Banking & health" },
  { key: "documents", label: "Biometrics photo" },
  { key: "role_contract", label: "Contract signing" },
  { key: "credentials", label: "Completion" },
];

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | {
      kind: "ready";
      drafts: HrOnboardingDraft[];
      grades: HrGradeOption[];
      roles: HrRoleOption[];
    };

export function StaffHrOnboardingWizard() {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [draftId, setDraftId] = useState<string | null>(null);
  const [stage, setStage] = useState<HrOnboardingStage>("personal");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const [fullName, setFullName] = useState("");
  const [dob, setDob] = useState("");
  const [nationalId, setNationalId] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [address, setAddress] = useState("");
  const [nextOfKin, setNextOfKin] = useState("");
  const [gradeId, setGradeId] = useState("");
  const [hrRoleId, setHrRoleId] = useState("");

  const [bankName, setBankName] = useState("");
  const [accountNumber, setAccountNumber] = useState("");
  const [branch, setBranch] = useState("");
  const [medicalAid, setMedicalAid] = useState("");
  const [allergies, setAllergies] = useState("");
  const [emergencyContact, setEmergencyContact] = useState("");

  const [photoNote, setPhotoNote] = useState("");
  const [photoFileName, setPhotoFileName] = useState<string | null>(null);
  const [contractSigned, setContractSigned] = useState(false);
  const [signatureName, setSignatureName] = useState("");
  const [completion, setCompletion] = useState<Record<string, unknown> | null>(
    null,
  );

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
    const [drafts, grades, roles] = await Promise.all([
      listHrOnboardingDrafts(client),
      listHrGrades(client),
      listHrRoles(client),
    ]);
    if (!drafts.ok) {
      setBoot({ kind: "error", message: drafts.error });
      return;
    }
    if (!grades.ok) {
      setBoot({ kind: "error", message: grades.error });
      return;
    }
    if (!roles.ok) {
      setBoot({ kind: "error", message: roles.error });
      return;
    }
    setBoot({
      kind: "ready",
      drafts: drafts.data,
      grades: grades.data,
      roles: roles.data,
    });
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const stageIndex = useMemo(
    () => WIZARD_STAGES.findIndex((s) => s.key === stage),
    [stage],
  );

  function loadDraft(d: HrOnboardingDraft) {
    setDraftId(d.id);
    setStage(d.stage);
    const p = d.payload ?? {};
    setFullName(String(p.full_name ?? ""));
    setDob(String(p.dob ?? ""));
    setNationalId(String(p.national_id ?? ""));
    setEmail(String(p.email ?? ""));
    setPhone(String(p.phone_e164 ?? ""));
    setAddress(String(p.address ?? ""));
    setNextOfKin(String(p.next_of_kin ?? ""));
    setGradeId(String(p.grade_id ?? ""));
    setHrRoleId(String(p.hr_role_id ?? ""));
    setPhotoNote(String(p.photo_note ?? ""));
    setPhotoFileName(String(p.photo_file_name ?? "") || null);
    setContractSigned(Boolean(p.contract_signed));
    setSignatureName(String(p.signature_name ?? ""));
    const b = d.banking_json ?? {};
    setBankName(String(b.bank_name ?? ""));
    setAccountNumber(String(b.account_number ?? ""));
    setBranch(String(b.branch ?? ""));
    const h = d.health_json ?? {};
    setMedicalAid(String(h.medical_aid ?? ""));
    setAllergies(String(h.allergies ?? ""));
    setEmergencyContact(String(h.emergency_contact ?? ""));
    setCompletion(null);
    setMessage(`Resumed draft ${d.id.slice(0, 8)}…`);
  }

  function buildPayload(): Record<string, unknown> {
    return {
      full_name: fullName.trim(),
      dob: dob || null,
      national_id: nationalId.trim() || null,
      email: email.trim() || null,
      phone_e164: phone.trim() || null,
      address: address.trim() || null,
      next_of_kin: nextOfKin.trim() || null,
      grade_id: gradeId || null,
      hr_role_id: hrRoleId || null,
      photo_note: photoNote.trim() || null,
      photo_file_name: photoFileName,
      contract_signed: contractSigned,
      signature_name: signatureName.trim() || null,
      photo_bridge:
        "Android: bridges biometric photo capture — web file upload only (no HTML5 camera/QR)",
    };
  }

  async function saveCurrent(nextStage?: HrOnboardingStage) {
    const client = createWebClient();
    if (!client) return;
    setBusy(true);
    setMessage(null);
    const target = nextStage ?? stage;
    const res = await saveHrOnboardingStage(client, {
      draftId,
      stage: target,
      payload: buildPayload(),
      bankingJson:
        target === "banking_health" || stage === "banking_health" || draftId
          ? {
              bank_name: bankName.trim(),
              account_number: accountNumber.trim(),
              branch: branch.trim(),
            }
          : undefined,
      healthJson:
        target === "banking_health" || stage === "banking_health" || draftId
          ? {
              medical_aid: medicalAid.trim(),
              allergies: allergies.trim(),
              emergency_contact: emergencyContact.trim(),
            }
          : undefined,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setDraftId(res.data);
    if (nextStage) setStage(nextStage);
    setMessage(`Saved · stage ${target}`);
    await refresh();
  }

  async function onAdvance(e: FormEvent) {
    e.preventDefault();
    if (stage === "personal") {
      if (!fullName.trim() || !email.trim() || !phone.trim() || !gradeId) {
        setMessage("Name, email, phone, and grade are required.");
        return;
      }
      await saveCurrent("banking_health");
      return;
    }
    if (stage === "banking_health") {
      if (!bankName.trim() || !accountNumber.trim()) {
        setMessage("Bank name and account number required.");
        return;
      }
      await saveCurrent("documents");
      return;
    }
    if (stage === "documents") {
      if (!photoFileName && !photoNote.trim()) {
        setMessage(
          "Attach a photo file (web) or note Bridge capture path for Android.",
        );
        return;
      }
      await saveCurrent("role_contract");
      return;
    }
    if (stage === "role_contract") {
      if (!contractSigned || !signatureName.trim()) {
        setMessage("Capture signature name and confirm contract signed.");
        return;
      }
      await saveCurrent("credentials");
      return;
    }
    if (stage === "credentials") {
      if (!draftId) {
        setMessage("Save earlier stages first.");
        return;
      }
      const client = createWebClient();
      if (!client) return;
      setBusy(true);
      setMessage(null);
      await saveCurrent("credentials");
      const res = await completeHrOnboarding(client, draftId);
      if (!res.ok) {
        setBusy(false);
        setMessage(res.error);
        return;
      }

      const employeeId =
        typeof res.data.employee_id === "string" ? res.data.employee_id : null;
      const existingUserId =
        typeof res.data.user_id === "string" && res.data.user_id
          ? res.data.user_id
          : null;

      let authSummary: Record<string, unknown> | null = null;
      if (employeeId && !existingUserId) {
        const authRes = await createHrOnboardingAuthUser(client, employeeId);
        if (!authRes.ok) {
          setBusy(false);
          setCompletion({
            ...res.data,
            // Never surface temp_password_hint from RPC in the UI.
            temp_password_hint: undefined,
            auth_error: authRes.error,
          });
          setMessage(
            `Employee created (emp# ${String(res.data.employee_code ?? "")}) but auth link failed: ${authRes.error}`,
          );
          await refresh();
          return;
        }
        authSummary = {
          user_id: authRes.data.user_id,
          created: authRes.data.created,
          must_change_password: authRes.data.must_change_password,
          channels: authRes.data.channels,
        };
      }

      setBusy(false);
      const role =
        boot.kind === "ready"
          ? boot.roles.find((r) => r.id === hrRoleId)
          : null;
      const safeCompletion = {
        ...res.data,
        full_name: fullName,
        role_title: role?.title ?? null,
        temp_password_hint: undefined,
        auth: authSummary ?? {
          user_id: existingUserId,
          note: existingUserId
            ? "Existing user linked from draft; must_change_password set by RPC."
            : "No employee_id — auth create skipped.",
        },
      };
      setCompletion(safeCompletion);
      setMessage(
        existingUserId
          ? `Completed · emp# ${String(res.data.employee_code ?? "")} · existing user linked.`
          : `Completed · emp# ${String(res.data.employee_code ?? "")} · auth user created; credentials via outbox (email/SMS/WA).`,
      );
      await refresh();
    }
  }

  async function onDownloadIdCard() {
    if (!completion) {
      setMessage("Complete onboarding first to export ID card.");
      return;
    }
    const client = createWebClient();
    if (!client) return;
    const session = await client.auth.getSession();
    const token = session.data.session?.access_token;
    if (!token) {
      setMessage("Sign in required for ID card PDF.");
      return;
    }
    const role =
      boot.kind === "ready"
        ? boot.roles.find((r) => r.id === hrRoleId)
        : null;
    setBusy(true);
    const pdf = await downloadBrandedIdCardPdf(token, {
      storeName: "Nissan GTR Auto",
      fullName: String(completion.full_name ?? fullName),
      roleTitle: role?.title ?? String(completion.role_title ?? "Staff"),
      employeeCode: String(completion.employee_code ?? ""),
      verifyUrl: completion.employee_id
        ? `https://nissangtrauto.co.zw/staff/verify/${String(completion.employee_id)}`
        : null,
    });
    setBusy(false);
    setMessage(
      pdf.ok
        ? "ID card PDF downloaded (CR80 85.6×54 mm · no fiscal QR)."
        : pdf.error,
    );
  }

  async function onDownloadBusinessCard() {
    if (!completion) {
      setMessage("Complete onboarding first to export business card.");
      return;
    }
    const client = createWebClient();
    if (!client) return;
    const session = await client.auth.getSession();
    const token = session.data.session?.access_token;
    if (!token) {
      setMessage("Sign in required for business card PDF.");
      return;
    }
    const role =
      boot.kind === "ready"
        ? boot.roles.find((r) => r.id === hrRoleId)
        : null;
    setBusy(true);
    const pdf = await downloadBrandedBusinessCardPdf(token, {
      storeName: "Nissan GTR Auto",
      fullName: String(completion.full_name ?? fullName),
      roleTitle: role?.title ?? String(completion.role_title ?? "Staff"),
      employeeCode: String(completion.employee_code ?? ""),
      phone: phone || null,
      email: email || null,
      domain: "nissangtrauto.co.zw",
    });
    setBusy(false);
    setMessage(
      pdf.ok
        ? "Business card PDF downloaded (90×50 mm)."
        : pdf.error,
    );
  }

  if (boot.kind === "loading") {
    return <p className={styles.muted}>Loading onboarding…</p>;
  }
  if (boot.kind === "auth") {
    return <p className={styles.muted}>Sign in as HR/admin to continue.</p>;
  }
  if (boot.kind === "error") {
    return (
      <p className={styles.muted} role="alert">
        {boot.message}
      </p>
    );
  }

  return (
    <div>
      <p className={styles.muted}>
        Five-stage resumable onboarding. Banking &amp; health stay RLS-tight
        (HR/admin). Web photo = file upload only — Android camera via Bridge.
        No HTML5 QR.
      </p>

      {boot.drafts.length > 0 ? (
        <fieldset className={styles.fieldset}>
          <legend className={styles.legend}>Resume draft</legend>
          <ul className={styles.list}>
            {boot.drafts.map((d) => (
              <li key={d.id}>
                <button
                  type="button"
                  className={styles.btnGhost}
                  disabled={busy}
                  onClick={() => loadDraft(d)}
                >
                  {d.id.slice(0, 8)}… · {d.stage} ·{" "}
                  {String((d.payload as { full_name?: string }).full_name ?? "—")}
                </button>
              </li>
            ))}
          </ul>
          <button
            type="button"
            className={styles.btnGhost}
            disabled={busy}
            onClick={() => {
              setDraftId(null);
              setStage("personal");
              setCompletion(null);
              setMessage("Started new draft.");
            }}
          >
            New draft
          </button>
        </fieldset>
      ) : null}

      <nav aria-label="Onboarding stages" style={{ margin: "1rem 0" }}>
        <ol className={styles.list}>
          {WIZARD_STAGES.map((s, i) => (
            <li key={s.key}>
              {i <= stageIndex ? "●" : "○"} {s.label}
              {s.key === stage ? " (current)" : ""}
            </li>
          ))}
        </ol>
      </nav>

      <form className={styles.form} onSubmit={(e) => void onAdvance(e)}>
        {stage === "personal" ? (
          <fieldset className={styles.fieldset}>
            <legend className={styles.legend}>Personal information</legend>
            <div className={styles.formGrid}>
              <label className={styles.field}>
                Full name
                <input
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  required
                  disabled={busy}
                />
              </label>
              <label className={styles.field}>
                Date of birth
                <input
                  type="date"
                  value={dob}
                  onChange={(e) => setDob(e.target.value)}
                  disabled={busy}
                />
              </label>
              <label className={styles.field}>
                National ID
                <input
                  value={nationalId}
                  onChange={(e) => setNationalId(e.target.value)}
                  disabled={busy}
                />
              </label>
              <label className={styles.field}>
                Email (login)
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  disabled={busy}
                />
              </label>
              <label className={styles.field}>
                Phone (login)
                <input
                  type="tel"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  required
                  disabled={busy}
                />
              </label>
              <label className={styles.field}>
                Address
                <input
                  value={address}
                  onChange={(e) => setAddress(e.target.value)}
                  disabled={busy}
                />
              </label>
              <label className={styles.field}>
                Next of kin
                <input
                  value={nextOfKin}
                  onChange={(e) => setNextOfKin(e.target.value)}
                  disabled={busy}
                />
              </label>
              <label className={styles.field}>
                Grade
                <select
                  value={gradeId}
                  onChange={(e) => setGradeId(e.target.value)}
                  required
                  disabled={busy}
                >
                  <option value="">Select grade</option>
                  {boot.grades.map((g) => (
                    <option key={g.id} value={g.id}>
                      {g.code} — {g.title}
                    </option>
                  ))}
                </select>
              </label>
              <label className={styles.field}>
                Organogram role
                <select
                  value={hrRoleId}
                  onChange={(e) => setHrRoleId(e.target.value)}
                  disabled={busy}
                >
                  <option value="">Optional</option>
                  {boot.roles.map((r) => (
                    <option key={r.id} value={r.id}>
                      {r.title}
                    </option>
                  ))}
                </select>
              </label>
            </div>
          </fieldset>
        ) : null}

        {stage === "banking_health" ? (
          <fieldset className={styles.fieldset}>
            <legend className={styles.legend}>
              Banking &amp; health (HR/admin only)
            </legend>
            <div className={styles.formGrid}>
              <label className={styles.field}>
                Bank
                <input
                  value={bankName}
                  onChange={(e) => setBankName(e.target.value)}
                  required
                  disabled={busy}
                />
              </label>
              <label className={styles.field}>
                Account number
                <input
                  value={accountNumber}
                  onChange={(e) => setAccountNumber(e.target.value)}
                  required
                  disabled={busy}
                />
              </label>
              <label className={styles.field}>
                Branch
                <input
                  value={branch}
                  onChange={(e) => setBranch(e.target.value)}
                  disabled={busy}
                />
              </label>
              <label className={styles.field}>
                Medical aid
                <input
                  value={medicalAid}
                  onChange={(e) => setMedicalAid(e.target.value)}
                  disabled={busy}
                />
              </label>
              <label className={styles.field}>
                Allergies
                <input
                  value={allergies}
                  onChange={(e) => setAllergies(e.target.value)}
                  disabled={busy}
                />
              </label>
              <label className={styles.field}>
                Emergency medical contact
                <input
                  value={emergencyContact}
                  onChange={(e) => setEmergencyContact(e.target.value)}
                  disabled={busy}
                />
              </label>
            </div>
          </fieldset>
        ) : null}

        {stage === "documents" ? (
          <fieldset className={styles.fieldset}>
            <legend className={styles.legend}>Biometrics — profile photo</legend>
            <p className={styles.muted}>
              Capture only (not fingerprint/face matching). Android management:
              Bridge <code>biometric-photo</code> (CameraX). Web HR desk: file
              upload only — no browser camera / HTML5 QR.
            </p>
            <label className={styles.field}>
              Photo file
              <input
                type="file"
                accept="image/*"
                disabled={busy}
                onChange={(e) => {
                  const f = e.target.files?.[0];
                  setPhotoFileName(f?.name ?? null);
                }}
              />
            </label>
            <label className={styles.field}>
              Bridge / capture note
              <input
                value={photoNote}
                onChange={(e) => setPhotoNote(e.target.value)}
                placeholder="e.g. Android bridge path pending"
                disabled={busy}
              />
            </label>
          </fieldset>
        ) : null}

        {stage === "role_contract" ? (
          <fieldset className={styles.fieldset}>
            <legend className={styles.legend}>Contract signing</legend>
            <p className={styles.muted}>
              Desk onboarding: typed signature stand-in. Signed PDF render via{" "}
              <code>render-branded-doc</code> / documents package.
            </p>
            <label className={styles.field}>
              Signer full name
              <input
                value={signatureName}
                onChange={(e) => setSignatureName(e.target.value)}
                required
                disabled={busy}
              />
            </label>
            <label className={styles.field}>
              <input
                type="checkbox"
                checked={contractSigned}
                onChange={(e) => setContractSigned(e.target.checked)}
                disabled={busy}
              />{" "}
              I confirm the role contract was reviewed and signed
            </label>
          </fieldset>
        ) : null}

        {stage === "credentials" ? (
          <fieldset className={styles.fieldset}>
            <legend className={styles.legend}>Completion</legend>
            <p className={styles.muted}>
              Completing assigns employee number{" "}
              <code>GTR{"{grade}"}{"{seq}"}</code>, marks draft done, then calls{" "}
              <code>hr-onboarding-create-auth</code> when no{" "}
              <code>user_id</code> is linked. Temp password stays on the server
              and is delivered via outbox (email/SMS/WA) — never shown here.
            </p>
            {completion ? (
              <>
                <pre className={styles.muted} style={{ whiteSpace: "pre-wrap" }}>
                  {JSON.stringify(completion, null, 2)}
                </pre>
                <div className={styles.formActions} style={{ marginTop: "0.85rem" }}>
                  <button
                    type="button"
                    className={styles.btnGhost}
                    disabled={busy}
                    onClick={() => void onDownloadIdCard()}
                  >
                    Download ID card PDF
                  </button>
                  <button
                    type="button"
                    className={styles.btnGhost}
                    disabled={busy}
                    onClick={() => void onDownloadBusinessCard()}
                  >
                    Download business card PDF
                  </button>
                </div>
                <p className={styles.muted}>
                  CR80 85.6×54 mm ID · 90×50 mm business card via{" "}
                  <code>render-branded-doc</code> — no ZIMRA fiscal QR.
                </p>
              </>
            ) : null}
          </fieldset>
        ) : null}

        <div className={styles.formActions}>
          <button
            type="button"
            className={styles.btnGhost}
            disabled={busy || stageIndex === 0}
            onClick={() => {
              const prev = WIZARD_STAGES[stageIndex - 1];
              if (prev) setStage(prev.key);
            }}
          >
            Back
          </button>
          <button
            type="button"
            className={styles.btnGhost}
            disabled={busy}
            onClick={() => void saveCurrent()}
          >
            Save
          </button>
          <button type="submit" className={styles.btn} disabled={busy}>
            {stage === "credentials" ? "Complete onboarding" : "Save & continue"}
          </button>
        </div>
      </form>

      {message ? (
        <p className={styles.muted} role="status">
          {message}
        </p>
      ) : null}
    </div>
  );
}
