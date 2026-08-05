"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type FormEvent,
  type KeyboardEvent,
} from "react";
import {
  fetchSearchSuggestions,
  partHref,
  type FacetChip,
  type SearchMode,
  type SearchSuggestion,
} from "@/lib/catalog-search";
import { createWebClient } from "@/lib/supabase";
import styles from "./search-four-way.module.css";

const modes = [
  { id: "part", label: "Part #" },
  { id: "vin", label: "VIN" },
  { id: "model", label: "Model" },
  { id: "pnc", label: "PNC" },
] as const satisfies ReadonlyArray<{ id: SearchMode; label: string }>;

const TYPEAHEAD_DEBOUNCE_MS = 300;

function facetLabel(chip: FacetChip): string {
  const prefix =
    chip.facet === "category_name"
      ? "Category"
      : chip.facet === "pnc_code"
        ? "PNC"
        : chip.facet === "chassis_code"
          ? "Chassis"
          : chip.facet === "model_variant"
            ? "Model"
            : chip.facet;
  return `${prefix}: ${chip.value}`;
}

export function SearchFourWay({
  variant = "panel",
  initialMode = "part",
  initialQuery = "",
}: {
  /** panel = page body; header = dense AutoDoc-style chrome search */
  variant?: "panel" | "header" | "compact";
  initialMode?: SearchMode;
  initialQuery?: string;
}) {
  const router = useRouter();
  const [mode, setMode] = useState<SearchMode>(initialMode);
  const [q, setQ] = useState(initialQuery);
  const [focused, setFocused] = useState(false);
  const [busy, setBusy] = useState(false);
  const [suggestions, setSuggestions] = useState<SearchSuggestion[]>([]);
  const [facetChips, setFacetChips] = useState<FacetChip[]>([]);
  const [backend, setBackend] = useState<"meili" | "fts" | null>(null);
  const [activeIdx, setActiveIdx] = useState(-1);
  const blurTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const isHeader = variant === "header";
  const idPrefix = isHeader ? "gtr-hdr" : "gtr-search";

  useEffect(() => {
    setMode(initialMode);
    setQ(initialQuery);
  }, [initialMode, initialQuery]);

  useEffect(() => {
    const trimmed = q.trim();
    if (trimmed.length < 2) {
      setSuggestions([]);
      setFacetChips([]);
      setBackend(null);
      setBusy(false);
      return;
    }

    let cancelled = false;
    setBusy(true);
    const timer = setTimeout(() => {
      void (async () => {
        const client = createWebClient();
        if (!client) {
          if (!cancelled) {
            setSuggestions([]);
            setFacetChips([]);
            setBackend(null);
            setBusy(false);
          }
          return;
        }

        const { data: sessionData } = await client.auth.getSession();
        if (!sessionData.session) {
          if (!cancelled) {
            setSuggestions([]);
            setFacetChips([]);
            setBackend(null);
            setBusy(false);
          }
          return;
        }

        const result = await fetchSearchSuggestions(client, trimmed);
        if (cancelled) return;
        setSuggestions(result.suggestions);
        setFacetChips(result.facetChips);
        setBackend(result.backend ?? null);
        setActiveIdx(-1);
        setBusy(false);
      })();
    }, TYPEAHEAD_DEBOUNCE_MS);

    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [q]);

  const showDropdown =
    focused &&
    q.trim().length >= 2 &&
    (busy || suggestions.length > 0 || facetChips.length > 0);

  const navigateSearch = useCallback(
    (nextMode: SearchMode, query: string) => {
      const trimmed = query.trim();
      if (!trimmed) return;
      router.push(`/search?mode=${nextMode}&q=${encodeURIComponent(trimmed)}`);
    },
    [router],
  );

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (activeIdx >= 0 && suggestions[activeIdx]) {
      applySuggestion(suggestions[activeIdx]);
      return;
    }
    navigateSearch(mode, q);
  }

  function applySuggestion(s: SearchSuggestion) {
    if (s.oem) {
      const href = partHref(s.oem);
      if (href) {
        router.push(href);
        setFocused(false);
        return;
      }
    }
    const filter = s.filterQuery ?? s.title;
    const nextMode =
      s.kind === "model" ? "model" : s.kind === "category" ? "pnc" : "part";
    setMode(nextMode);
    setQ(filter);
    navigateSearch(nextMode, filter);
    setFocused(false);
  }

  function applyFacet(chip: FacetChip) {
    const nextMode =
      chip.facet === "model_variant" || chip.facet === "chassis_code"
        ? "model"
        : chip.facet === "pnc_code"
          ? "pnc"
          : "part";
    setMode(nextMode);
    setQ(chip.value);
    navigateSearch(nextMode, chip.value);
    setFocused(false);
  }

  function onInputKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (!showDropdown || suggestions.length === 0) return;
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setActiveIdx((i) => Math.min(i + 1, suggestions.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setActiveIdx((i) => Math.max(i - 1, -1));
    } else if (e.key === "Escape") {
      setFocused(false);
      setActiveIdx(-1);
    }
  }

  function onFocus() {
    if (blurTimer.current) clearTimeout(blurTimer.current);
    setFocused(true);
  }

  function onBlur() {
    blurTimer.current = setTimeout(() => setFocused(false), 150);
  }

  return (
    <form
      className={
        isHeader
          ? styles.header
          : variant === "compact"
            ? styles.compact
            : styles.panel
      }
      onSubmit={onSubmit}
      aria-label="Parts search"
    >
      <div className={styles.modes} role="tablist" aria-label="Search by">
        {modes.map((m) => (
          <button
            key={m.id}
            type="button"
            role="tab"
            aria-selected={mode === m.id}
            className={mode === m.id ? styles.modeActive : styles.mode}
            onClick={() => setMode(m.id)}
          >
            {m.label}
          </button>
        ))}
      </div>
      <div className={styles.searchWrap}>
        <div className={styles.row}>
          <label className={styles.srOnly} htmlFor={`${idPrefix}-q`}>
            Search query
          </label>
          <input
            id={`${idPrefix}-q`}
            className={styles.input}
            value={q}
            onChange={(e) => setQ(e.target.value)}
            onFocus={onFocus}
            onBlur={onBlur}
            onKeyDown={onInputKeyDown}
            placeholder={
              mode === "vin"
                ? "17-character VIN"
                : mode === "model"
                  ? "e.g. Navara D40"
                  : mode === "pnc"
                    ? "PNC code"
                    : "OEM part number or keyword"
            }
            autoComplete="off"
            role="combobox"
            aria-expanded={showDropdown}
            aria-controls={`${idPrefix}-listbox`}
            aria-autocomplete="list"
          />
          <button type="submit" className={styles.submit}>
            Search
          </button>
        </div>

        {showDropdown ? (
          <div
            id={`${idPrefix}-listbox`}
            className={styles.dropdown}
            role="listbox"
            aria-label="Search suggestions"
          >
            {busy ? (
              <p className={styles.dropdownHint}>Searching catalog…</p>
            ) : null}
            {facetChips.length > 0 ? (
              <div className={styles.facetRow} aria-label="Facet filters">
                {facetChips.map((chip) => (
                  <button
                    key={`${chip.facet}:${chip.value}`}
                    type="button"
                    className={styles.facetChip}
                    onMouseDown={(e) => e.preventDefault()}
                    onClick={() => applyFacet(chip)}
                  >
                    {facetLabel(chip)}
                    <span className={styles.facetCount}>{chip.count}</span>
                  </button>
                ))}
              </div>
            ) : null}
            {suggestions.length > 0 ? (
              <ul className={styles.suggestList}>
                {suggestions.map((s, i) => (
                  <li key={`${s.kind}:${s.title}:${s.subtitle ?? ""}`}>
                    <button
                      type="button"
                      role="option"
                      aria-selected={i === activeIdx}
                      className={
                        i === activeIdx
                          ? styles.suggestActive
                          : styles.suggestItem
                      }
                      onMouseDown={(e) => e.preventDefault()}
                      onClick={() => applySuggestion(s)}
                    >
                      <span className={styles.suggestKind}>
                        {s.kind === "part"
                          ? "Part"
                          : s.kind === "model"
                            ? "Model"
                            : "Category"}
                      </span>
                      <span className={styles.suggestTitle}>{s.title}</span>
                      {s.subtitle ? (
                        <span className={styles.suggestSub}>{s.subtitle}</span>
                      ) : null}
                    </button>
                  </li>
                ))}
              </ul>
            ) : !busy ? (
              <p className={styles.dropdownHint}>
                No typeahead hits — press Search for full results.
              </p>
            ) : null}
          </div>
        ) : null}
      </div>
      {!isHeader ? (
        <p className={styles.hint}>
          Sign in for live catalog typeahead (Meili via Edge proxy, FTS fallback).
          OEM hits link through to{" "}
          <Link href="/search">advanced search</Link>.
          {backend ? (
            <>
              {" "}
              · Index: <strong>{backend}</strong>
            </>
          ) : null}
        </p>
      ) : null}
    </form>
  );
}
