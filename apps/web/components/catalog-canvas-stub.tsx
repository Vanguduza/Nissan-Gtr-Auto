import styles from "./catalog-canvas-stub.module.css";

/** Bounding-box catalog canvas — diagram data from Phase 7 pipeline. */
export function CatalogCanvasStub() {
  return (
    <div className={styles.wrap} role="img" aria-label="Parts diagram canvas placeholder">
      <div className={styles.frame}>
        <span className={styles.callout} style={{ left: "22%", top: "28%" }}>
          01
        </span>
        <span className={styles.callout} style={{ left: "58%", top: "42%" }}>
          02
        </span>
        <span className={styles.callout} style={{ left: "40%", top: "68%" }}>
          03
        </span>
        <p className={styles.caption}>
          Visual catalog canvas — FAST diagram + hotspots arrive with the data
          pipeline.
        </p>
      </div>
    </div>
  );
}
