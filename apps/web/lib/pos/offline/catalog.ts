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

/** Releases on this device: the one in use, and the one kept to go back to (blueprint §10.9). */
type Releases = { activeRelease: string | null; previousRelease: string | null };

export type OfflineStatus =
  | ({ state: "none"; message?: string } & Releases)
  | ({ state: "downloading"; release: string; doneBytes: number; totalBytes: number } & Releases)
  | ({
      state: "ready";
      release: string;
      builtAt: string;
      downloadedAt: string;
      totalBytes: number;
      stockPulledAt: string | null;
      stockCount: number;
      /** E.g. the active release was damaged and the previous one is in use again. */
      notice?: string;
    } & Releases)
  | ({ state: "paused"; release: string; doneBytes: number; totalBytes: number; message: string } & Releases);

type Saved = {
  manifest: BundleManifest;
  done: string[];
  complete: boolean;
  downloadedAt: string | null;
};

/** A build and the folder holding it ("." is a build downloaded before staging existed, kept in the root). */
type Build = { dir: string; saved: Saved };

const POINTER = "catalog.json";
const LEGACY = ".";
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
 * The complete catalogue on this device, answering the POS catalogue questions — vehicle cascade,
 * part search, EPC sections, diagrams, parts and images — with no connection.
 *
 * Updates follow blueprint §10.9: a new build downloads into its own folder while the current one
 * stays in use; each file is checked against its SHA-256 (files unchanged since the active build are
 * copied locally instead of downloaded); only a fully verified build is activated, by rewriting the
 * pointer file `catalog.json` (OPFS replaces a file atomically when its writer closes); the build it
 * replaces is kept so staff can go back to it. At most two builds are stored: starting a new update
 * drops the older previous one.
 */
export class OfflineCatalog {
  private active: Build | null = null;
  private previous: Build | null = null;
  private staging: Build | null = null;
  private stock: StockSnapshot | null = null;
  private stockByOem = new Map<string, StockItem>();
  private vehiclesFile: VehiclesFile | null = null;
  private rowsCache = new Lru<string, Row[]>(4);
  private routesCache = new Lru<string, Record<string, DiagramRoute>>(8);
  private progress: { doneBytes: number; totalBytes: number } | null = null;
  private pausedMessage: string | null = null;
  private notice: string | null = null;
  private listeners = new Set<() => void>();
  private abort: AbortController | null = null;
  private imageUrl: string | null = null;
  private initialised: Promise<void> | null = null;

  constructor(
    private readonly storage: BundleStorage,
    private readonly sources: OfflineSources | null,
  ) {}

  private store(dir: string): BundleStorage {
    return dir === LEGACY ? this.storage : this.storage.sub(dir);
  }

  private static dirFor(build: string): string {
    return `build-${build.replace(/[^A-Za-z0-9._-]/g, "_")}`;
  }

  private async readJson<T>(store: BundleStorage, name: string): Promise<T | null> {
    const raw = await store.read(name).catch(() => null);
    if (!raw) return null;
    try {
      return JSON.parse(new TextDecoder().decode(raw)) as T;
    } catch {
      return null;
    }
  }

  private async loadBuild(dir: string | null | undefined): Promise<Build | null> {
    if (!dir) return null;
    const saved = await this.readJson<Saved>(this.store(dir), STATE);
    return saved ? { dir, saved } : null;
  }

  /** Cheap startup check: every file present with its recorded size (checksums were verified on download). */
  private async intact(b: Build): Promise<boolean> {
    const store = this.store(b.dir);
    for (const f of b.saved.manifest.files) if ((await store.size(f.path)) !== f.bytes) return false;
    return true;
  }

  private async deleteBuild(b: Build): Promise<void> {
    if (b.dir === LEGACY) {
      for (const f of b.saved.manifest.files) await this.storage.remove(f.path);
      await this.storage.remove(STATE);
    } else {
      await this.storage.removeDir(b.dir);
    }
  }

  private async writePointer(): Promise<void> {
    const body = { active: this.active?.dir ?? null, previous: this.previous?.dir ?? null };
    await this.storage.write(POINTER, new TextEncoder().encode(JSON.stringify(body)));
  }

  /** Loads what is on the device and checks the active build; a damaged one falls back to the previous. */
  init(): Promise<void> {
    this.initialised ??= (async () => {
      let pointer = await this.readJson<{ active: string | null; previous: string | null }>(this.storage, POINTER);
      if (!pointer) {
        // Downloaded before staging existed: the build sits in the root folder.
        const legacy = await this.loadBuild(LEGACY);
        pointer = { active: legacy?.saved.complete ? LEGACY : null, previous: null };
        if (legacy && !legacy.saved.complete) await this.deleteBuild(legacy);
      }
      let active = await this.loadBuild(pointer.active);
      let previous = await this.loadBuild(pointer.previous);
      if (active && !active.saved.complete) active = null;
      if (previous && !previous.saved.complete) previous = null;
      if (active && !(await this.intact(active))) {
        const fallback = previous && (await this.intact(previous)) ? previous : null;
        this.notice = fallback
          ? `Offline catalogue release ${active.saved.manifest.release} was damaged on this device; release ${fallback.saved.manifest.release} is in use again.`
          : `Offline catalogue release ${active.saved.manifest.release} was damaged on this device. Download it again.`;
        await this.deleteBuild(active);
        if (previous && !fallback) await this.deleteBuild(previous);
        active = fallback;
        previous = null;
      } else if (previous && !(await this.intact(previous))) {
        await this.deleteBuild(previous);
        previous = null;
      }
      this.active = active;
      this.previous = previous;
      await this.writePointer();
      for (const dir of await this.storage.dirs()) {
        if (dir === active?.dir || dir === previous?.dir) continue;
        const b = await this.loadBuild(dir);
        if (b && !b.saved.complete && !this.staging) this.staging = b;
        else await this.storage.removeDir(dir);
      }
      const stock = await this.readJson<StockSnapshot>(this.storage, STOCK);
      if (stock) this.setStock(stock);
      if (this.staging) this.pausedMessage = "Download paused. Continue to finish it.";
      this.emit();
    })();
    return this.initialised;
  }

  get ready(): boolean {
    return this.active?.saved.complete === true;
  }

  get downloading(): boolean {
    return this.abort != null;
  }

  status(): OfflineStatus {
    const releases: Releases = {
      activeRelease: this.active?.saved.manifest.release ?? null,
      previousRelease: this.previous?.saved.manifest.release ?? null,
    };
    const st = this.staging?.saved;
    if (this.abort && this.progress) {
      return { state: "downloading", release: st?.manifest.release ?? "", ...this.progress, ...releases };
    }
    if (st) {
      return {
        state: "paused",
        release: st.manifest.release,
        doneBytes: this.doneBytes(st),
        totalBytes: st.manifest.total_bytes,
        message: this.pausedMessage ?? "Download paused.",
        ...releases,
      };
    }
    const a = this.active?.saved;
    if (!a) return { state: "none", ...(this.pausedMessage ? { message: this.pausedMessage } : {}), ...releases };
    return {
      state: "ready",
      release: a.manifest.release,
      builtAt: a.manifest.built_at,
      downloadedAt: a.downloadedAt ?? "",
      totalBytes: a.manifest.total_bytes,
      stockPulledAt: this.stock?.pulledAt ?? null,
      stockCount: this.stock?.items.length ?? 0,
      ...(this.notice ? { notice: this.notice } : {}),
      ...releases,
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

  private async save(b: Build): Promise<void> {
    await this.store(b.dir).write(STATE, new TextEncoder().encode(JSON.stringify(b.saved)));
  }

  private setStock(snapshot: StockSnapshot): void {
    this.stock = snapshot;
    this.stockByOem = new Map(snapshot.items.map((i) => [normalizePart(i.oem_part_number), i]));
  }

  /** The staging build for [manifest]: resumed when it is the one already staged, else started fresh. */
  private async stage(manifest: BundleManifest): Promise<Build> {
    if (this.staging?.saved.manifest.build === manifest.build) return this.staging;
    // One update at a time, and at most two builds stored: drop any other staging and the old previous.
    if (this.staging) await this.deleteBuild(this.staging);
    this.staging = null;
    if (this.previous) {
      await this.deleteBuild(this.previous);
      this.previous = null;
      await this.writePointer();
    }
    const dir = OfflineCatalog.dirFor(manifest.build);
    await this.storage.removeDir(dir);
    const b: Build = { dir, saved: { manifest: stripUrls(manifest), done: [], complete: false, downloadedAt: null } };
    await this.save(b);
    this.staging = b;
    return b;
  }

  /** A file the active build already has with the same checksum is copied on the device, not downloaded. */
  private async copyFromActive(file: BundleFile, target: BundleStorage, onBytes: (n: number) => void): Promise<boolean> {
    const a = this.active;
    if (!a) return false;
    const same = a.saved.manifest.files.find((f) => f.path === file.path && f.sha256 === file.sha256 && f.bytes === file.bytes);
    if (!same) return false;
    const body = await this.store(a.dir).stream(file.path);
    if (!body) return false;
    await target.write(file.path, body, onBytes);
    if ((await target.size(file.path)) !== file.bytes) {
      await target.remove(file.path);
      return false;
    }
    return true;
  }

  /**
   * Downloads (or finishes) the published bundle into staging, then the shop's stock and prices, and
   * activates the new build once every file is verified. The current build stays in use meanwhile.
   */
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
      if (this.active?.saved.manifest.build === manifest.build) {
        await this.refreshStockInner();
        return;
      }
      const b = await this.stage(manifest);
      const saved = b.saved;
      const target = this.store(b.dir);
      if (typeof navigator !== "undefined" && navigator.storage?.persist) await navigator.storage.persist().catch(() => false);
      const done = new Set(saved.done);
      let doneBytes = this.doneBytes(saved);
      this.progress = { doneBytes, totalBytes: saved.manifest.total_bytes };
      this.emit();
      const onBytes = (n: number) => {
        this.progress = { doneBytes: doneBytes + n, totalBytes: saved.manifest.total_bytes };
        this.emit();
      };
      for (const file of saved.manifest.files) {
        if (done.has(file.path)) continue;
        if (abort.signal.aborted) throw new DOMException("Download paused.", "AbortError");
        if (!(await this.copyFromActive(file, target, onBytes))) {
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
          await target.write(file.path, resp.body, onBytes);
          await this.verify(file, target);
        }
        done.add(file.path);
        saved.done.push(file.path);
        doneBytes += file.bytes;
        await this.save(b);
      }
      await this.refreshStockInner();
      await this.activate(b);
    } catch (e) {
      this.pausedMessage = abort.signal.aborted ? "Download paused." : e instanceof Error ? e.message : "Download failed.";
      throw e;
    } finally {
      this.abort = null;
      this.progress = null;
      this.emit();
    }
  }

  /** Every file present with its verified size → the pointer is rewritten; the old build becomes previous. */
  private async activate(b: Build): Promise<void> {
    const store = this.store(b.dir);
    const done = new Set(b.saved.done);
    for (const f of b.saved.manifest.files) {
      if (!done.has(f.path) || (await store.size(f.path)) !== f.bytes) {
        throw new Error(`${f.path} is missing from the new catalogue. Continue the download.`);
      }
    }
    b.saved.complete = true;
    b.saved.downloadedAt = new Date().toISOString();
    await this.save(b);
    this.previous = this.active;
    this.active = b;
    this.staging = null;
    this.notice = null;
    await this.writePointer();
    this.resetCaches();
  }

  /** Goes back to the build that was in use before the last update; the current one becomes previous. */
  async rollback(): Promise<void> {
    await this.init();
    if (this.abort) throw new Error("Pause the download first.");
    const p = this.previous;
    if (!p) throw new Error("There is no previous offline catalogue on this device.");
    if (!(await this.intact(p))) {
      await this.deleteBuild(p);
      this.previous = null;
      await this.writePointer();
      this.emit();
      throw new Error("The previous offline catalogue was damaged and has been removed.");
    }
    this.previous = this.active;
    this.active = p;
    this.notice = null;
    await this.writePointer();
    this.resetCaches();
    this.emit();
  }

  private async verify(file: BundleFile, store: BundleStorage): Promise<void> {
    const bytes = await store.read(file.path);
    if (!bytes || bytes.length !== file.bytes || (await sha256Hex(bytes)) !== file.sha256) {
      await store.remove(file.path);
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
    this.active = null;
    this.previous = null;
    this.staging = null;
    this.stock = null;
    this.stockByOem.clear();
    this.pausedMessage = null;
    this.notice = null;
    this.resetCaches();
    this.emit();
  }

  private resetCaches(): void {
    this.vehiclesFile = null;
    this.rowsCache.clear();
    this.routesCache.clear();
  }

  /** The build in use; reads never touch staging. */
  private activeStore(): BundleStorage {
    if (!this.active) throw new Error("The offline catalogue is not downloaded on this device.");
    return this.store(this.active.dir);
  }

  // -------------------------------------------------------------------------------------------
  // Reading
  // -------------------------------------------------------------------------------------------

  private async readLoc(loc: Loc): Promise<Uint8Array> {
    const bytes = await this.activeStore().read(packPath(loc[0]), loc[1], loc[2]);
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
    const raw = await this.activeStore().read("vehicles.json.gz");
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
    const buckets = this.active?.saved.manifest.route_buckets ?? 64;
    const path = routesPath(diagramId, buckets);
    let table = this.routesCache.get(path);
    if (!table) {
      const raw = await this.activeStore().read(path);
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
