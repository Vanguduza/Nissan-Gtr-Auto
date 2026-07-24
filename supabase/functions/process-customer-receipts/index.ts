/**
 * Customer receipt PDF stub + channel send drain.
 * Tax-agnostic; no ZIMRA/FDMS/fiscal QR. PDF links use nissangtrauto.co.zw.
 * Storage bucket: customer-receipts (private).
 *
 * AuthZ: requires header x-worker-secret matching env WORKER_SHARED_SECRET
 * (refuse 401 if missing/wrong). Local stub only: WORKER_ALLOW_UNVERIFIED_LOCAL=1
 * when WORKER_SHARED_SECRET is unset. Never commit real secrets.
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
import { assertWorkerSecret } from "../_shared/worker_auth.ts";

Deno.serve(async (req) => {
  try {
    const denied = assertWorkerSecret(req);
    if (denied) return denied;

    const body = req.method === "POST" ? await req.json().catch(() => ({})) : {};
    const documentId = body.document_id as string | undefined;
    const limit = Number(body.limit ?? 50);

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    if (documentId) {
      const path = `customer-receipts/${documentId}.pdf`;
      // Stub: real PDF render would upload bytes to Storage here (no fiscal payload).
      const { data: art, error: artErr } = await supabase.rpc("mark_receipt_pdf_ready", {
        p_document_id: documentId,
        p_storage_path: path,
        p_download_token: null,
        p_expires_at: new Date(Date.now() + 7 * 864e5).toISOString(),
        p_byte_size: null,
        p_content_sha256: null,
      });
      if (artErr) {
        return new Response(JSON.stringify({ error: artErr.message }), {
          status: 400,
          headers: { "Content-Type": "application/json" },
        });
      }

      const { data: sent, error: sendErr } = await supabase.rpc(
        "process_receipt_outbox_batch",
        { p_limit: limit, p_stub_success: true },
      );
      if (sendErr) {
        return new Response(JSON.stringify({ error: sendErr.message }), {
          status: 400,
          headers: { "Content-Type": "application/json" },
        });
      }

      return new Response(
        JSON.stringify({
          artifact_id: art,
          channels_sent: sent,
          stub: true,
          fiscal: false,
        }),
        { headers: { "Content-Type": "application/json" } },
      );
    }

    const { data, error } = await supabase.rpc("process_receipt_outbox_batch", {
      p_limit: limit,
      p_stub_success: body.stub_success !== false,
    });

    if (error) {
      return new Response(JSON.stringify({ error: error.message }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }

    return new Response(
      JSON.stringify({ channels_processed: data, stub: true }),
      { headers: { "Content-Type": "application/json" } },
    );
  } catch (e) {
    return new Response(JSON.stringify({ error: String(e) }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});
