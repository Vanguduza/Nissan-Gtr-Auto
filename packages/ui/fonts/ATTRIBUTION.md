# Font attribution (OFL)

Mobile and shared packages ship **Titillium Web** and **Source Sans 3** under the SIL Open Font License 1.1.

| Family | License file | Upstream |
|--------|--------------|----------|
| Titillium Web | [`OFL-TitilliumWeb.txt`](./OFL-TitilliumWeb.txt) | [google/fonts ofl/titilliumweb](https://github.com/google/fonts/tree/main/ofl/titilliumweb) |
| Source Sans 3 | [`OFL-SourceSans3.txt`](./OFL-SourceSans3.txt) | [adobe-fonts/source-sans](https://github.com/adobe-fonts/source-sans) / Google Fonts OFL |

Bundled copies:

- Canonical OFL texts + TTF mirrors: this directory
  - `TitilliumWeb-{Regular,SemiBold,Bold}.ttf`
  - `SourceSans3-{Regular,SemiBold,Bold}.ttf`
- Android Compose: `packages/android-ui/src/main/res/font/`
  (`titillium_web_*`, `source_sans_3_*` — consumed by `GtrTypography`)
- iOS: `apps/ios/GTRCustomer/Fonts/` (+ `UIAppFonts` in Info.plist)

Do not rename families in a way that violates OFL reserved-name rules when redistributing modified versions.
