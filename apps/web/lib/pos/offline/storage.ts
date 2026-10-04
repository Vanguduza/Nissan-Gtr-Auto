/**
 * Where the offline catalogue lives on this device. The browser implementation is the
 * Origin Private File System (private to this site, not visible in the user's files); tests use the
 * in-memory one.
 */
export interface BundleStorage {
  /** Streams [body] into [name], replacing it; [onBytes] reports bytes written so far. */
  write(name: string, body: ReadableStream<Uint8Array> | Uint8Array, onBytes?: (written: number) => void): Promise<void>;
  /** The whole file, or [length] bytes from [offset]; null when the file is absent. */
  read(name: string, offset?: number, length?: number): Promise<Uint8Array | null>;
  size(name: string): Promise<number | null>;
  remove(name: string): Promise<void>;
  /** Deletes every file of the bundle. */
  clear(): Promise<void>;
  /** The file as a stream (to copy without holding it in memory); null when absent. */
  stream(name: string): Promise<ReadableStream<Uint8Array> | null>;
  /** A folder inside this one (created on first write). */
  sub(name: string): BundleStorage;
  /** Deletes a folder and everything in it. */
  removeDir(name: string): Promise<void>;
  /** The folders directly inside this one. */
  dirs(): Promise<string[]>;
}

const DIR = "gtr-pos-offline-catalog";

export function opfsSupported(): boolean {
  return (
    typeof navigator !== "undefined" &&
    typeof navigator.storage?.getDirectory === "function" &&
    typeof FileSystemFileHandle !== "undefined" &&
    "createWritable" in FileSystemFileHandle.prototype
  );
}

export class OpfsBundleStorage implements BundleStorage {
  private dir: Promise<FileSystemDirectoryHandle> | null = null;

  /** [path]: folder names from the bundle root (empty for the root itself). */
  constructor(private readonly path: string[] = []) {}

  private root(): Promise<FileSystemDirectoryHandle> {
    this.dir ??= this.path.reduce<Promise<FileSystemDirectoryHandle>>(
      (parent, name) => parent.then((d) => d.getDirectoryHandle(name, { create: true })),
      navigator.storage.getDirectory().then((r) => r.getDirectoryHandle(DIR, { create: true })),
    );
    return this.dir;
  }

  sub(name: string): BundleStorage {
    return new OpfsBundleStorage([...this.path, name]);
  }

  async removeDir(name: string): Promise<void> {
    await (await this.root()).removeEntry(name, { recursive: true }).catch(() => undefined);
  }

  async dirs(): Promise<string[]> {
    const out: string[] = [];
    const root = (await this.root()) as FileSystemDirectoryHandle & { entries(): AsyncIterable<[string, FileSystemHandle]> };
    for await (const [name, handle] of root.entries()) if (handle.kind === "directory") out.push(name);
    return out;
  }

  async stream(name: string): Promise<ReadableStream<Uint8Array> | null> {
    const file = await (await this.handle(name))?.getFile();
    return file ? (file.stream() as ReadableStream<Uint8Array>) : null;
  }

  private async handle(name: string, create = false): Promise<FileSystemFileHandle | null> {
    try {
      return await (await this.root()).getFileHandle(name, { create });
    } catch {
      return null;
    }
  }

  async write(name: string, body: ReadableStream<Uint8Array> | Uint8Array, onBytes?: (written: number) => void): Promise<void> {
    const file = await this.handle(name, true);
    if (!file) throw new Error(`Cannot create ${name} in browser storage.`);
    const out = await file.createWritable();
    try {
      if (body instanceof Uint8Array) {
        await out.write(body as BufferSource);
        onBytes?.(body.length);
      } else {
        let written = 0;
        const reader = body.getReader();
        for (;;) {
          const { done, value } = await reader.read();
          if (done) break;
          await out.write(value as BufferSource);
          written += value.length;
          onBytes?.(written);
        }
      }
      await out.close();
    } catch (e) {
      await out.abort().catch(() => undefined);
      throw e;
    }
  }

  async read(name: string, offset?: number, length?: number): Promise<Uint8Array | null> {
    const file = await (await this.handle(name))?.getFile();
    if (!file) return null;
    const blob = offset == null ? file : file.slice(offset, offset + (length ?? file.size - offset));
    return new Uint8Array(await blob.arrayBuffer());
  }

  async size(name: string): Promise<number | null> {
    const file = await (await this.handle(name))?.getFile();
    return file ? file.size : null;
  }

  async remove(name: string): Promise<void> {
    await (await this.root()).removeEntry(name).catch(() => undefined);
  }

  async clear(): Promise<void> {
    if (this.path.length === 0) {
      const root = await navigator.storage.getDirectory();
      await root.removeEntry(DIR, { recursive: true }).catch(() => undefined);
    } else {
      const parent = await new OpfsBundleStorage(this.path.slice(0, -1)).root();
      await parent.removeEntry(this.path[this.path.length - 1], { recursive: true }).catch(() => undefined);
    }
    this.dir = null;
  }
}

export class MemoryBundleStorage implements BundleStorage {
  readonly files = new Map<string, Uint8Array>();
  readonly folders = new Map<string, MemoryBundleStorage>();

  sub(name: string): BundleStorage {
    let f = this.folders.get(name);
    if (!f) {
      f = new MemoryBundleStorage();
      this.folders.set(name, f);
    }
    return f;
  }

  async removeDir(name: string): Promise<void> {
    this.folders.delete(name);
  }

  async dirs(): Promise<string[]> {
    return [...this.folders.keys()];
  }

  async stream(name: string): Promise<ReadableStream<Uint8Array> | null> {
    const f = this.files.get(name);
    if (!f) return null;
    const copy = f.slice();
    return new ReadableStream<Uint8Array>({
      start(c) {
        c.enqueue(copy);
        c.close();
      },
    });
  }

  async write(name: string, body: ReadableStream<Uint8Array> | Uint8Array, onBytes?: (written: number) => void): Promise<void> {
    if (body instanceof Uint8Array) {
      this.files.set(name, body.slice());
      onBytes?.(body.length);
      return;
    }
    const chunks: Uint8Array[] = [];
    let written = 0;
    const reader = body.getReader();
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      chunks.push(value);
      written += value.length;
      onBytes?.(written);
    }
    const all = new Uint8Array(written);
    let at = 0;
    for (const c of chunks) {
      all.set(c, at);
      at += c.length;
    }
    this.files.set(name, all);
  }

  async read(name: string, offset?: number, length?: number): Promise<Uint8Array | null> {
    const f = this.files.get(name);
    if (!f) return null;
    return offset == null ? f.slice() : f.slice(offset, offset + (length ?? f.length - offset));
  }

  async size(name: string): Promise<number | null> {
    return this.files.get(name)?.length ?? null;
  }

  async remove(name: string): Promise<void> {
    this.files.delete(name);
  }

  async clear(): Promise<void> {
    this.files.clear();
    this.folders.clear();
  }
}
