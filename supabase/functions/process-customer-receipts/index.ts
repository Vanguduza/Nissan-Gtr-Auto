/**
 * Customer receipt PDF generate + channel send drain.
 * Tax-agnostic; no ZIMRA/FDMS/fiscal QR. PDF links use nissangtrauto.co.zw.
 * Storage bucket: customer-receipts (private).
 *
 * AuthZ: x-worker-secret ↔ WORKER_SHARED_SECRET (or local unverified stub).
 * Channel secrets fail closed unless WORKER_ALLOW_UNVERIFIED_LOCAL=1 with
 * WORKER_SHARED_SECRET unset.
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2";
import { assertWorkerSecret } from "../_shared/worker_auth.ts";
import {
  allowLocalChannelStub,
  jsonErr,
  jsonOk,
} from "../_shared/channel_env.ts";
import { buildReceiptPdf } from "../_shared/receipt_pdf.ts";
import { getSmsGatewayConfig, sendSms } from "../_shared/sms_gateway.ts";
import {
  bytesToBase64,
  getEmailSendConfig,
  sendEmail,
} from "../_shared/email_send.ts";
import {
  getWhatsAppCloudConfig,
  sendWhatsAppDocument,
  sendWhatsAppText,
} from "../_shared/whatsapp_cloud.ts";

const BUCKET = "customer-receipts";

type OutboxRow = {
  id: string;
  document_id: string;
  channel: "sms" | "email" | "whatsapp";
  recipient: string;
  summary_body: string;
  download_url: string | null;
  pdf_storage_path: string | null;
};

function serviceClient(): SupabaseClient {
  return createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );
}

async function loadReceiptData(supabase: SupabaseClient, documentId: string) {
  const { data: inv, error: invErr } = await supabase
    .from("sales_invoices")
    .select(
      "id, doc_type, status, document_number, currency, exchange_rate_applied, subtotal, total, posted_at, customer_phone_e164, customer_email, warehouse_id, warehouses(code, name)",
    )
    .eq("id", documentId)
    .maybeSingle();
  if (invErr) throw new Error(invErr.message);
  if (!inv || inv.status !== "posted") {
    throw new Error("posted invoice/credit note required");
  }

  const { data: lines, error: lineErr } = await supabase
    .from("sales_invoice_lines")
    .select(
      "qty, unit_price, line_total, is_core_charge, stock_items(oem_part_number, description)",
    )
    .eq("invoice_id", documentId)
    .order("created_at", { ascending: true });
  if (lineErr) throw new Error(lineErr.message);

  const { data: allocations } = await supabase
    .from("payment_allocations")
    .select(
      "amount, payment_entries!inner(tender, currency, amount, status)",
    )
    .eq("sales_invoice_id", documentId);

  const tenders: { tender: string; amount: number; currency: string }[] = [];
  for (const a of allocations ?? []) {
    const pe = a.payment_entries as unknown as {
      tender: string;
      currency: string;
      status: string;
    } | null;
    if (!pe || pe.status !== "posted") continue;
    tenders.push({
      tender: pe.tender,
      amount: Number(a.amount),
      currency: pe.currency,
    });
  }

  const wh = inv.warehouses as unknown as { code: string; name: string } | null;
  const pdfLines = (lines ?? []).map((l) => {
    const item = l.stock_items as unknown as {
      oem_part_number: string;
      description: string | null;
    } | null;
    return {
      description:
        item?.description?.trim() ||
        item?.oem_part_number ||
        "Part",
      qty: Number(l.qty),
      unitPrice: Number(l.unit_price),
      lineTotal: Number(l.line_total),
      isCoreCharge: Boolean(l.is_core_charge),
    };
  });

  return {
    inv,
    wh,
    pdfLines,
    tenders,
  };
}

async function generateAndStorePdf(
  supabase: SupabaseClient,
  documentId: string,
): Promise<{ artifactId: string; storagePath: string; downloadUrl: string | null }> {
  const { inv, wh, pdfLines, tenders } = await loadReceiptData(
    supabase,
    documentId,
  );
  const isCredit = inv.doc_type === "credit_note";
  const documentLabel = inv.document_number || inv.id;
  const { bytes, sha256Hex, byteSize } = await buildReceiptPdf({
    storeName: wh?.name || "Nissan GTR Auto",
    storeCode: wh?.code ?? null,
    documentLabel,
    docKind: isCredit ? "Credit" : "Sale",
    currency: inv.currency,
    exchangeRate: Number(inv.exchange_rate_applied),
    postedAt: inv.posted_at,
    subtotal: Number(inv.subtotal),
    total: Number(inv.total),
    lines: pdfLines,
    tenders,
    customerContact: inv.customer_email || inv.customer_phone_e164 || null,
  });

  const storagePath = `${documentId}.pdf`;
  const { error: upErr } = await supabase.storage
    .from(BUCKET)
    .upload(storagePath, bytes, {
      contentType: "application/pdf",
      upsert: true,
    });
  if (upErr) throw new Error(`storage upload: ${upErr.message}`);

  const expiresAt = new Date(Date.now() + 7 * 864e5).toISOString();
  const { data: art, error: artErr } = await supabase.rpc(
    "mark_receipt_pdf_ready",
    {
      p_document_id: documentId,
      p_storage_path: storagePath,
      p_download_token: null,
      p_expires_at: expiresAt,
      p_byte_size: byteSize,
      p_content_sha256: sha256Hex,
    },
  );
  if (artErr) throw new Error(artErr.message);

  const { data: artifact } = await supabase
    .from("receipt_pdf_artifacts")
    .select("download_url")
    .eq("id", art)
    .maybeSingle();

  return {
    artifactId: art as string,
    storagePath,
    downloadUrl: artifact?.download_url ?? null,
  };
}

async function fetchPdfBytes(
  supabase: SupabaseClient,
  storagePath: string | null,
): Promise<Uint8Array | null> {
  if (!storagePath) return null;
  const { data, error } = await supabase.storage
    .from(BUCKET)
    .download(storagePath);
  if (error || !data) return null;
  return new Uint8Array(await data.arrayBuffer());
}

async function sendOutboxChannel(
  supabase: SupabaseClient,
  row: OutboxRow,
  localStub: boolean,
): Promise<{ ok: boolean; error?: string; stub?: boolean }> {
  const body = row.summary_body;
  const to = row.recipient;

  try {
    if (row.channel === "sms") {
      const cfg = getSmsGatewayConfig();
      if (!cfg) {
        if (!localStub) {
          return {
            ok: false,
            error: "SMS_GATEWAY_API_KEY required for receipt SMS",
          };
        }
        return { ok: true, stub: true };
      }
      await sendSms(cfg, to, body);
      return { ok: true };
    }

    if (row.channel === "email") {
      const cfg = getEmailSendConfig();
      if (!cfg) {
        if (!localStub) {
          return {
            ok: false,
            error: "EMAIL_API_KEY + EMAIL_FROM required for receipt email",
          };
        }
        return { ok: true, stub: true };
      }
      const pdfBytes = await fetchPdfBytes(supabase, row.pdf_storage_path);
      const attachments = pdfBytes
        ? [
          {
            filename: "receipt.pdf",
            contentBase64: bytesToBase64(pdfBytes),
            contentType: "application/pdf",
          },
        ]
        : undefined;
      const linkLine = row.download_url
        ? `\n\nDownload: ${row.download_url}`
        : "";
      await sendEmail(cfg, {
        to,
        subject: "Your Nissan GTR Auto receipt",
        text: `${body}${linkLine}`,
        attachments,
      });
      return { ok: true };
    }

    if (row.channel === "whatsapp") {
      const cfg = getWhatsAppCloudConfig();
      if (!cfg) {
        if (!localStub) {
          return {
            ok: false,
            error:
              "WHATSAPP_ACCESS_TOKEN + WHATSAPP_PHONE_NUMBER_ID required for receipt WhatsApp",
          };
        }
        return { ok: true, stub: true };
      }
      // Prefer document link (company-domain URL); Meta fetches HTTPS link.
      if (row.download_url) {
        await sendWhatsAppDocument(cfg, to, {
          link: row.download_url,
          filename: "receipt.pdf",
          caption: body.slice(0, 1024),
        });
      } else {
        await sendWhatsAppText(cfg, to, body);
      }
      return { ok: true };
    }

    return { ok: false, error: `unknown channel ${row.channel}` };
  } catch (e) {
    return { ok: false, error: String(e) };
  }
}

async function drainChannels(
  supabase: SupabaseClient,
  limit: number,
  localStub: boolean,
): Promise<{
  processed: number;
  sent: number;
  failed: number;
  stubbed: number;
}> {
  const { data: rows, error } = await supabase.rpc(
    "claim_receipt_outbox_batch",
    { p_limit: limit },
  );
  if (error) throw new Error(error.message);

  let sent = 0;
  let failed = 0;
  let stubbed = 0;
  const list = (rows ?? []) as OutboxRow[];

  for (const row of list) {
    const result = await sendOutboxChannel(supabase, row, localStub);
    await supabase.rpc("complete_receipt_outbox", {
      p_id: row.id,
      p_success: result.ok,
      p_error: result.ok ? null : result.error ?? "send failed",
    });
    if (result.ok) {
      sent += 1;
      if (result.stub) stubbed += 1;
    } else {
      failed += 1;
    }
  }

  return { processed: list.length, sent, failed, stubbed };
}

Deno.serve(async (req) => {
  try {
    const denied = assertWorkerSecret(req);
    if (denied) return denied;

    const body = req.method === "POST" ? await req.json().catch(() => ({})) : {};
    const documentId = body.document_id as string | undefined;
    const limit = Number(body.limit ?? 50);
    const localStub = allowLocalChannelStub();
    const supabase = serviceClient();

    // Fail closed when no channel secrets and not local stub — refuse blind stub success.
    const hasAnyChannelSecret =
      !!getSmsGatewayConfig() ||
      !!getEmailSendConfig() ||
      !!getWhatsAppCloudConfig();
    if (!hasAnyChannelSecret && !localStub && !documentId) {
      // Allow PDF-only generation path below when document_id set; for pure drain, refuse.
      return jsonErr(
        "SMS/EMAIL/WhatsApp secrets unset — refuse (set WORKER_ALLOW_UNVERIFIED_LOCAL=1 with WORKER_SHARED_SECRET unset for local stub only)",
        503,
      );
    }

    const pdfResults: {
      document_id: string;
      artifact_id: string;
      storage_path: string;
    }[] = [];

    if (documentId) {
      const art = await generateAndStorePdf(supabase, documentId);
      pdfResults.push({
        document_id: documentId,
        artifact_id: art.artifactId,
        storage_path: art.storagePath,
      });
    } else {
      const { data: needing, error: needErr } = await supabase.rpc(
        "list_receipt_documents_needing_pdf",
        { p_limit: Math.min(limit, 20) },
      );
      if (needErr) return jsonErr(needErr.message, 400);
      for (const row of needing ?? []) {
        const id = (row as { document_id: string }).document_id;
        try {
          const art = await generateAndStorePdf(supabase, id);
          pdfResults.push({
            document_id: id,
            artifact_id: art.artifactId,
            storage_path: art.storagePath,
          });
        } catch (e) {
          console.error(`receipt PDF failed for ${id}:`, e);
        }
      }
    }

    // Channel drain: real send when secrets present; local stub only when allowed.
    if (!hasAnyChannelSecret && !localStub && documentId) {
      // PDF may have been generated; channels cannot send.
      return jsonOk({
        pdfs: pdfResults,
        channels: null,
        stub: false,
        fiscal: false,
        warning:
          "PDF ready; channel secrets missing — set SMS/EMAIL/WhatsApp env or local stub flag to drain outbox",
      });
    }

    if (!hasAnyChannelSecret && localStub) {
      // Preserve smoke-compatible stub drain for localhost.
      const { data, error } = await supabase.rpc(
        "process_receipt_outbox_batch",
        { p_limit: limit, p_stub_success: true },
      );
      if (error) return jsonErr(error.message, 400);
      return jsonOk({
        pdfs: pdfResults,
        channels_processed: data,
        stub: true,
        fiscal: false,
      });
    }

    const channels = await drainChannels(supabase, limit, localStub);
    return jsonOk({
      pdfs: pdfResults,
      channels,
      stub: channels.stubbed > 0 && channels.stubbed === channels.sent,
      fiscal: false,
    });
  } catch (e) {
    return jsonErr(String(e), 500);
  }
});
