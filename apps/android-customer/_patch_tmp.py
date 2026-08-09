from pathlib import Path

p = Path(
    r"c:\Users\j\Desktop\nissan gtr\apps\android-customer\feature\catalog"
    r"\src\main\java\co\zw\nissangtr\customer\catalog\CatalogScreen.kt"
)
text = p.read_text(encoding="utf-8")
marker = 'title = "Most sale"'
idx = text.find(marker)
print("idx", idx)
print(repr(text[idx - 40 : idx + 80]))
# Find LazyRow after Most sale
start = text.find("            ShopMerchTitleRow(\n                title = \"Most sale\"")
print("start", start)
if start < 0:
    # try CRLF
    start = text.find("            ShopMerchTitleRow(\r\n                title = \"Most sale\"")
    print("crlf start", start)
