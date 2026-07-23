import type { CSSProperties, ReactNode } from "react";
import type { Metadata } from "next";
import { Barlow_Condensed, Source_Sans_3 } from "next/font/google";
import "./globals.css";

const display = Barlow_Condensed({
  subsets: ["latin"],
  weight: ["500", "600", "700"],
  variable: "--font-display-loaded",
});

const body = Source_Sans_3({
  subsets: ["latin"],
  weight: ["400", "600"],
  variable: "--font-body-loaded",
});

const siteUrl =
  process.env.NEXT_PUBLIC_SITE_URL ?? "https://nissangtrauto.co.zw";

export const metadata: Metadata = {
  metadataBase: new URL(siteUrl),
  title: {
    default: "Nissan GTR Auto",
    template: "%s · Nissan GTR Auto",
  },
  description:
    "Genuine Nissan spare parts for Zimbabwe — fitment search, My Garage, and B2B supply.",
  icons: {
    icon: "/brand/logo.png",
    apple: "/brand/logo.png",
  },
  openGraph: {
    title: "Nissan GTR Auto",
    description: "Spare parts distribution · nissangtrauto.co.zw",
    url: siteUrl,
    siteName: "Nissan GTR Auto",
    images: [{ url: "/brand/logo.png", alt: "Nissan GTR Auto" }],
  },
};

export default function RootLayout({
  children,
}: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="en" className={`${display.variable} ${body.variable}`}>
      <body
        style={
          {
            "--font-display": "var(--font-display-loaded), var(--font-display)",
            "--font-body": "var(--font-body-loaded), var(--font-body)",
          } as CSSProperties
        }
      >
        {children}
      </body>
    </html>
  );
}
