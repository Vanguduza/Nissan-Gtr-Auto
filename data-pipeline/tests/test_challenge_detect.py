from __future__ import annotations

from data_pipeline.amayama_catalog_auto import (
    is_cloudflare_challenge,
    is_recaptcha_challenge,
)


def test_is_recaptcha_challenge() -> None:
    assert is_recaptcha_challenge(
        '<form action="/bot/captcha/verify"><div class="g-recaptcha"></div></form>'
    )
    assert is_recaptcha_challenge(
        "<p>Complete the captcha</p><div class='g-recaptcha'></div>"
    )
    # PartSouq normal pages embed widgets — must NOT be treated as blocking
    assert not is_recaptcha_challenge(
        "<title>Nissan | Parts Catalogs | PartSouq</title>"
        "<div class='g-recaptcha' data-sitekey='x'></div>"
        "<a href='/en/catalog/genuine/locate?c=Nissan'>Nissan</a>"
    )
    assert not is_recaptcha_challenge("<html>catalog list</html>")


def test_is_cloudflare_challenge() -> None:
    assert is_cloudflare_challenge("<title>Just a moment...</title>")
    assert not is_cloudflare_challenge("<html>PartSouq catalog</html>")
