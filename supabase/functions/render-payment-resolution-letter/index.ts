// Recovered from the deployed bundle (render-payment-resolution-letter v1): the source was never
// committed. Renders a signed provider-resolution letter (POS phase 8; see
// 20261004060207_payment_letters_list_and_profile.sql), archives it in the private
// payment-resolution-letters bucket and returns the PDF.
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
import { corsHeaders, jsonResponse } from "../_shared/payment_edge.ts";
import { buildPaymentResolutionLetterPdf } from "../_shared/branded_docs_pdf.ts";
async function sha256Hex(bytes: Uint8Array): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest)).map((b)=>b.toString(16).padStart(2, "0")).join("");
}
function text(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}
Deno.serve(async (req)=>{
  const cors = corsHeaders(req);
  if (req.method === "OPTIONS") return new Response("ok", {
    headers: cors
  });
  if (req.method !== "POST") return jsonResponse({
    error: "POST required"
  }, 405, cors);
  const url = Deno.env.get("SUPABASE_URL")?.trim();
  const anon = Deno.env.get("SUPABASE_ANON_KEY")?.trim();
  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim();
  if (!url || !anon || !serviceRole) {
    return jsonResponse({
      error: "payment-resolution renderer is not configured"
    }, 503, cors);
  }
  const auth = req.headers.get("Authorization") ?? "";
  if (!auth.toLowerCase().startsWith("bearer ")) {
    return jsonResponse({
      error: "unauthorized"
    }, 401, cors);
  }
  const userClient = createClient(url, anon, {
    global: {
      headers: {
        Authorization: auth
      }
    },
    auth: {
      persistSession: false,
      autoRefreshToken: false
    }
  });
  const { data: userData, error: userError } = await userClient.auth.getUser();
  if (userError || !userData.user) return jsonResponse({
    error: "unauthorized"
  }, 401, cors);
  let body: Record<string, unknown>;
  try {
    body = await req.json();
  } catch  {
    return jsonResponse({
      error: "invalid JSON"
    }, 400, cors);
  }
  const letterId = text(body.letter_id);
  if (!letterId) return jsonResponse({
    error: "letter_id required"
  }, 400, cors);
  const { data: renderData, error: renderError } = await userClient.rpc("get_payment_resolution_letter_render_data", {
    p_letter_id: letterId
  });
  if (renderError || !renderData) {
    return jsonResponse({
      error: renderError?.message ?? "letter unavailable"
    }, 403, cors);
  }
  const root = renderData as { letter?: Record<string, unknown>; business?: Record<string, unknown> };
  const letter = root.letter ?? {};
  const business = root.business ?? {};
  const signatureBucket = text(letter.signature_storage_bucket);
  const signaturePath = text(letter.signature_storage_path);
  const signatureMime = text(letter.signature_mime_type);
  const signatureSha = text(letter.signature_sha256)?.toLowerCase();
  if (!signatureBucket || !signaturePath || !signatureMime || !signatureSha) {
    return jsonResponse({
      error: "letter has no registered manager signature"
    }, 409, cors);
  }
  const admin = createClient(url, serviceRole, {
    auth: {
      persistSession: false,
      autoRefreshToken: false
    }
  });
  const { data: sigBlob, error: sigError } = await admin.storage.from(signatureBucket).download(signaturePath);
  if (sigError || !sigBlob) {
    return jsonResponse({
      error: "registered manager signature could not be loaded"
    }, 409, cors);
  }
  const signatureBytes = new Uint8Array(await sigBlob.arrayBuffer());
  const actualSignatureSha = await sha256Hex(signatureBytes);
  if (actualSignatureSha !== signatureSha) {
    return jsonResponse({
      error: "manager signature integrity check failed"
    }, 409, cors);
  }
  let pdf: Uint8Array;
  try {
    pdf = await buildPaymentResolutionLetterPdf({
      documentNumber: text(letter.document_number) ?? "PDL-UNKNOWN",
      issuedAt: text(letter.issued_at) ?? new Date().toISOString(),
      business: {
        legalName: text(business.legal_name) ?? "Nissan GTR Auto",
        tradingName: text(business.trading_name) ?? "Nissan GTR Auto",
        domain: text(business.domain) ?? "nissangtrauto.co.zw",
        addressLine1: text(business.address_line1),
        addressLine2: text(business.address_line2),
        city: text(business.city),
        country: text(business.country),
        phone: text(business.phone_e164),
        email: text(business.email),
        registrationNumber: text(business.registration_number)
      },
      customerName: text(letter.customer_name),
      invoiceDocumentNumber: text(letter.invoice_document_number),
      provider: text(letter.provider) ?? "unknown",
      observedStatus: text(letter.observed_status) ?? "unknown",
      amount: Number(letter.amount ?? 0),
      currency: text(letter.currency) ?? "USD",
      externalReference: text(letter.external_reference),
      providerReference: text(letter.provider_reference),
      terminalTransactionId: text(letter.terminal_transaction_id),
      rrn: text(letter.rrn),
      authorizationCode: text(letter.authorization_code),
      cardLast4: text(letter.card_last4),
      cardScheme: text(letter.card_scheme),
      failureDetail: text(letter.failure_detail),
      issueNotes: text(letter.issue_notes),
      managerName: text(letter.manager_name) ?? "Manager",
      managerTitle: text(letter.manager_title),
      managerEmployeeCode: text(letter.manager_employee_code) ?? "",
      signatureBytes,
      signatureMimeType: signatureMime
    });
  } catch (error) {
    console.error("render-payment-resolution-letter:", error);
    return jsonResponse({
      error: "letter render failed"
    }, 500, cors);
  }
  const pdfSha = await sha256Hex(pdf);
  const year = new Date(text(letter.issued_at) ?? Date.now()).getUTCFullYear();
  const path = `${year}/${letterId}.pdf`;
  const { error: uploadError } = await admin.storage.from("payment-resolution-letters").upload(path, pdf, {
    upsert: true,
    contentType: "application/pdf",
    cacheControl: "0"
  });
  if (uploadError) {
    return jsonResponse({
      error: `letter archive failed: ${uploadError.message}`
    }, 500, cors);
  }
  const { error: markError } = await admin.from("payment_resolution_letters").update({
    rendered_storage_bucket: "payment-resolution-letters",
    rendered_storage_path: path,
    rendered_sha256: pdfSha,
    rendered_at: new Date().toISOString()
  }).eq("id", letterId);
  if (markError) {
    return jsonResponse({
      error: `letter archive metadata failed: ${markError.message}`
    }, 500, cors);
  }
  const filename = `${text(letter.document_number) ?? "payment-resolution"}.pdf`;
  return new Response(pdf, {
    status: 200,
    headers: {
      ...cors,
      "Content-Type": "application/pdf",
      "Content-Disposition": `attachment; filename="${filename.replace(/[^A-Za-z0-9._-]/g, "-")}"`,
      "X-GTR-Letter-Id": letterId,
      "X-GTR-SHA256": pdfSha,
      "Cache-Control": "no-store"
    }
  });
});
