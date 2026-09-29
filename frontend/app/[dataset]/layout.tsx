import type { ReactNode } from "react";
import { RequireAuth } from "@/features/auth/RequireAuth";
import { MetadataProvider } from "@/contexts/MetadataContext";
export default function DataLayout({ children }: { children: ReactNode }) {
  return <RequireAuth><MetadataProvider>{children}</MetadataProvider></RequireAuth>;
}
