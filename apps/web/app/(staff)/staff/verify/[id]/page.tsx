import { notFound } from "next/navigation";
import { StaffIdentityVerification } from "@/components/staff-identity-verification";
import { StaffNav } from "@/components/staff-nav";
import styles from "@/components/account.module.css";
export default async function StaffVerifyPage({params}: {params: Promise<{id: string}>}) {
  const {id} = await params;
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id)) notFound();
  return <div className={styles.shell}><StaffNav current="/staff/account" /><section className={styles.panel}>
    <header className={styles.pageHeader}><h1 className={styles.title}>Staff identity</h1></header>
    <StaffIdentityVerification employeeId={id} /></section></div>;
}
