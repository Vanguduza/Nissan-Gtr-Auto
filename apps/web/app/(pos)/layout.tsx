import type { ReactNode } from "react";
import { Inter } from "next/font/google";
import "@gtr/ui/tokens.css";

/** POS product UI face (blueprint §4.6). Display face is inherited from the root layout. */
const inter = Inter({ subsets: ["latin"], variable: "--pos-font", display: "swap" });

export default function PosLayout({ children }: { children: ReactNode }) {
  return (
    <div className={inter.variable} style={{ ["--pos-display-font" as string]: "var(--font-display-loaded)" }}>
      {children}
    </div>
  );
}
