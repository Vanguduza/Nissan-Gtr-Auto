from pathlib import Path
import re

configs = {
    "apps/android-customer/feature/orders/src/main/java/co/zw/nissangtr/customer/orders/OrdersScreen.kt": {
        "title": "Orders",
        "subtitle": "History · track",
    },
    "apps/android-customer/feature/pay/src/main/java/co/zw/nissangtr/customer/pay/PayIntentScreen.kt": {
        "title": "Pay",
        "subtitle": "ContiPay · Paynow · EcoCash",
    },
    "apps/android-customer/feature/garage/src/main/java/co/zw/nissangtr/customer/garage/GarageScreen.kt": {
        "title": "My Garage",
        "subtitle": "VIN · vehicles",
    },
    "apps/android-customer/feature/compare/src/main/java/co/zw/nissangtr/customer/compare/CompareScreen.kt": {
        "title": "Compare",
        "subtitle": "Attribute matrix",
    },
    "apps/android-customer/feature/chat/src/main/java/co/zw/nissangtr/customer/chat/ChatScreen.kt": {
        "title": "Live chat",
        "subtitle": "Counter support",
        "extra_params": "scrollable = false, ",
    },
    "apps/android-customer/feature/reviews/src/main/java/co/zw/nissangtr/customer/reviews/ReviewsScreen.kt": {
        "title": "Reviews",
        "subtitle": "Bridge photo attach",
    },
    "apps/android-customer/feature/track/src/main/java/co/zw/nissangtr/customer/track/DeliveryTrackScreen.kt": {
        "title": "Live delivery",
        "subtitle": "Last point · ETA only",
    },
    "apps/android-customer/feature/address/src/main/java/co/zw/nissangtr/customer/address/AddressScreen.kt": {
        "title": "Addresses",
        "subtitle": "Delivery · map pick",
    },
}

for path, cfg in configs.items():
    p = Path(path)
    t = p.read_text(encoding="utf-8")
    extra = cfg.get("extra_params", "")
    title = cfg["title"]
    subtitle = cfg["subtitle"]
    t = re.sub(
        r"ShopDefaultScreen\(title = TITLE_PLACEHOLDER, onBack = onBack,\s*",
        f'ShopDefaultScreen(\n        title = "{title}",\n        subtitle = "{subtitle}",\n        onBack = onBack,\n        {extra}',
        t,
        count=1,
    )
    t = t.replace(
        "GtrFeatureBody(",
        f'ShopDefaultScreen(title = "{title}", subtitle = "{subtitle}", onBack = onBack, ',
    )
    t = t.replace("GtrSectionLabel(", "ShopSectionHeader(title = ")
    if "ShopSectionHeader(" in t and "import co.zw.nissangtr.ui.shop.ShopSectionHeader" not in t:
        t = t.replace(
            "import co.zw.nissangtr.ui.shop.ShopDefaultScreen\n",
            "import co.zw.nissangtr.ui.shop.ShopDefaultScreen\nimport co.zw.nissangtr.ui.shop.ShopSectionHeader\n",
        )
    t = re.sub(
        r'\s*OutlinedButton\(\s*onClick = onBack,\s*shape = sharp,\s*\)\s*\{\s*Text\("Back"\)\s*\}\s*',
        "\n",
        t,
    )
    p.write_text(t, encoding="utf-8")
    print("fixed", path)

print("done")
