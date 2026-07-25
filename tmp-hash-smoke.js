const crypto = require("crypto");
const fs = require("fs");
const key = "3e9fed89-60e1-4ce5-ab6e-6b1eb2d4f977";
const inbound =
  "status=Ok&browserurl=https%3a%2f%2fstaging.paynow.co.zw%2fPayment%2fConfirmPayment%2f9510&pollurl=https%3a%2f%2fstaging.paynow.co.zw%2fInterface%2fCheckPayment%2f%3fguid%3dc7ed41da-0159-46da-b428-69549f770413&paynowreference=9510&hash=750DD0B0DF374678707BB5AF915AF81C228B9058AD57BB7120569EC68BBB9C2EFC1B26C6375D2BC562AC909B3CD6B2AF1D42E1A5E479FFAC8F4FB3FDCE71DF4D";
const ordered = [];
let provided = "";
for (const p of inbound.split("&")) {
  const eq = p.indexOf("=");
  const k = decodeURIComponent(p.slice(0, eq));
  const v = decodeURIComponent(p.slice(eq + 1).replace(/\+/g, " "));
  if (k.toLowerCase() === "hash") {
    provided = v;
    continue;
  }
  ordered.push(v);
}
const h2 = crypto
  .createHash("sha512")
  .update(ordered.join("") + key, "utf8")
  .digest("hex")
  .toUpperCase();
const hmac = crypto
  .createHmac("sha256", "test-secret")
  .update('{"reference":"CP-1"}')
  .digest("hex");
fs.writeFileSync(
  "tmp-hash-smoke.txt",
  [
    "inbound=" + (h2 === provided.toUpperCase()),
    "hmac_len=" + hmac.length,
    "concat=" + ordered.join(""),
  ].join("\n"),
);
