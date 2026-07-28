import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import { headers } from "next/headers";
import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export async function generateMetadata(): Promise<Metadata> {
  const requestHeaders = await headers();
  const host = requestHeaders.get("x-forwarded-host") ?? requestHeaders.get("host");
  const protocol = requestHeaders.get("x-forwarded-proto") ?? "https";
  const origin = host ? `${protocol}://${host}` : "http://localhost:3000";
  const image = new URL("/og-sprint9.png", origin).toString();
  return {
    metadataBase: new URL(origin),
    title: "NANObaseAI | Technical Specification Intelligence v1.0 RC",
    description:
      "Pilot feedback, kök neden, deney, release gate ve insan go-live kararını kanıt zinciriyle yönetin.",
    icons: {
      icon: "/favicon.svg",
      shortcut: "/favicon.svg",
    },
    openGraph: {
      title: "NANObaseAI | Technical Specification Intelligence v1.0 RC",
      description: "Pilot → Evidence → Release: fail-closed kalite ve go-live yönetişimi.",
      type: "website",
      locale: "tr_TR",
      images: [{ url: image, width: 1536, height: 1024, alt: "NANObaseAI Şartname AI" }],
    },
    twitter: {
      card: "summary_large_image",
      title: "NANObaseAI | Technical Specification Intelligence v1.0 RC",
      description: "Pilot → Evidence → Release: fail-closed kalite ve go-live yönetişimi.",
      images: [image],
    },
  };
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="tr">
      <body
        className={`${geistSans.variable} ${geistMono.variable} antialiased`}
      >
        {children}
      </body>
    </html>
  );
}
