import type { ReactNode } from "react";
import type { Metadata } from "next";
import { Source_Sans_3, Titillium_Web } from "next/font/google";
import { publicSiteUrl } from "@/lib/site-url";
import "./globals.css";

/**
 * next/font emits hashed family names on these CSS variables.
 * globals.css must use var(--font-display-loaded) / var(--font-body-loaded)
 * — never a bare "Titillium Web" string (that face is never registered).
 */
const display = Titillium_Web({
  subsets: ["latin"],
  weight: ["400", "600", "700"],
  variable: "--font-display-loaded",
  display: "swap",
});

const body = Source_Sans_3({
  subsets: ["latin"],
  weight: ["400", "600", "700"],
  variable: "--font-body-loaded",
  display: "swap",
});

const siteUrl = publicSiteUrl();

export const metadata: Metadata = {
  metadataBase: new URL(siteUrl),
  title: {
    default: "Nissan GTR Auto",
    template: "%s · Nissan GTR Auto",
  },
  description:
    "Nissan spare parts for Zimbabwe — fitment search, My Account, My Garage, and B2B.",
  icons: {
    icon: "/brand/logo.png",
    apple: "/brand/logo.png",
  },
  openGraph: {
    title: "Nissan GTR Auto",
    description: "Spare parts · nissangtrauto.co.zw",
    url: siteUrl,
    siteName: "Nissan GTR Auto",
    images: [{ url: "/brand/logo.png", alt: "Nissan GTR Auto" }],
  },
};

export default function RootLayout({
  children,
}: Readonly<{ children: ReactNode }>) {
  return (
    <html
      lang="en"
      className={`${display.variable} ${body.variable}`}
    >
      <body className={body.className}>{children}</body>
    </html>
  );
}
