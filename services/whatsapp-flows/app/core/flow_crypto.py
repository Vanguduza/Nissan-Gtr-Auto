"""Meta WhatsApp Flows end-to-end encryption (data API v3.0).

Spec: https://developers.facebook.com/docs/whatsapp/flows/guides/implementingyourflowendpoint/

- Decrypt AES key with RSA-OAEP SHA-256 / MGF1 SHA-256
- Decrypt payload with AES-128-GCM (16-byte tag appended)
- Response: invert IV bytes (XOR 0xFF), AES-GCM encrypt, base64 body
"""

from __future__ import annotations

import base64
import json
from pathlib import Path
from typing import Any

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.ciphers.aead import AESGCM


class FlowCryptoError(Exception):
    """Raised when Flow payload crypto fails."""


class FlowCrypto:
    def __init__(self, private_key_pem: bytes | str) -> None:
        if isinstance(private_key_pem, str):
            private_key_pem = private_key_pem.encode("utf-8")
        self._private_key = serialization.load_pem_private_key(
            private_key_pem,
            password=None,
        )

    @classmethod
    def from_pem_path(cls, path: str | Path) -> FlowCrypto:
        pem = Path(path).read_bytes()
        return cls(pem)

    def decrypt_request(
        self,
        *,
        encrypted_aes_key_b64: str,
        encrypted_flow_data_b64: str,
        initial_vector_b64: str,
    ) -> tuple[dict[str, Any], bytes, bytes]:
        """Return (clear_json, aes_key, request_iv)."""
        try:
            enc_aes_key = base64.b64decode(encrypted_aes_key_b64)
            enc_flow = base64.b64decode(encrypted_flow_data_b64)
            iv = base64.b64decode(initial_vector_b64)
        except Exception as exc:  # noqa: BLE001
            raise FlowCryptoError(f"invalid base64 fields: {exc}") from exc

        try:
            aes_key = self._private_key.decrypt(
                enc_aes_key,
                padding.OAEP(
                    mgf=padding.MGF1(algorithm=hashes.SHA256()),
                    algorithm=hashes.SHA256(),
                    label=None,
                ),
            )
        except Exception as exc:  # noqa: BLE001
            raise FlowCryptoError(f"RSA-OAEP decrypt failed: {exc}") from exc

        if len(enc_flow) < 16:
            raise FlowCryptoError("encrypted_flow_data too short for GCM tag")
        ciphertext, tag = enc_flow[:-16], enc_flow[-16:]
        try:
            clear = AESGCM(aes_key).decrypt(iv, ciphertext + tag, None)
        except Exception as exc:  # noqa: BLE001
            raise FlowCryptoError(f"AES-GCM decrypt failed: {exc}") from exc

        try:
            payload = json.loads(clear.decode("utf-8"))
        except Exception as exc:  # noqa: BLE001
            raise FlowCryptoError(f"JSON parse failed: {exc}") from exc
        if not isinstance(payload, dict):
            raise FlowCryptoError("flow payload must be a JSON object")
        return payload, aes_key, iv

    def encrypt_response(
        self,
        response_obj: dict[str, Any],
        *,
        aes_key: bytes,
        request_iv: bytes,
    ) -> str:
        """Return base64 ciphertext+tag for HTTP body (plain text)."""
        inverted_iv = bytes(b ^ 0xFF for b in request_iv)
        clear = json.dumps(response_obj, separators=(",", ":"), ensure_ascii=False).encode(
            "utf-8",
        )
        aesgcm = AESGCM(aes_key)
        # cryptography returns ciphertext || tag
        sealed = aesgcm.encrypt(inverted_iv, clear, None)
        return base64.b64encode(sealed).decode("ascii")
