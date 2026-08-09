from pathlib import Path

p = Path(
    r"c:\Users\j\Desktop\nissan gtr\apps\android-customer\feature\catalog"
    r"\src\main\java\co\zw\nissangtr\customer\catalog\CatalogScreen.kt"
)
text = p.read_text(encoding="utf-8")
start = text.find('            ShopMerchTitleRow(\n                title = "Most sale"')
if start < 0:
    raise SystemExit("start not found")
end_marker = "            Spacer(modifier.height(24.dp))\n        }\n    }\n}"
end = text.find(end_marker, start)
if end < 0:
    raise SystemExit("end not found")
# only replace through the spacer before closing braces of KmpHome
end = text.find("            Spacer(modifier.height(24.dp))", start)
if end < 0:
    raise SystemExit("spacer not found")
# include the spacer line
end_line = text.find("\n", end)
new_block = '''            ShopMerchTitleRow(
                title = "Most sale",
                actionLabel = "See all",
                onAction = onSeeAllMostSale,
            )
            if (mostSale.isEmpty()) {
                ShopHonestEmpty(
                    title = "No stock yet",
                    body = "Catalog SoR has no saleable stock items. Reload inventory, then refresh.",
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    rowItems(mostSale, key = { it.stockItemId }) { item ->
                        ShopProductCard(
                            title = item.oem,
                            subtitle = item.name,
                            priceLabel = item.usd?.let { "USD %.2f".format(it) } ?: "On request",
                            liked = wishOems.contains(item.oem.trim().uppercase()),
                            onLikeClick = { onToggleWish(item) },
                            onClick = { onOpenProduct(item.oem) },
                        )
                    }
                }
            }

            Spacer(modifier.height(16.dp))
            ShopMerchTitleRow(
                title = "Newest products",
                actionLabel = "See all",
                onAction = onSeeAllNewest,
            )
            if (newest.isEmpty()) {
                ShopHonestEmpty(
                    title = "No recent parts",
                    body = "No stock rows yet — empty catalog, not a filter bug.",
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    rowItems(newest, key = { "n-${it.stockItemId}" }) { item ->
                        ShopProductCard(
                            title = item.oem,
                            subtitle = item.name,
                            priceLabel = item.usd?.let { "USD %.2f".format(it) } ?: "On request",
                            liked = wishOems.contains(item.oem.trim().uppercase()),
                            onLikeClick = { onToggleWish(item) },
                            onClick = { onOpenProduct(item.oem) },
                        )
                    }
                }
            }
            Spacer(modifier.height(24.dp))'''
p.write_text(text[:start] + new_block + text[end_line:], encoding="utf-8")
print("patched", start, end_line)
