"use client";

import { useEffect, useMemo } from "react";
import { StaffGate } from "@/components/staff-gate";
import { createPreviewPosGateway, isPosPreviewEnabled } from "@/lib/pos/preview-gateway";
import { OfflineCatalog } from "@/lib/pos/offline/catalog";
import { supabaseOfflineSources, withOfflineCatalog } from "@/lib/pos/offline/gateway";
import { OpfsBundleStorage, opfsSupported } from "@/lib/pos/offline/storage";
import { createSupabasePosGateway } from "@/lib/pos/supabase-gateway";
import { createWebClient } from "@/lib/supabase";
import { PosApp } from "./PosApp";

function LivePos() {
  const live = useMemo(() => {
    const client = createWebClient();
    if (!client) return null;
    // The downloadable full catalogue answers catalogue calls when the till has no connection.
    const offline = new OfflineCatalog(new OpfsBundleStorage(), supabaseOfflineSources(client));
    return { gateway: withOfflineCatalog(createSupabasePosGateway(client), offline), offline };
  }, []);
  useEffect(() => {
    if (live && opfsSupported()) void live.offline.init();
  }, [live]);
  if (!live) return null;
  return <PosApp gateway={live.gateway} offline={live.offline} />;
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
