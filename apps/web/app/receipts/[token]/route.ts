import { NextResponse } from "next/server";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

type EdgePayload = {
  signed_url?: string;
  signedUrl?: string;
  download_url?: string;
  error?: string;
};

/**
 * Public company-domain receipt download.
 * Validates token via Edge `receipt-download` (service_role stays off the web app —
 * see docs/HARDENING.md). Tax-agnostic PDF only; no ZIMRA.
 *
 * @backend_agent gap until Edge exists: POST /functions/v1/receipt-download
 * body `{ token }` → `{ signed_url }` for Storage bucket `customer-receipts`.
 */
export async function GET(
  _request: Request,
  context: { params: Promise<{ token: string }> },
) {
  const { token: raw } = await context.params;
  const token = decodeURIComponent(raw ?? "").trim();
  if (!token || token.length < 8 || /[^a-zA-Z0-9_-]/.test(token)) {
    return new NextResponse("Invalid receipt token.", { status: 400 });
  }

  const url = process.env.NEXT_PUBLIC_SUPABASE_URL?.replace(/\/$/, "");
  const anon = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
  if (!url || !anon) {
    return new NextResponse("Receipt service unavailable.", { status: 503 });
  }

  let edgeRes: Response;
  try {
    edgeRes = await fetch(`${url}/functions/v1/receipt-download`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${anon}`,
        apikey: anon,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ token }),
      cache: "no-store",
    });
  } catch {
    return new NextResponse(
      "Receipt download edge unreachable. @backend_agent: deploy receipt-download.",
      { status: 502 },
    );
  }

  if (edgeRes.status === 404) {
    return new NextResponse(
      "Receipt not found or Edge receipt-download missing. @backend_agent: add resolve-by-token Edge (Storage signed URL for customer-receipts).",
      { status: 404 },
    );
  }

  if (!edgeRes.ok) {
    let detail = `HTTP ${edgeRes.status}`;
    try {
      const body = (await edgeRes.json()) as EdgePayload;
      if (body.error) detail = body.error;
    } catch {
      /* ignore */
    }
    return new NextResponse(`Receipt download failed: ${detail}`, {
      status: edgeRes.status >= 400 && edgeRes.status < 600 ? edgeRes.status : 502,
    });
  }

  let payload: EdgePayload;
  try {
    payload = (await edgeRes.json()) as EdgePayload;
  } catch {
    return new NextResponse("Invalid receipt edge response.", { status: 502 });
  }

  const signed =
    payload.signed_url?.trim() ||
    payload.signedUrl?.trim() ||
    payload.download_url?.trim();

  if (!signed) {
    return new NextResponse(
      "Receipt edge returned no signed URL. @backend_agent: mint Storage signed URL for pdf_storage_path.",
      { status: 502 },
    );
  }

  // Redirect to short-lived Storage signed URL (PDF stream).
  return NextResponse.redirect(signed, 302);
}
