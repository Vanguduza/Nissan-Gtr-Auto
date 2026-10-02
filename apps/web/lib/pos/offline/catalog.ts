import type { EpcDiagram, EpcDiagramRef, EpcSection, PosCurrency, PosPart, PosResult, SelectedVehicle, VehicleModel, VehicleVariant } from "../types";
import {
  OFFLINE_FORMAT,
  type BundleFile,
  type BundleManifest,
  type DiagramRoute,
  type Loc,
  type Row,
  type StockItem,
  type VehiclesFile,
  diagramFromParts,
  diagramsFrom,
  gunzip,
  gunzipJson,
  modelsFrom,
  normalizePart,
  packPath,
  parseRows,
  routesPath,
  sectionsFrom,
  sha256Hex,
  stockHits,
  variantsFrom,
  vehicleIdFor,
  vehicleStockHits,
} from "./format";
import type { BundleStorage } from "./storage";

export type StockSnapshot = { warehouseId: string; pulledAt: string; items: StockItem[] };

/** Network side of the bundle: the signed manifest and the shop's live stock and prices. */
export type OfflineSources = {
  fetchManifest(): Promise<BundleManifest>;
  fetchStock(): Promise<StockSnapshot>;
};

export type OfflineStatus =
  | { state: "none" }
  | { state: "downloading"; release: string; doneBytes: number; totalBytes: number }
  | {
      state: "ready";
      release: string;
      builtAt: string;
      downloadedAt: string;
      totalBytes: number;
      stockPulledAt: string | null;
      stockCount: number;
    }
  | { state: "paused"; release: string; doneBytes: number; totalBytes: number; message: string };

type Saved = {
  manifest: BundleManifest;
  done: string[];
  complete: boolean;
  downloadedAt: string | null;
};

const STATE = "state.json";
const STOCK = "stock.json";
const LIMIT = 100;

class Lru<K, V> {
  private map = new Map<K, V>();
  constructor(private readonly size: number) {}
  get(key: K): V | undefined {
    const v = this.map.get(key);
    if (v !== undefined) {
      this.map.delete(key);
      this.map.set(key, v);
    }
    return v;
  }
  set(key: K, value: V): void {
    this.map.delete(key);
    this.map.set(key, value);
    if (this.map.size > this.size) this.map.delete(this.map.keys().next().value as K);
  }
  clear(): void {
    this.map.clear();
  }
}

function asCurrency(value: unknown): PosCurrency {
  return value === "ZIG" ? "ZIG" : "USD";
}

function stripUrls(m: BundleManifest): BundleManifest {
  return { ...m, files: m.files.map(({ url: _url, ...f }) => f), expires_in: undefined };
}

/**
 * The complete catalogue on this device: downloads the published bundle file by file (each one
 * checked against its SHA-256 before it counts), keeps going after a pause, and answers the POS
 * catalogue questions — vehicle cascade, part search, EPC sections, diagrams, parts and images —
 * from it with no connection.
 */
export class OfflineCatalog {
  private saved: Saved | null = null;
  private stock: StockSnapshot | null = null;
  private stockByOem = new Map<string, StockItem>();
  private vehiclesFile: VehiclesFile | null = null;
  private rowsCache = new Lru<string, Row[]>(4);
  private routesCache = new Lru<string, Record<string, DiagramRoute>>(8);
  private progress: { doneBytes: number; totalBytes: number } | null = null;
  private pausedMessage: string | null = null;
  private listeners = new Set<() => void>();
  private abort: AbortController | null = null;
  private imageUrl: string | null = null;
  private initialised: Promise<void> | null = null;

  constructor(
    private readonly storage: BundleStorage,
    private readonly sources: OfflineSources | null,
  ) {}

  /** Loads what is already on the device. Safe to call more than once. */
  init(): Promise<void> {
    this.initialised ??= (async () => {
      const raw = await this.storage.read(STATE).catch(() => null);
      if (raw) {
        try {
          this.saved = JSON.parse(new TextDecoder().decode(raw)) as Saved;
        } catch {
          this.saved = null;
        }
      }
      const stock = await this.storage.read(STOCK).catch(() => null);
      if (stock) this.setStock(JSON.parse(new TextDecoder().decode(stock)) as StockSnapshot);
      if (this.saved && !this.saved.complete) {
        this.pausedMessage = "Download paused. Continue to finish it.";
      }
      this.emit();
    })();
    return this.initialised;
  }

  get ready(): boolean {
    return this.saved?.complete === true;
  }

  get downloading(): boolean {
    return this.abort != null;
  }

  status(): OfflineStatus {
    const s = this.saved;
    if (this.abort && this.progress) {
      return { state: "downloading", release: s?.manifest.release ?? "", ...this.progress };
    }
    if (!s) return { state: "none" };
    if (s.complete) {
      return {
        state: "ready",
        release: s.manifest.release,
        builtAt: s.manifest.built_at,
        downloadedAt: s.downloadedAt ?? "",
        totalBytes: s.manifest.total_bytes,
        stockPulledAt: this.stock?.pulledAt ?? null,
        stockCount: this.stock?.items.length ?? 0,
      };
    }
    return {
      state: "paused",
      release: s.manifest.release,
      doneBytes: this.doneBytes(s),
      totalBytes: s.manifest.total_bytes,
      message: this.pausedMessage ?? "Download paused.",
    };
  }

  subscribe(listener: () => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private emit(): void {
    for (const l of this.listeners) l();
  }

  private doneBytes(s: Saved): number {
    const done = new Set(s.done);
    return s.manifest.files.filter((f) => done.has(f.path)).reduce((n, f) => n + f.bytes, 0);
  }

  private async save(): Promise<void> {
    if (!this.saved) return;
    await this.storage.write(STATE, new TextEncoder().encode(JSON.stringify(this.saved)));
  }

  private setStock(snapshot: StockSnapshot): void {
    this.stock = snapshot;
    this.stockByOem = new Map(snapshot.items.map((i) => [normalizePart(i.oem_part_number), i]));
  }

  /** Downloads (or finishes) the published bundle, then the shop's stock and prices. */
  async download(): Promise<void> {
    if (!this.sources) throw new Error("The offline catalogue cannot be downloaded in preview mode.");
    if (this.abort) return;
    await this.init();
    const abort = new AbortController();
    this.abort = abort;
    this.pausedMessage = null;
    try {
      let manifest = await this.sources.fetchManifest();
      if (manifest.format !== OFFLINE_FORMAT) throw new Error("This offline catalogue needs a newer version of the POS.");
      if (!this.saved || this.saved.manifest.build !== manifest.build) {
        // A new build replaces the old one completely; the old copy stays usable until then only
        // if it was complete, so it is cleared here, before the first new file is written.
        await this.storage.clear();
        this.resetCaches();
        this.saved = { manifest: stripUrls(manifest), done: [], complete: false, downloadedAt: null };
        this.stock = null;
        this.stockByOem.clear();
        await this.save();
      }
      const saved = this.saved as Saved;
      if (!saved.complete) {
        if (typeof navigator !== "undefined" && navigator.storage?.persist) await navigator.storage.persist().catch(() => false);
        const done = new Set(saved.done);
        let doneBytes = this.doneBytes(saved);
        this.progress = { doneBytes, totalBytes: saved.manifest.total_bytes };
        this.emit();
        for (const file of saved.manifest.files) {
          if (done.has(file.path)) continue;
          const signed = manifest.files.find((f) => f.path === file.path);
          let url = signed?.url;
          let resp = url ? await fetch(url, { signal: abort.signal }) : null;
          if (!resp || resp.status === 403 || resp.status === 400) {
            // Signed links last a few hours; a long download asks for fresh ones.
            manifest = await this.sources.fetchManifest();
            url = manifest.files.find((f) => f.path === file.path)?.url;
            resp = url ? await fetch(url, { signal: abort.signal }) : null;
          }
          if (!resp || !resp.ok || !resp.body) throw new Error(`Download failed (${resp?.status ?? "no link"}) at ${file.path}.`);
          await this.storage.write(file.path, resp.body, (n) => {
            this.progress = { doneBytes: doneBytes + n, totalBytes: saved.manifest.total_bytes };
            this.emit();
          });
          await this.verify(file);
          done.add(file.path);
          saved.done.push(file.path);
          doneBytes += file.bytes;
          await this.save();
        }
      }
      await this.refreshStockInner();
      saved.complete = true;
      saved.downloadedAt ??= new Date().toISOString();
      await this.save();
    } catch (e) {
      this.pausedMessage = abort.signal.aborted ? "Download paused." : e instanceof Error ? e.message : "Download failed.";
      throw e;
    } finally {
      this.abort = null;
      this.progress = null;
      this.emit();
    }
  }

  private async verify(file: BundleFile): Promise<void> {
    const bytes = await this.storage.read(file.path);
    if (!bytes || bytes.length !== file.bytes || (await sha256Hex(bytes)) !== file.sha256) {
      await this.storage.remove(file.path);
      throw new Error(`${file.path} arrived damaged. Continue the download to fetch it again.`);
    }
  }

  pause(): void {
    this.abort?.abort();
  }

  /** Re-pulls the shop's stock and prices (what offline search can sell). */
  async refreshStock(): Promise<void> {
    await this.refreshStockInner();
    this.emit();
  }

  private async refreshStockInner(): Promise<void> {
    if (!this.sources) return;
    const snapshot = await this.sources.fetchStock();
    await this.storage.write(STOCK, new TextEncoder().encode(JSON.stringify(snapshot)));
    this.setStock(snapshot);
  }

  async remove(): Promise<void> {
    this.pause();
    await this.storage.clear();
    this.saved = null;
    this.stock = null;
    this.stockByOem.clear();
    this.pausedMessage = null;
    this.resetCaches();
    this.emit();
  }

  private resetCaches(): void {
    this.vehiclesFile = null;
    this.rowsCache.clear();
    this.routesCache.clear();
  }

  // -------------------------------------------------------------------------------------------
  // Reading
  // -------------------------------------------------------------------------------------------

  private async readLoc(loc: Loc): Promise<Uint8Array> {
    const bytes = await this.storage.read(packPath(loc[0]), loc[1], loc[2]);
    if (!bytes || bytes.length !== loc[2]) throw new Error("The offline catalogue is incomplete. Download it again in Settings.");
    return bytes;
  }

  private async rowsAt(pages: Loc[]): Promise<Row[]> {
    const out: Row[] = [];
    for (const loc of pages) out.push(...parseRows(new TextDecoder().decode(await gunzip(await this.readLoc(loc)))));
    return out;
  }

  private async vehicles(): Promise<VehiclesFile> {
    if (this.vehiclesFile) return this.vehiclesFile;
    const raw = await this.storage.read("vehicles.json.gz");
    if (!raw) throw new Error("The offline catalogue is incomplete. Download it again in Settings.");
    this.vehiclesFile = await gunzipJson<VehiclesFile>(raw);
    return this.vehiclesFile;
  }

  private async vehicleRows(vehicleId: string): Promise<Row[]> {
    const cached = this.rowsCache.get(vehicleId);
    if (cached) return cached;
    const pages = (await this.vehicles()).shards[vehicleId];
    if (!pages) throw new Error("This vehicle is not in the offline catalogue.");
    const rows = await this.rowsAt(pages);
    this.rowsCache.set(vehicleId, rows);
    return rows;
  }

  private async route(diagramId: string): Promise<DiagramRoute | null> {
    const buckets = this.saved?.manifest.route_buckets ?? 64;
    const path = routesPath(diagramId, buckets);
    let table = this.routesCache.get(path);
    if (!table) {
      const raw = await this.storage.read(path);
      if (!raw) return null;
      table = await gunzipJson<Record<string, DiagramRoute>>(raw);
      this.routesCache.set(path, table);
    }
    return table[diagramId] ?? null;
  }

  private async attempt<T>(fallback: string, run: () => Promise<T>): Promise<PosResult<T>> {
    try {
      return { ok: true, data: await run() };
    } catch (e) {
      return { ok: false, error: e instanceof Error && e.message ? e.message : fallback };
    }
  }

  listModels(): Promise<PosResult<VehicleModel[]>> {
    return this.attempt("Could not read vehicles from the offline catalogue.", async () => modelsFrom((await this.vehicles()).vehicles));
  }

  listVariants(modelSlug: string): Promise<PosResult<VehicleVariant[]>> {
    return this.attempt("Could not read vehicles from the offline catalogue.", async () => variantsFrom((await this.vehicles()).vehicles, modelSlug));
  }

  listSections(variantSlug: string): Promise<PosResult<EpcSection[]>> {
    return this.attempt("Could not load sections offline.", async () => sectionsFrom(await this.vehicleRows(variantSlug)));
  }

  listDiagrams(variantSlug: string, sectionSlug: string): Promise<PosResult<EpcDiagramRef[]>> {
    return this.attempt("Could not load diagrams offline.", async () =>
      diagramsFrom(await this.vehicleRows(variantSlug), sectionSlug).map((d) => ({ id: d.id, slug: d.id, title: d.title, imageUrl: null })),
    );
  }

  getDiagram(ref: EpcDiagramRef): Promise<PosResult<EpcDiagram>> {
    return this.attempt("Could not load the diagram offline.", async () => {
      if (!ref.id) throw new Error("This diagram is not in the catalogue.");
      const route = await this.route(ref.id);
      if (!route) throw new Error("This diagram is not in the offline catalogue.");
      let parts = route.p ? await this.rowsAt(route.p) : [];
      if (route.f) parts = parts.filter((p) => String(p.diagram_id ?? "") === ref.id);
      let imageUrl: string | null = null;
      if (route.i) {
        if (this.imageUrl) URL.revokeObjectURL(this.imageUrl);
        this.imageUrl = URL.createObjectURL(new Blob([(await this.readLoc(route.i)) as BlobPart], { type: "image/png" }));
        imageUrl = this.imageUrl;
      }
      return diagramFromParts(
        ref.title,
        imageUrl,
        parts as Parameters<typeof diagramFromParts>[2],
        route.i ? null : "No diagram image in the catalogue for this diagram.",
      );
    });
  }

  searchParts(query: string, vehicle: SelectedVehicle | null): Promise<PosResult<PosPart[]>> {
    return this.attempt("Offline search failed.", async () => {
      const q = query.trim();
      const toPart = (s: StockItem, row?: Row): PosPart => ({
        stockItemId: s.stock_item_id,
        oemPartNumber: s.oem_part_number,
        name: s.description ?? String(row?.name ?? s.oem_part_number),
        price: { amount: Number(s.unit_price), currency: asCurrency(s.currency) },
        saleableQty: Number(s.saleable_qty),
        imageUrl: null,
        categoryName: row ? String(row.subcategory_name ?? row.category_name ?? "") || null : null,
      });
      if (vehicle) {
        const id = vehicleIdFor((await this.vehicles()).vehicles, vehicle.modelSlug, vehicle.chassisCode, vehicle.engineCode);
        if (!id) return [];
        return vehicleStockHits(await this.vehicleRows(id), q, this.stockByOem, LIMIT).map((h) => toPart(h.stock, h.row));
      }
      if (q.length < 2) return [];
      return stockHits(this.stock?.items ?? [], q, LIMIT).map((s) => toPart(s));
    });
  }
}
