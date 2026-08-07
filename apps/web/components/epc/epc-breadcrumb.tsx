import Link from "next/link";
import {
  catalogPath,
  type CatalogBrowseContext,
} from "@/lib/catalog-hierarchy";
import styles from "./epc-breadcrumb.module.css";

type Crumb = { href?: string; label: string };

export function EpcBreadcrumb({
  ctx,
  labels,
}: {
  ctx: CatalogBrowseContext;
  /** Optional display labels keyed by level (defaults to slug). */
  labels?: Partial<
    Record<"maker" | "model" | "variant" | "section", string>
  >;
}) {
  const crumbs: Crumb[] = [
    { href: "/catalog", label: "Catalog" },
    {
      href: ctx.model ? catalogPath({ maker: ctx.maker }) : undefined,
      label: labels?.maker ?? ctx.maker,
    },
  ];
  if (ctx.model) {
    crumbs.push({
      href: ctx.variant
        ? catalogPath({ maker: ctx.maker, model: ctx.model })
        : undefined,
      label: labels?.model ?? ctx.model,
    });
  }
  if (ctx.model && ctx.variant) {
    crumbs.push({
      href: ctx.section
        ? catalogPath({
            maker: ctx.maker,
            model: ctx.model,
            variant: ctx.variant,
          })
        : undefined,
      label: labels?.variant ?? ctx.variant,
    });
  }
  if (ctx.model && ctx.variant && ctx.section) {
    crumbs.push({
      label: labels?.section ?? ctx.section,
    });
  }

  return (
    <nav className={styles.nav} aria-label="EPC breadcrumb">
      {crumbs.map((c, i) => (
        <span key={`${c.label}-${i}`}>
          {i > 0 ? <span className={styles.sep}> › </span> : null}
          {c.href ? (
            <Link href={c.href} className={styles.link}>
              {c.label}
            </Link>
          ) : (
            <span className={styles.current}>{c.label}</span>
          )}
        </span>
      ))}
    </nav>
  );
}
