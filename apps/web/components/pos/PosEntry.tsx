"use client";

import { useMemo } from "react";
import { StaffGate } from "@/components/staff-gate";
import { createPreviewPosGateway, isPosPreviewEnabled } from "@/lib/pos/preview-gateway";
import { createSupabasePosGateway } from "@/lib/pos/supabase-gateway";
import { createWebClient } from "@/lib/supabase";
import { PosApp } from "./PosApp";

function LivePos() {
  const gateway = useMemo(() => {
    const client = createWebClient();
    return client ? createSupabasePosGateway(client) : null;
  }, []);
  if (!gateway) return null;
  return <PosApp gateway={gateway} />;
}

/**
 * Live POS sits behind the same staff gate as every staff surface (roles + module access), rendered
 * full-screen without the staff header. Development preview skips the gate and uses preview data.
 */
export function PosEntry() {
  const previewGateway = useMemo(() => (isPosPreviewEnabled() ? createPreviewPosGateway() : null), []);
  if (previewGateway) return <PosApp gateway={previewGateway} />;
  return (
    <StaffGate bare>
      <LivePos />
    </StaffGate>
  );
}
