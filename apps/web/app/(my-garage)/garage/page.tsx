import styles from "../../(storefront)/page.module.css";
import local from "../garage-page.module.css";

export const metadata = { title: "My Garage" };

export default function GaragePage() {
  return (
    <div className={`${styles.page} ${local.panel}`}>
      <h1 className={styles.title}>My Garage</h1>
      <p className={styles.lede}>
        Save vehicles to filter catalog and search by fitment. Add a VIN or
        model when signed in — garage rows sync via Supabase profiles.
      </p>
      <ul className={local.list}>
        <li className={local.empty}>No vehicles yet — add one after sign-in.</li>
      </ul>
    </div>
  );
}
