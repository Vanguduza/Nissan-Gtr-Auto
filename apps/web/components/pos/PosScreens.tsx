"use client";

import { ArrowLeft, Building2, Car, FileText, Pause, Pin, Play, Search, Send, User } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { epcBox, sameOem } from "@/lib/pos/epc";
import { formatMoney } from "@/lib/pos/money";
import type {
  CustomerInput,
  EpcDiagram,
  EpcDiagramPart,
  EpcDiagramRef,
  EpcSection,
  GarageVehicle,
  ParkedCart,
  PosCustomer,
  Quotation,
  VehicleVariant,
} from "@/lib/pos/types";
import type { PosStore } from "@/lib/pos/use-pos";
import { PickupList, ResolveList } from "./RecoveryScreen";
import styles from "./pos.module.css";

const EMPTY_CUSTOMER: CustomerInput = {
  kind: "individual",
  displayName: "",
  businessName: null,
  email: null,
  phoneE164: null,
  whatsappE164: null,
};

export function Segment<T extends string>({
  value,
  options,
  onChange,
  label,
}: {
  value: T;
  options: Array<{ id: T; label: string }>;
  onChange: (v: T) => void;
  label: string;
}) {
  return (
    <div className={styles.segment} role="group" aria-label={label}>
      {options.map((o) => (
        <button
          key={o.id}
          type="button"
          aria-pressed={value === o.id}
          className={`${styles.segmentItem} ${value === o.id ? styles.segmentActive : ""}`}
          onClick={() => onChange(o.id)}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}

function when(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString("en-ZW", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }) : "";
}

// ───────── Customer ─────────
export function CustomerScreen({ pos }: { pos: PosStore }) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<PosCustomer[]>([]);
  const [editing, setEditing] = useState<{ id: string | null; input: CustomerInput } | null>(null);
  const [selected, setSelected] = useState<PosCustomer | null>(null);
  const [garage, setGarage] = useState<GarageVehicle[]>([]);

  const search = useCallback(async () => {
    const res = await pos.gateway.searchCustomers(query);
    if (res.ok) setResults(res.data);
  }, [pos.gateway, query]);

  useEffect(() => {
    void search();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!selected) return setGarage([]);
    void pos.gateway.listGarage(selected.id).then((r) => r.ok && setGarage(r.data));
  }, [selected, pos.gateway, pos.notice]);

  const field = (k: keyof CustomerInput, label: string, type = "text") =>
    editing ? (
      <label className={styles.field}>
        <span className={styles.fieldLabel}>{label}</span>
        <input
          className={styles.input}
          type={type}
          value={(editing.input[k] as string | null) ?? ""}
          onChange={(e) => setEditing({ ...editing, input: { ...editing.input, [k]: e.target.value || null } })}
        />
      </label>
    ) : null;

  return (
    <div className={styles.screenGrid}>
      <section className={styles.panel}>
        <h2 className={styles.panelTitle}>Customer</h2>
        <form
          className={styles.row}
          onSubmit={(e) => {
            e.preventDefault();
            void search();
          }}
        >
          <input className={styles.input} style={{ flex: 1 }} placeholder="Name, business, email or phone" value={query} onChange={(e) => setQuery(e.target.value)} aria-label="Search customers" />
          <button type="submit" className={`${styles.softButton} ${styles.inlineButton}`}>
            <Search size={16} aria-hidden /> Search
          </button>
          <button type="button" className={styles.primaryButton} onClick={() => setEditing({ id: null, input: EMPTY_CUSTOMER })}>
            New customer
          </button>
        </form>
        <div className={styles.list}>
          {results.length === 0 ? <div className={styles.emptyCard}>No customers match.</div> : null}
          {results.map((c) => (
            <div key={c.id} className={`${styles.listRow} ${selected?.id === c.id ? styles.listRowSelected : ""}`}>
              <button type="button" style={{ all: "unset", cursor: "pointer" }} onClick={() => setSelected(c)}>
                <div className={styles.listTitle}>
                  {c.kind === "business" ? <Building2 size={14} aria-hidden /> : <User size={14} aria-hidden />} {c.businessName || c.displayName}
                </div>
                <div className={styles.muted}>{[c.businessName ? c.displayName : null, c.phoneE164, c.email].filter(Boolean).join(" · ")}</div>
              </button>
              <div className={styles.row}>
                <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={() => setEditing({ id: c.id, input: { ...c } })}>
                  Edit
                </button>
                <button type="button" className={styles.primaryButton} onClick={() => { setSelected(c); void pos.selectCustomer(c); }}>
                  Add to sale
                </button>
              </div>
            </div>
          ))}
        </div>
      </section>

      <section className={styles.panel}>
        {editing ? (
          <form
            onSubmit={async (e) => {
              e.preventDefault();
              const saved = await pos.saveCustomer(editing.input, editing.id);
              if (saved) {
                setEditing(null);
                setSelected(saved);
                void search();
              }
            }}
          >
            <h2 className={styles.panelTitle}>{editing.id ? "Edit customer" : "New customer"}</h2>
            <Segment
              label="Customer type"
              value={editing.input.kind}
              options={[
                { id: "individual", label: "Individual" },
                { id: "business", label: "Business" },
              ]}
              onChange={(kind) => setEditing({ ...editing, input: { ...editing.input, kind } })}
            />
            <div className={styles.formGrid}>
              {field("displayName", editing.input.kind === "business" ? "Contact name" : "Full name")}
              {editing.input.kind === "business" ? field("businessName", "Business name") : null}
              {field("phoneE164", "Phone (+263…)", "tel")}
              {field("whatsappE164", "WhatsApp (+263…)", "tel")}
              {field("email", "Email", "email")}
            </div>
            <div className={styles.rowEnd}>
              <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={() => setEditing(null)}>
                Cancel
              </button>
              <button type="submit" className={styles.primaryButton} disabled={!editing.input.displayName.trim()}>
                Save customer
              </button>
            </div>
          </form>
        ) : (
          <>
            <h2 className={styles.panelTitle}>Garage</h2>
            {selected ? (
              <>
                <p className={styles.muted}>{selected.businessName || selected.displayName}</p>
                <div className={styles.list}>
                  {garage.length === 0 ? <div className={styles.emptyCard}>No vehicles saved yet.</div> : null}
                  {garage.map((g) => (
                    <div key={g.id} className={styles.listRow}>
                      <span>
                        <div className={styles.listTitle}>
                          <Car size={14} aria-hidden /> {g.model} {g.chassisCode}
                        </div>
                        <div className={styles.muted}>{g.engine ?? "Engine not set"}</div>
                      </span>
                      <button
                        type="button"
                        className={`${styles.softButton} ${styles.inlineButton}`}
                        onClick={() => void pos.applyVehicle({ modelSlug: g.modelSlug, chassisCode: g.chassisCode, engineCode: g.engine })}
                      >
                        Shop for this
                      </button>
                    </div>
                  ))}
                </div>
                <div className={styles.rowEnd}>
                  <button
                    type="button"
                    className={`${styles.softButton} ${styles.inlineButton}`}
                    disabled={!pos.vehicle || pos.cart?.customerId !== selected.id}
                    title={!pos.vehicle ? "Choose a vehicle in the header first" : pos.cart?.customerId !== selected.id ? "Add this customer to the sale first" : ""}
                    onClick={() => void pos.addVehicleToGarage()}
                  >
                    Save current vehicle to garage
                  </button>
                </div>
              </>
            ) : (
              <div className={styles.emptyCard}>Select a customer to see their vehicles.</div>
            )}
          </>
        )}
      </section>
    </div>
  );
}

// ───────── Quick Sale ─────────
export function QuickSaleScreen({ pos, onQuote }: { pos: PosStore; onQuote: () => void }) {
  const [percent, setPercent] = useState("");
  const [overrideLine, setOverrideLine] = useState("");
  const [overridePrice, setOverridePrice] = useState("");
  const lines = pos.cart?.lines ?? [];
  const locked = lines.length > 0;
  return (
    <div className={styles.screenGrid}>
      <section className={styles.panel}>
        <h2 className={styles.panelTitle}>Sale setup</h2>
        <p className={styles.muted}>{locked ? "Locked while parts are on the sale." : "Applies to the next part added."}</p>
        <div className={styles.list}>
          <div className={styles.field}>
            <span className={styles.fieldLabel}>Warehouse</span>
            <Segment
              label="Warehouse"
              value={pos.setup.warehouseId ?? ""}
              options={pos.warehouses.map((w) => ({ id: w.id, label: `${w.code} · ${w.name}` }))}
              onChange={(warehouseId) => pos.changeSetup({ warehouseId })}
            />
          </div>
          <div className={styles.field}>
            <span className={styles.fieldLabel}>Currency</span>
            <Segment
              label="Currency"
              value={pos.setup.currency}
              options={[
                { id: "USD", label: "US$" },
                { id: "ZIG", label: "ZiG" },
              ]}
              onChange={(currency) => pos.changeSetup({ currency })}
            />
          </div>
          <div className={styles.field}>
            <span className={styles.fieldLabel}>Fulfilment</span>
            <Segment
              label="Fulfilment"
              value={pos.setup.fulfillmentMode}
              options={[
                { id: "immediate", label: "Collect now" },
                { id: "dispatch", label: "Dispatch" },
              ]}
              onChange={(fulfillmentMode) => pos.changeSetup({ fulfillmentMode })}
            />
          </div>
        </div>
      </section>

      <section className={styles.panel}>
        <h2 className={styles.panelTitle}>Sale actions</h2>
        <p className={styles.muted}>Discounts, price overrides and voids need a reason. A manager signs in when the shop's approval policy asks for one.</p>
        <div className={styles.formGrid}>
          <label className={styles.field}>
            <span className={styles.fieldLabel}>Discount %</span>
            <input className={styles.input} type="number" min={0} max={100} step="0.5" value={percent} onChange={(e) => setPercent(e.target.value)} />
          </label>
          <button
            type="button"
            className={`${styles.softButton} ${styles.inlineButton}`}
            style={{ alignSelf: "flex-end" }}
            disabled={!locked || !(Number(percent) > 0 && Number(percent) <= 100)}
            onClick={() => pos.requestManager({ kind: "discount", percent: Number(percent) })}
          >
            Apply discount
          </button>
        </div>
        <div className={styles.formGrid}>
          <label className={styles.field}>
            <span className={styles.fieldLabel}>Line</span>
            <select className={styles.input} value={overrideLine} onChange={(e) => setOverrideLine(e.target.value)}>
              <option value="">Choose a line</option>
              {lines.map((l) => (
                <option key={l.id} value={l.id}>
                  {l.name} ({formatMoney(l.unitPrice, pos.currency)})
                </option>
              ))}
            </select>
          </label>
          <label className={styles.field}>
            <span className={styles.fieldLabel}>New unit price</span>
            <input className={styles.input} type="number" min={0} step="0.01" value={overridePrice} onChange={(e) => setOverridePrice(e.target.value)} />
          </label>
        </div>
        <div className={styles.rowEnd}>
          <button
            type="button"
            className={`${styles.softButton} ${styles.inlineButton}`}
            disabled={!overrideLine || !(Number(overridePrice) >= 0) || overridePrice === ""}
            onClick={() => {
              const line = lines.find((l) => l.id === overrideLine);
              if (line) pos.requestManager({ kind: "override", lineId: line.id, lineName: line.name, unitPrice: Number(overridePrice) });
            }}
          >
            Override price
          </button>
        </div>
        <div className={styles.rowEnd}>
          <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} disabled={!locked} onClick={() => void pos.parkCurrent()}>
            <Pause size={16} aria-hidden /> Park sale
          </button>
          <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} disabled={!locked} onClick={onQuote}>
            <FileText size={16} aria-hidden /> Create quotation
          </button>
          <button type="button" className={styles.primaryButton} disabled={!locked} onClick={() => pos.requestManager({ kind: "void" })}>
            Void sale
          </button>
        </div>
      </section>
    </div>
  );
}

// ───────── Orders: parked sales + quotations ─────────
export function OrdersScreen({ pos }: { pos: PosStore }) {
  const [tab, setTab] = useState<"parked" | "quotes" | "pickup" | "resolve">("parked");
  const [parked, setParked] = useState<ParkedCart[]>([]);
  const [quotes, setQuotes] = useState<Quotation[]>([]);
  const [sending, setSending] = useState<{ id: string; channel: "email" | "sms" | "whatsapp"; contact: string } | null>(null);

  const load = useCallback(async () => {
    const [p, q] = await Promise.all([pos.gateway.listParked(), pos.gateway.listQuotations()]);
    if (p.ok) setParked(p.data);
    if (q.ok) setQuotes(q.data);
  }, [pos.gateway]);

  useEffect(() => {
    void load();
  }, [load, pos.notice, pos.cart?.id]);

  return (
    <section className={styles.panel}>
      <div className={styles.sectionHead}>
        <h2 className={styles.panelTitle}>Orders</h2>
        <Segment
          label="Orders view"
          value={tab}
          options={[
            { id: "parked", label: `Parked (${parked.length})` },
            { id: "quotes", label: `Quotations (${quotes.length})` },
            { id: "pickup", label: "Ready for pickup" },
            { id: "resolve", label: "Payments to resolve" },
          ]}
          onChange={setTab}
        />
      </div>
      <div className={styles.list}>
        {tab === "pickup" ? (
          <PickupList pos={pos} />
        ) : tab === "resolve" ? (
          <ResolveList pos={pos} />
        ) : tab === "parked" ? (
          parked.length === 0 ? (
            <div className={styles.emptyCard}>No parked sales.</div>
          ) : (
            parked.map((c) => (
              <div key={c.id} className={styles.listRow}>
                <span>
                  <div className={styles.listTitle}>{c.documentNumber ?? "Parked sale"}</div>
                  <div className={styles.muted}>
                    {c.lineCount} line{c.lineCount === 1 ? "" : "s"} · {formatMoney(c.total, c.currency)} · {when(c.updatedAt)}
                  </div>
                </span>
                <button type="button" className={styles.primaryButton} onClick={() => void pos.resume(c.id)}>
                  <Play size={16} aria-hidden /> Resume
                </button>
              </div>
            ))
          )
        ) : quotes.length === 0 ? (
          <div className={styles.emptyCard}>No quotations yet. Create one from Quick Sale.</div>
        ) : (
          quotes.map((q) => (
            <div key={q.id} className={styles.listRow}>
              <span>
                <div className={styles.listTitle}>
                  {q.documentNumber ?? "Quotation"} <span className={styles.badge}>{q.status}</span>
                </div>
                <div className={styles.muted}>
                  {q.lineCount} line{q.lineCount === 1 ? "" : "s"} · {formatMoney(q.total, q.currency)}
                  {q.validUntil ? ` · valid until ${q.validUntil}` : ""}
                  {q.sentChannel ? ` · sent by ${q.sentChannel}` : ""}
                </div>
                {sending?.id === q.id ? (
                  <form
                    className={styles.row}
                    style={{ marginTop: 8 }}
                    onSubmit={async (e) => {
                      e.preventDefault();
                      const res = await pos.gateway.sendQuotation(q.id, sending.channel, sending.contact.trim() || null);
                      if (res.ok) {
                        setSending(null);
                        void load();
                      }
                    }}
                  >
                    <Segment
                      label="Channel"
                      value={sending.channel}
                      options={[
                        { id: "whatsapp", label: "WhatsApp" },
                        { id: "sms", label: "SMS" },
                        { id: "email", label: "Email" },
                      ]}
                      onChange={(channel) => setSending({ ...sending, channel })}
                    />
                    <input className={styles.input} placeholder="Contact (blank = customer on file)" value={sending.contact} onChange={(e) => setSending({ ...sending, contact: e.target.value })} />
                    <button type="submit" className={styles.primaryButton}>
                      Send
                    </button>
                  </form>
                ) : null}
              </span>
              <div className={styles.row}>
                <button
                  type="button"
                  className={`${styles.softButton} ${styles.inlineButton}`}
                  disabled={q.status === "converted" || q.status === "cancelled" || q.status === "expired"}
                  onClick={() => setSending({ id: q.id, channel: "whatsapp", contact: "" })}
                >
                  <Send size={16} aria-hidden /> Send
                </button>
                <button
                  type="button"
                  className={styles.primaryButton}
                  disabled={q.status === "converted" || q.status === "cancelled" || q.status === "expired"}
                  onClick={() => void pos.convertQuote(q.id)}
                >
                  Convert to sale
                </button>
              </div>
            </div>
          ))
        )}
      </div>
    </section>
  );
}

// ───────── EPC Browse (Nissan: model → variant → section → diagram → parts) ─────────
export function EpcScreen({ pos }: { pos: PosStore }) {
  const [model, setModel] = useState(pos.modelSlug);
  const [variants, setVariants] = useState<VehicleVariant[]>([]);
  const [variant, setVariant] = useState<VehicleVariant | null>(null);
  const [sections, setSections] = useState<EpcSection[]>([]);
  const [section, setSection] = useState<EpcSection | null>(null);
  const [diagrams, setDiagrams] = useState<EpcDiagramRef[]>([]);
  const [diagram, setDiagram] = useState<EpcDiagram | null>(null);
  const [active, setActive] = useState<string | null>(null);
  const [natural, setNatural] = useState<{ w: number; h: number } | null>(null);
  const [imageFailed, setImageFailed] = useState(false);
  /** Why the live catalogue could not show this level (fail closed, never fixture data). */
  const [epcError, setEpcError] = useState<string | null>(null);
  const rowRefs = useRef<Record<string, HTMLDivElement | null>>({});
  const boxes = useMemo(() => {
    if (!diagram) return [];
    // Pixel callouts are in source-image pixels: the stored size wins, else the loaded image's.
    const w = diagram.width ?? natural?.w;
    const h = diagram.height ?? natural?.h;
    return diagram.hotspots.flatMap((spot, i) => {
      const box = epcBox(spot, w, h);
      return box ? [{ h: spot, box, n: i + 1 }] : [];
    });
  }, [diagram, natural]);
  const callouts = useMemo(() => {
    const m = new Map<string, number>();
    diagram?.hotspots.forEach((h, i) => {
      const k = h.oem.trim().toUpperCase();
      if (!m.has(k)) m.set(k, i + 1);
    });
    return m;
  }, [diagram]);
  const select = (oem: string, fromDiagram: boolean) => {
    const next = sameOem(active, oem) ? null : oem;
    setActive(next);
    if (next && fromDiagram) rowRefs.current[next.trim().toUpperCase()]?.scrollIntoView({ block: "nearest", behavior: "smooth" });
  };
  const modelName = pos.models.find((m) => m.slug === model)?.name ?? "";

  useEffect(() => {
    setVariant(null);
    setSections([]);
    if (!model) return setVariants([]);
    void pos.gateway.listEpcVariants(model).then((r) => r.ok && setVariants(r.data));
  }, [model, pos.gateway]);

  const openVariant = async (v: VehicleVariant) => {
    setVariant(v);
    setSection(null);
    setDiagram(null);
    const r = await pos.gateway.listEpcSections(model, v.slug);
    if (r.ok) setSections(r.data);
  };
  const openSection = async (s: EpcSection) => {
    if (!variant) return;
    setSection(s);
    setDiagram(null);
    setEpcError(null);
    const r = await pos.gateway.listEpcDiagrams(model, variant.slug, s.slug);
    if (!r.ok) return setEpcError(r.error);
    setDiagrams(r.data);
    if (r.data.length === 1) await openDiagram(r.data[0], s);
  };
  const openDiagram = async (d: EpcDiagramRef, s = section) => {
    if (!variant || !s) return;
    setEpcError(null);
    const r = await pos.gateway.getEpcDiagram(model, variant.slug, s.slug, d);
    if (!r.ok) return setEpcError(r.error);
    setNatural(null);
    setImageFailed(false);
    setActive(null);
    setDiagram(r.data);
  };
  const addOem = async (part: EpcDiagramPart) => {
    const res = await pos.gateway.searchParts(part.oemPartNumber, null);
    const live = res.ok ? res.data.find((p) => p.oemPartNumber.toUpperCase() === part.oemPartNumber.toUpperCase()) : null;
    if (live) await pos.addPart(live);
    else void pos.runSearch(part.oemPartNumber);
  };

  return (
    <section className={styles.panel}>
      <div className={styles.sectionHead}>
        <h2 className={styles.panelTitle}>EPC Browse</h2>
        <select className={styles.input} value={model} onChange={(e) => setModel(e.target.value)} aria-label="Model">
          <option value="">Choose model</option>
          {pos.models.map((m) => (
            <option key={m.slug} value={m.slug}>
              {m.name}
            </option>
          ))}
        </select>
      </div>
      <div className={styles.epcCrumbs}>
        <span>Nissan</span>
        {modelName ? <span>› {modelName}</span> : null}
        {variant ? <span>› {variant.chassisCode} {variant.engineCode}</span> : null}
        {section ? <span>› {section.name}</span> : null}
        {diagram ? <span>› {diagram.title}</span> : null}
        {variant ? (
          <button
            type="button"
            className={`${styles.textLink} ${styles.statusDismiss}`}
            onClick={() => {
              if (diagram) {
                setDiagram(null);
                setActive(null);
              }
              else if (section) setSection(null);
              else setVariant(null);
            }}
          >
            <ArrowLeft size={14} aria-hidden /> Back
          </button>
        ) : null}
      </div>

      {!model ? (
        <div className={styles.emptyCard} style={{ marginTop: 14 }}>Choose a Nissan model to browse its parts catalogue.</div>
      ) : !variant ? (
        <div className={styles.tileGrid}>
          {variants.map((v) => (
            <button key={v.slug} type="button" className={styles.category} onClick={() => void openVariant(v)}>
              <Car size={26} strokeWidth={1.5} aria-hidden />
              <strong>{v.chassisCode}</strong>
              <span className={styles.muted}>
                {v.engineCode} {v.yearLabel ? `· ${v.yearLabel}` : ""}
              </span>
            </button>
          ))}
        </div>
      ) : !section ? (
        <div className={styles.tileGrid}>
          {sections.map((s) => (
            <div key={s.slug} style={{ position: "relative" }}>
              <button type="button" className={styles.category} style={{ width: "100%" }} onClick={() => void openSection(s)}>
                {s.name}
              </button>
              <button
                type="button"
                className={`${styles.iconButton} ${styles.cardMenu}`}
                aria-label={`Pin ${s.name} to Popular`}
                onClick={() => void pos.pinCategory(s.name, s.name, "subcategory")}
              >
                <Pin size={14} aria-hidden />
              </button>
            </div>
          ))}
        </div>
      ) : !diagram ? (
        <div className={styles.list}>
          {epcError ? (
            <div className={styles.emptyCard} role="status">
              {epcError}
            </div>
          ) : diagrams.length === 0 ? (
            <div className={styles.emptyCard}>No diagrams in this section yet.</div>
          ) : null}
          {diagrams.map((d) => (
            <button key={d.slug} type="button" className={styles.listRow} style={{ border: 0, font: "inherit", textAlign: "left", cursor: "pointer" }} onClick={() => void openDiagram(d)}>
              <span className={styles.listTitle}>{d.title}</span>
            </button>
          ))}
        </div>
      ) : (
        <div className={styles.screenGrid} style={{ marginTop: 14 }}>
          <div className={styles.diagramWrap}>
            {diagram.imageUrl && !imageFailed ? (
              <>
                <div className={styles.diagramStage}>
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img
                    src={diagram.imageUrl}
                    alt={diagram.title}
                    onLoad={(e) => setNatural({ w: e.currentTarget.naturalWidth, h: e.currentTarget.naturalHeight })}
                    onError={() => setImageFailed(true)}
                  />
                  {boxes.map(({ h, box, n }) => (
                    <button
                      key={`${h.oem}-${n}`}
                      type="button"
                      aria-label={`Callout ${n}, part ${h.oem}`}
                      aria-pressed={sameOem(active, h.oem)}
                      className={`${styles.hotspot} ${sameOem(active, h.oem) ? styles.hotspotActive : ""}`}
                      style={{
                        left: `${box.left * 100}%`,
                        top: `${box.top * 100}%`,
                        width: `${box.width * 100}%`,
                        height: `${box.height * 100}%`,
                      }}
                      onClick={() => select(h.oem, true)}
                    >
                      <span className={styles.hotspotLabel}>{String(n).padStart(2, "0")}</span>
                    </button>
                  ))}
                </div>
                <p className={styles.diagramCaption}>
                  {boxes.length ? `${boxes.length} callout${boxes.length === 1 ? "" : "s"}. Tap one to find its part.` : "Match the reference numbers on the diagram to the Ref in the parts list."}
                </p>
              </>
            ) : (
              <div className={styles.diagramEmpty}>{diagram.notice ?? "Diagram image not available. The parts list still works."}</div>
            )}
          </div>
          <div className={styles.list} style={{ marginTop: 0 }}>
            {diagram.parts.length === 0 ? <div className={styles.emptyCard}>{diagram.notice ?? "No parts listed on this diagram."}</div> : null}
            {diagram.parts.map((p) => (
              <div
                key={p.oemPartNumber}
                ref={(el) => {
                  rowRefs.current[p.oemPartNumber.trim().toUpperCase()] = el;
                }}
                className={`${styles.listRow} ${styles.epcPartRow} ${sameOem(active, p.oemPartNumber) ? styles.listRowSelected : ""}`}
                onClick={() => select(p.oemPartNumber, false)}
                style={{ cursor: "pointer" }}
              >
                <span className={styles.calloutNo}>{callouts.get(p.oemPartNumber.trim().toUpperCase())?.toString().padStart(2, "0") ?? "–"}</span>
                <span style={{ minWidth: 0 }}>
                  <div className={styles.listTitle}>{p.name}</div>
                  <div className={styles.muted}>
                    {p.ref ? `Ref ${p.ref} · ` : ""}
                    {p.oemPartNumber}
                    {p.pnc ? ` · PNC ${p.pnc}` : ""}
                  </div>
                </span>
                <div className={styles.row}>
                  <button
                    type="button"
                    className={styles.iconButton}
                    aria-label={`Pin ${p.name} to Popular`}
                    onClick={(e) => {
                      e.stopPropagation();
                      void pos.pinPart({ stockItemId: null, oemPartNumber: p.oemPartNumber, name: p.name, price: null, saleableQty: null, imageUrl: null });
                    }}
                  >
                    <Pin size={14} aria-hidden />
                  </button>
                  <button
                    type="button"
                    className={styles.primaryButton}
                    onClick={(e) => {
                      e.stopPropagation();
                      setActive(p.oemPartNumber);
                      void addOem(p);
                    }}
                  >
                    Add
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </section>
  );
}
