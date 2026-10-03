import type { Metadata } from "next";
import type { CSSProperties, ReactNode } from "react";
import { AppShell } from "@/components/AppShell";
import colors from "@/utils/colors.json";
import "./globals.css";

export const metadata: Metadata = {
  title: "LaborLens | Ireland’s labour market",
  description: "Explore Ireland’s employment trends and labour market policies.",
};

/** Theme tokens come from the shared palette, including future chart colours. */
export default function RootLayout({ children }: { children: ReactNode }) {
  const theme = {
    "--primary": colors.theme.primary,
    "--dark": colors.theme.dark,
    "--background": colors.surface.background,
    "--card": colors.surface.card
  } as CSSProperties;

  return <html lang="en">
    <body style={theme}>
      <AppShell>{children}</AppShell>
    </body>
  </html>;
}
