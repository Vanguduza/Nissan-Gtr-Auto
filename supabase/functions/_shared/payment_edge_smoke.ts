/**
 * Local smoke: Paynow SHA512 hash vectors from Paynow developer docs.
 * Run: deno run --allow-all supabase/functions/_shared/payment_edge_smoke.ts
 */
import {
  contipayHmacSha256Hex,
  paynowHashFromValues,
  verifyPaynowMessageHash,
} from "./payment_edge.ts";

const key = "3e9fed89-60e1-4ce5-ab6e-6b1eb2d4f977";

const outboundExpected =
  "2A033FC38798D913D42ECB786B9B19645ADEDBDE788862032F1BD82CF3B92DEF84F316385D5B40DBB35F1A4FD7D5BFE73835174136463CDD48C9366B0749C689";

const outbound = await paynowHashFromValues(
  [
    "1201",
    "TEST REF",
    "99.99",
    "A test ticket transaction",
    "http://www.google.com/search?q=returnurl",
    "http://www.google.com/search?q=resulturl",
    "Message",
  ],
  key,
);

if (outbound !== outboundExpected) {
  console.error("OUTBOUND FAIL", outbound);
  Deno.exit(1);
}
console.log("outbound Paynow hash OK");

const inboundRaw =
  "status=Ok&browserurl=https%3a%2f%2fstaging.paynow.co.zw%2fPayment%2fConfirmPayment%2f9510&pollurl=https%3a%2f%2fstaging.paynow.co.zw%2fInterface%2fCheckPayment%2f%3fguid%3dc7ed41da-0159-46da-b428-69549f770413&paynowreference=9510&hash=750DD0B0DF374678707BB5AF915AF81C228B9058AD57BB7120569EC68BBB9C2EFC1B26C6375D2BC562AC909B3CD6B2AF1D42E1A5E479FFAC8F4FB3FDCE71DF4D";

const verified = await verifyPaynowMessageHash(inboundRaw, key);
if (!verified.ok) {
  console.error("INBOUND FAIL", verified);
  Deno.exit(1);
}
console.log("inbound Paynow hash OK");

const hmac = await contipayHmacSha256Hex(
  '{"reference":"CP-1","status":"success"}',
  "test-secret",
);
if (hmac.length !== 64) {
  console.error("HMAC FAIL", hmac);
  Deno.exit(1);
}
console.log("ContiPay HMAC hex OK", hmac.slice(0, 16) + "…");
console.log("ALL PASS");
