// Usage (from repo root): node packages/pos-design/scripts/gen-lucide-icons.mjs apps/web/node_modules/lucide-react/dist/esm/icons House FileText ... > packages/pos-design/src/main/java/co/zw/nissangtr/pos/design/icons/PosIconsLucide.kt
// Generates Kotlin ImageVector extension properties from vendored lucide-react icon nodes.
import fs from "node:fs";
const dir = process.argv[2];
const names = process.argv.slice(3);
const kebab = (n) => n.replace(/([a-z])([A-Z0-9])/g, "$1-$2").replace(/([0-9])([A-Z])/g, "$1-$2").toLowerCase();
const num = (v) => Number(v);
function toPath(tag, a) {
  switch (tag) {
    case "path": return a.d;
    case "circle": { const cx = num(a.cx), cy = num(a.cy), r = num(a.r);
      return `M${cx - r} ${cy}a${r} ${r} 0 1 0 ${2 * r} 0a${r} ${r} 0 1 0 ${-2 * r} 0z`; }
    case "ellipse": { const cx = num(a.cx), cy = num(a.cy), rx = num(a.rx), ry = num(a.ry);
      return `M${cx - rx} ${cy}a${rx} ${ry} 0 1 0 ${2 * rx} 0a${rx} ${ry} 0 1 0 ${-2 * rx} 0z`; }
    case "line": return `M${a.x1} ${a.y1}L${a.x2} ${a.y2}`;
    case "polyline": { const p = a.points.trim().split(/[\s,]+/); let s = `M${p[0]} ${p[1]}`; for (let i = 2; i < p.length; i += 2) s += `L${p[i]} ${p[i + 1]}`; return s; }
    case "polygon": { const p = a.points.trim().split(/[\s,]+/); let s = `M${p[0]} ${p[1]}`; for (let i = 2; i < p.length; i += 2) s += `L${p[i]} ${p[i + 1]}`; return s + "z"; }
    case "rect": { const x = num(a.x ?? 0), y = num(a.y ?? 0), w = num(a.width), h = num(a.height); const r = Math.min(num(a.rx ?? a.ry ?? 0), w / 2, h / 2);
      if (!r) return `M${x} ${y}h${w}v${h}h${-w}z`;
      return `M${x + r} ${y}h${w - 2 * r}a${r} ${r} 0 0 1 ${r} ${r}v${h - 2 * r}a${r} ${r} 0 0 1 ${-r} ${r}h${-(w - 2 * r)}a${r} ${r} 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a${r} ${r} 0 0 1 ${r} ${-r}z`; }
    default: throw new Error("unsupported tag " + tag);
  }
}
let out = `package co.zw.nissangtr.pos.design.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// GENERATED from lucide-react icon nodes (ISC licence) by packages/pos-design/scripts/gen-lucide-icons.mjs — do not edit by hand.
// Same box and stroke as PosIcons (Blueprint §4.7 / SYS-10): 24 dp, 1.75 stroke, round caps and joins.

private fun lucideFromPaths(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(
        name = "Lucide.$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        paths.forEach { d ->
            addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
`;
for (const n of names) {
  const file = `${dir}/${kebab(n)}.mjs`;
  const src = fs.readFileSync(file, "utf8");
  const node = src.slice(src.indexOf("const __iconNode = ") + 19, src.indexOf("];\nconst") + 1);
  const arr = Function(`return ${node}`)();
  const paths = arr.map(([tag, a]) => JSON.stringify(toPath(tag, a)));
  out += `\nprivate val _${n}: ImageVector by lazy {\n    lucideFromPaths(\n        "${n}",\n${paths.map((p) => `        ${p},`).join("\n")}\n    )\n}\nval PosIcons.${n}: ImageVector get() = _${n}\n`;
}
process.stdout.write(out);
