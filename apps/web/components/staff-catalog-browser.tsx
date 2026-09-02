"use client";

import { useEffect, useMemo, useState } from "react";
import { createWebClient } from "@/lib/supabase";
import {
  catalogGatewayGet,
  type DiagramImageResponse,
  type StaffPartsResponse,
} from "@/lib/catalog-live-gateway";
import styles from "./staff-catalog-browser.module.css";

type Family = {
  family_id: string;
  family_slug: string;
  display_name: string;
  generation_label: string | null;
  body_type: string | null;
  year_start: number | null;
  year_end: number | null;
};
type Variant = {
  variant_id: string;
  variant_slug: string;
  display_name: string;
  body_style: string | null;
  drive_type: string | null;
  transmission_code: string | null;
  market: string | null;
};
type Section = {
  section_id: string;
  section_slug: string;
  display_name: string;
  sort_order: number;
  diagram_count: number;
};
type Diagram = {
  diagram_id: string;
  title: string | null;
  name_en: string | null;
  diagram_kind: string | null;
  applicability: string | null;
  expected_part_count: number | null;
  expected_hotspot_count: number | null;
  image_ready: boolean;
  image_sha256: string | null;
};

type LoadState = "idle" | "loading" | "ready" | "error";

export function StaffCatalogBrowser() {
  const client = useMemo(() => createWebClient(), []);
  const db = client as any;
  const [families, setFamilies] = useState<Family[]>([]);
  const [variants, setVariants] = useState<Variant[]>([]);
  const [sections, setSections] = useState<Section[]>([]);
  const [diagrams, setDiagrams] = useState<Diagram[]>([]);
  const [familyId, setFamilyId] = useState("");
  const [variantId, setVariantId] = useState("");
  const [sectionId, setSectionId] = useState("");
  const [selectedDiagram, setSelectedDiagram] = useState<Diagram | null>(null);
  const [imageUrl, setImageUrl] = useState<string | null>(null);
  const [parts, setParts] = useState<StaffPartsResponse["parts"]>([]);
  const [state, setState] = useState<LoadState>("loading");
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!client) {
      setState("error");
      setMessage("Supabase is not configured.");
      return;
    }
    let cancelled = false;
    void (async () => {
      setState("loading");
      const { data, error } = await db.rpc("staff_catalog_v2_list_families", {
        p_maker_slug: "nissan",
      });
      if (cancelled) return;
      if (error) {
        setState("error");
        setMessage(error.message);
        return;
      }
      setFamilies((data ?? []) as Family[]);
      setState("ready");
    })();
    return () => { cancelled = true; };
  }, [client, db]);

  async function chooseFamily(next: string) {
    setFamilyId(next);
    setVariantId("");
    setSectionId("");
    setVariants([]);
    setSections([]);
    setDiagrams([]);
    setSelectedDiagram(null);
    setImageUrl(null);
    setParts([]);
    setMessage(null);
    if (!next || !client) return;
    setState("loading");
    const { data, error } = await db.rpc("staff_catalog_v2_list_variants", { p_family_id: next });
    if (error) {
      setState("error");
      setMessage(error.message);
      return;
    }
    setVariants((data ?? []) as Variant[]);
    setState("ready");
  }

  async function chooseVariant(next: string) {
    setVariantId(next);
    setSectionId("");
    setSections([]);
    setDiagrams([]);
    setSelectedDiagram(null);
    setImageUrl(null);
    setParts([]);
    setMessage(null);
    if (!next || !client) return;
    setState("loading");
    const { data, error } = await db.rpc("staff_catalog_v2_list_sections", { p_variant_id: next });
    if (error) {
      setState("error");
      setMessage(error.message);
      return;
    }
    setSections((data ?? []) as Section[]);
    setState("ready");
  }

  async function chooseSection(next: string) {
    setSectionId(next);
    setDiagrams([]);
    setSelectedDiagram(null);
    setImageUrl(null);
    setParts([]);
    setMessage(null);
    if (!next || !client) return;
    const family = families.find((x) => x.family_id === familyId);
    const variant = variants.find((x) => x.variant_id === variantId);
    const section = sections.find((x) => x.section_id === next);
    if (!family || !variant || !section) return;
    setState("loading");
    const { data, error } = await db.rpc("staff_catalog_v2_list_diagrams", {
      p_family_slug: family.family_slug,
      p_variant_slug: variant.variant_slug,
      p_section_slug: section.section_slug,
      p_limit: 500,
      p_offset: 0,
    });
    if (error) {
      setState("error");
      setMessage(error.message);
      return;
    }
    setDiagrams((data ?? []) as Diagram[]);
    setState("ready");
  }

  async function openDiagram(diagram: Diagram) {
    if (!client) return;
    setSelectedDiagram(diagram);
    setImageUrl(null);
    setParts([]);
    setMessage(null);
    setState("loading");
    try {
      const [image, partData] = await Promise.all([
        catalogGatewayGet<DiagramImageResponse>(client, "diagram-image", {
          maker: "nissan",
          diagram_id: diagram.diagram_id,
        }),
        catalogGatewayGet<StaffPartsResponse>(client, "staff-diagram-parts", {
          maker: "nissan",
          diagram_id: diagram.diagram_id,
        }),
      ]);
      setImageUrl(image.signed_url);
      setParts(partData.parts ?? []);
      setState("ready");
    } catch (error) {
      setState("error");
      setMessage(error instanceof Error ? error.message : String(error));
    }
  }

  return (
    <div className={styles.shell}>
      <div className={styles.intro}>
        <div>
          <p className={styles.eyebrow}>STAFF · LIVE EPC</p>
          <h1>Nissan Catalog</h1>
          <p>
            Browse the hosted catalog live. Hierarchy and authorization come from Supabase;
            part shards and diagrams are fetched from Cloudflare R2 only when selected.
          </p>
        </div>
        <div className={styles.liveBadge}>NO CATALOG DOWNLOAD</div>
      </div>

      <div className={styles.cascade}>
        <label>
          <span>Model family</span>
          <select value={familyId} onChange={(e) => void chooseFamily(e.target.value)}>
            <option value="">Choose family</option>
            {families.map((row) => (
              <option key={row.family_id} value={row.family_id}>
                {row.display_name}{row.generation_label ? ` · ${row.generation_label}` : ""}
              </option>
            ))}
          </select>
        </label>
        <label>
          <span>Variant</span>
          <select value={variantId} disabled={!familyId} onChange={(e) => void chooseVariant(e.target.value)}>
            <option value="">Choose variant</option>
            {variants.map((row) => (
              <option key={row.variant_id} value={row.variant_id}>{row.display_name}</option>
            ))}
          </select>
        </label>
        <label>
          <span>Section</span>
          <select value={sectionId} disabled={!variantId} onChange={(e) => void chooseSection(e.target.value)}>
            <option value="">Choose section</option>
            {sections.map((row) => (
              <option key={row.section_id} value={row.section_id}>
                {row.display_name} ({row.diagram_count})
              </option>
            ))}
          </select>
        </label>
      </div>

      {message ? <div className={styles.message}>{message}</div> : null}
      {state === "loading" ? <div className={styles.loading}>Loading live catalog…</div> : null}

      {diagrams.length ? (
        <div className={styles.diagramGrid}>
          {diagrams.map((diagram) => (
            <button
              key={diagram.diagram_id}
              type="button"
              className={`${styles.diagramCard} ${selectedDiagram?.diagram_id === diagram.diagram_id ? styles.active : ""}`}
              onClick={() => void openDiagram(diagram)}
            >
              <strong>{diagram.name_en || diagram.title || "Catalog diagram"}</strong>
              <span>{diagram.diagram_kind || "diagram"}</span>
              <span>{diagram.expected_part_count ?? 0} expected parts</span>
            </button>
          ))}
        </div>
      ) : null}

      {selectedDiagram ? (
        <div className={styles.workspace}>
          <section className={styles.diagramPane}>
            <div className={styles.paneHead}>
              <div>
                <p className={styles.eyebrow}>DIAGRAM</p>
                <h2>{selectedDiagram.name_en || selectedDiagram.title || selectedDiagram.diagram_id}</h2>
              </div>
              <span>R2 signed image</span>
            </div>
            {imageUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={imageUrl} alt={selectedDiagram.name_en || selectedDiagram.title || "EPC diagram"} className={styles.diagramImage} />
            ) : (
              <div className={styles.imagePlaceholder}>Diagram image is not yet mapped into the current R2 serving manifest.</div>
            )}
          </section>

          <section className={styles.partsPane}>
            <div className={styles.paneHead}>
              <div>
                <p className={styles.eyebrow}>PART RECORDS</p>
                <h2>{parts.length} live records</h2>
              </div>
              <span>R2 serving shard</span>
            </div>
            <div className={styles.tableWrap}>
              <table>
                <thead>
                  <tr>
                    <th>PNC</th>
                    <th>Part</th>
                    <th>Description</th>
                    <th>Fitment</th>
                  </tr>
                </thead>
                <tbody>
                  {parts.map((part, index) => (
                    <tr key={`${part.normalized_oem_number ?? part.display_oem_number ?? "part"}-${index}`}>
                      <td>{part.pnc_code || "—"}</td>
                      <td>{part.display_oem_number || part.normalized_oem_number || "—"}</td>
                      <td>{part.name || part.description || part.subcategory_name || part.category_name || "Nissan catalog part"}</td>
                      <td>{[part.chassis_code, part.engine_code].filter(Boolean).join(" · ") || "Catalog applicability"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </div>
      ) : null}
    </div>
  );
}
