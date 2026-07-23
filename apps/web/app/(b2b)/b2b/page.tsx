import styles from "../../(storefront)/page.module.css";
import local from "../b2b-page.module.css";

export const metadata = { title: "B2B" };

export default function B2bPage() {
  return (
    <div className={styles.page}>
      <h1 className={styles.title}>B2B supply</h1>
      <p className={styles.lede}>
        Trade accounts resolve the <strong>B2B</strong> price list and credit
        terms from Phase 5. Sign in with a linked customer profile to see net
        pricing.
      </p>
      <dl className={local.meta}>
        <div>
          <dt>Price list</dt>
          <dd>B2B (default for trade accounts)</dd>
        </div>
        <div>
          <dt>Currency</dt>
          <dd>
            <span className={local.usd}>USD</span> /{" "}
            <span className={local.zig}>ZiG</span>
          </dd>
        </div>
      </dl>
    </div>
  );
}
