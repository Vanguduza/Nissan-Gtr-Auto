from __future__ import annotations

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # Supabase (service role — Flow endpoint is Meta-authenticated, not user JWT)
    supabase_url: str = "http://127.0.0.1:54321"
    supabase_service_role_key: str = ""

    # Meta WhatsApp Cloud API
    whatsapp_access_token: str = ""
    whatsapp_phone_number_id: str = ""
    whatsapp_api_version: str = "v21.0"
    whatsapp_allow_unverified_local: bool = False

    # Flow endpoint crypto — path to RSA private key PEM matching Meta-uploaded public key
    flow_private_key_path: str = "keys/private.pem"

    # Pricing
    default_currency: str = "USD"
    retail_price_list_code: str = "RETAIL"
    # Delivery fee schedule (tax-agnostic; no ZIMRA). Amounts in default_currency.
    delivery_fee_counter_collect: float = 0.0
    delivery_fee_harare: float = 5.0
    delivery_fee_nationwide: float = 15.0

    # Public base URL for payment return / receipt links
    public_base_url: str = "https://nissangtrauto.co.zw"
    site_checkout_path: str = "/checkout/pay"

    # Default WhatsApp Flow tender: ecocash (direct) | paynow (aggregator CTA)
    default_whatsapp_payment_provider: str = "ecocash"

    # EcoCash Instant Payments — plug in after developers.ecocash.co.zw registration
    ecocash_api_key: str = ""
    ecocash_environment: str = "sandbox"  # sandbox | live
    ecocash_api_base_url: str = "https://developers.ecocash.co.zw/api/ecocash_pay"
    ecocash_c2b_path_sandbox: str = "/api/v2/payment/instant/c2b/sandbox"
    ecocash_c2b_path_live: str = "/api/v2/payment/instant/c2b/live"
    ecocash_lookup_path: str = "/api/v1/transaction/transactions/lookup"
    # x-api-key (default) | bearer
    ecocash_auth_header: str = "x-api-key"
    ecocash_allow_stub: bool = True
    # Required for /ecocash/{push,lookup,callback} settle (fail closed unless local flag)
    ecocash_webhook_secret: str = ""
    ecocash_allow_unverified_local: bool = False

    # Local stubs when PSP / Meta secrets unset
    allow_payment_stub: bool = True

    # Paynow / generic PSP settle on /api/v1/payments/callback — fail closed without secret
    # (or verified Paynow form hash via paynow_integration_key). Local stub flag only.
    payments_webhook_secret: str = ""
    payments_allow_unverified_local: bool = False
    paynow_integration_key: str = ""


@lru_cache
def get_settings() -> Settings:
    return Settings()
