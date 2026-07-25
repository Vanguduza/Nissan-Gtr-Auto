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
      "id, doc_type, status, document_number, currency, exchange_rate_applied, subtotal, total, posted_at, customer_phone_e164, customer_email, warehouse_id",
    )
    .eq("id", documentId)
    .maybeSingle();
  if (invErr) throw new Error(invErr.message);
  if (!inv || inv.status !== "posted") {
    throw new Error("posted invoice/credit note required");
  }

  let wh: { code: string; name: string } | null = null;
  if (inv.warehouse_id) {
    const { data: warehouse } = await supabase
      .from("warehouses")
      .select("code, name")
      .eq("id", inv.warehouse_id)
      .maybeSingle();
    wh = warehouse;
  }

  const { data: lines, error: lineErr } = await supabase
    .from("sales_invoice_lines")
    .select(
      "qty, unit_price, line_total, is_core_charge, stock_item_id",
    )
    .eq("invoice_id", documentId)
    .order("created_at", { ascending: true });
  if (lineErr) throw new Error(lineErr.message);

  const itemIds = [...new Set((lines ?? []).map((l) => l.stock_item_id))];
  const itemMap = new Map<
    string,
    { oem_part_number: string; description: string | null }
  >();
  if (itemIds.length) {
    const { data: items } = await supabase
      .from("stock_items")
      .select("id, oem_part_number, description")
      .in("id", itemIds);
    for (const it of items ?? []) {
      itemMap.set(it.id, {
        oem_part_number: it.oem_part_number,
        description: it.description,
      });
    }
  }

  const { data: allocations } = await supabase
    .from("payment_allocations")
    .select("amount, payment_entry_id")
    .eq("sales_invoice_id", documentId);

  const peIds = [
    ...new Set((allocations ?? []).map((a) => a.payment_entry_id).filter(Boolean)),
  ];
  const peMap = new Map<
    string,
    { tender: string; currency: string; status: string }
  >();
  if (peIds.length) {
    const { data: entries } = await supabase
      .from("payment_entries")
      .select("id, tender, currency, status")
      .in("id", peIds);
    for (const pe of entries ?? []) {
      peMap.set(pe.id, {
        tender: pe.tender,
        currency: pe.currency,
        status: pe.status,
      });
    }
  }

  const tenders: { tender: string; amount: number; currency: string }[] = [];
  for (const a of allocations ?? []) {
    const pe = peMap.get(a.payment_entry_id);
    if (!pe || pe.status !== "posted") continue;
    tenders.push({
      tender: pe.tender,
      amount: Number(a.amount),
      currency: pe.currency,
    });
  }

  const pdfLines = (lines ?? []).map((l) => {
    const item = itemMap.get(l.stock_item_id);
    return {
      description:
        item?.description?.trim() || item?.oem_part_number || "Part",
      qty: Number(l.qty),
      unitPrice: Number(l.unit_price),
      lineTotal: Number(l.line_total),
      isCoreCharge: Boolean(l.is_core_charge),
    };
  });

  return { inv, wh, pdfLines, tenders };
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
      // Meta must fetch the document URL; prefer short-lived Storage signed URL.
      // Caption/SMS still use company-domain download_url in summary_body.
      let docLink: string | null = null;
      if (row.pdf_storage_path) {
        const { data: signed } = await supabase.storage
          .from(BUCKET)
          .createSignedUrl(row.pdf_storage_path, 60 * 60 * 24 * 7);
        docLink = signed?.signedUrl ?? null;
      }
      if (docLink) {
        await sendWhatsAppDocument(cfg, to, {
          link: docLink,
          filename: "receipt.pdf",
          caption: body.slice(0, 1024),
        });
      } else if (row.download_url) {
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

    const hasAnyChannelSecret =
      !!getSmsGatewayConfig() ||
      !!getEmailSendConfig() ||
      !!getWhatsAppCloudConfig();

    const pdfResults: {
      document_id: string;
      artifact_id: string;
      storage_path: string;
    }[] = [];
    const pdfErrors: { document_id: string; error: string }[] = [];

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
          pdfErrors.push({ document_id: id, error: String(e) });
          console.error(`receipt PDF failed for ${id}:`, e);
        }
      }
    }

    // Local stub: RPC marks sent without HTTP (localhost only).
    if (!hasAnyChannelSecret && localStub) {
      const { data, error } = await supabase.rpc(
        "process_receipt_outbox_batch",
        { p_limit: limit, p_stub_success: true },
      );
      if (error) return jsonErr(error.message, 400);
      return jsonOk({
        pdfs: pdfResults,
        pdf_errors: pdfErrors.length ? pdfErrors : undefined,
        channels_processed: data,
        stub: true,
        fiscal: false,
      });
    }

    // Non-local without secrets: PDF ok; refuse channel drain (no fake success).
    if (!hasAnyChannelSecret && !localStub) {
      return jsonOk(
        {
          pdfs: pdfResults,
          pdf_errors: pdfErrors.length ? pdfErrors : undefined,
          channels: null,
          stub: false,
          fiscal: false,
          error:
            "SMS/EMAIL/WhatsApp secrets unset — channel drain refused (set WORKER_ALLOW_UNVERIFIED_LOCAL=1 with WORKER_SHARED_SECRET unset for local stub only)",
        },
        pdfResults.length ? 200 : 503,
      );
    }

    const channels = await drainChannels(supabase, limit, false);
    return jsonOk({
      pdfs: pdfResults,
      pdf_errors: pdfErrors.length ? pdfErrors : undefined,
      channels,
      stub: false,
      fiscal: false,
    });
  } catch (e) {
    return jsonErr(String(e), 500);
  }
});
