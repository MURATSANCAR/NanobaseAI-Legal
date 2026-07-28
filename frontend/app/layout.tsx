import type { Metadata } from "next";
import { DM_Sans, JetBrains_Mono, Sora } from "next/font/google";
import { headers } from "next/headers";
import "./globals.css";

const dmSans = DM_Sans({
  variable: "--font-dm-sans",
  subsets: ["latin"],
});

const sora = Sora({
  variable: "--font-sora",
  subsets: ["latin"],
});

const jetbrainsMono = JetBrains_Mono({
  variable: "--font-jetbrains",
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
    title: "NanobaseAI Şartname AI",
    description:
      "Pilot feedback, kök neden, deney, release gate ve insan go-live kararını kanıt zinciriyle yönetin.",
    icons: {
      icon: "/favicon.svg",
      shortcut: "/favicon.svg",
    },
    openGraph: {
      title: "NanobaseAI Şartname AI",
      description: "Pilot → Evidence → Release: fail-closed kalite ve go-live yönetişimi.",
      type: "website",
      locale: "tr_TR",
      images: [{ url: image, width: 1536, height: 1024, alt: "NanobaseAI Şartname AI" }],
    },
    twitter: {
      card: "summary_large_image",
      title: "NanobaseAI Şartname AI",
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
        className={`${dmSans.variable} ${sora.variable} ${jetbrainsMono.variable} font-sans antialiased`}
      >
        {children}
      </body>
    </html>
  );
}
