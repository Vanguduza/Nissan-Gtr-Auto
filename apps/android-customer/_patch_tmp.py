from pathlib import Path

p = Path(
    r"c:\Users\j\Desktop\nissan gtr\apps\android-customer\feature\catalog"
    r"\src\main\java\co\zw\nissangtr\customer\catalog\CatalogScreen.kt"
)
text = p.read_text(encoding="utf-8")
start = text.find('            ShopMerchTitleRow(\n                title = "Most sale"')
print("start", start)
# Find the Spacer(24) that closes the rails section inside KmpHome —
# it is the first Spacer(24) after Most sale.
end = text.find("            Spacer(modifier.height(24.dp))", start)
print("end", end)
print("between preview", repr(text[start : start + 120]))
print("around end", repr(text[end : end + 80]))
