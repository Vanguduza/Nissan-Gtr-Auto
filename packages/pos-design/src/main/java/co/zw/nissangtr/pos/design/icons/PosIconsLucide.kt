package co.zw.nissangtr.pos.design.icons

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

private val _House: ImageVector by lazy {
    lucideFromPaths(
        "House",
        "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
        "M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
    )
}
val PosIcons.House: ImageVector get() = _House

private val _FileText: ImageVector by lazy {
    lucideFromPaths(
        "FileText",
        "M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z",
        "M14 2v5a1 1 0 0 0 1 1h5",
        "M10 9H8",
        "M16 13H8",
        "M16 17H8",
    )
}
val PosIcons.FileText: ImageVector get() = _FileText

private val _Undo2: ImageVector by lazy {
    lucideFromPaths(
        "Undo2",
        "M9 14 4 9l5-5",
        "M4 9h10.5a5.5 5.5 0 0 1 5.5 5.5a5.5 5.5 0 0 1-5.5 5.5H11",
    )
}
val PosIcons.Undo2: ImageVector get() = _Undo2

private val _BookOpen: ImageVector by lazy {
    lucideFromPaths(
        "BookOpen",
        "M12 5v16",
        "M20.001 19A2 2 0 0022 17V5a2 2 0 00-1.999-2L16 3.002A5 5 0 0012 5a5 5 0 00-4-2H4a2 2 0 00-2 2v12a2 2 0 001.999 2H8a5 5 0 014 2 5 5 0 014-2z",
    )
}
val PosIcons.BookOpen: ImageVector get() = _BookOpen

private val _Settings: ImageVector by lazy {
    lucideFromPaths(
        "Settings",
        "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915",
        "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z",
    )
}
val PosIcons.Settings: ImageVector get() = _Settings

private val _Cog: ImageVector by lazy {
    lucideFromPaths(
        "Cog",
        "M11 10.27 7 3.34",
        "m11 13.73-4 6.93",
        "M12 22v-2",
        "M12 2v2",
        "M14 12h8",
        "m17 20.66-1-1.73",
        "m17 3.34-1 1.73",
        "M2 12h2",
        "m20.66 17-1.73-1",
        "m20.66 7-1.73 1",
        "m3.34 17 1.73-1",
        "m3.34 7 1.73 1",
        "M10 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z",
        "M4 12a8 8 0 1 0 16 0a8 8 0 1 0 -16 0z",
    )
}
val PosIcons.Cog: ImageVector get() = _Cog

private val _Disc3: ImageVector by lazy {
    lucideFromPaths(
        "Disc3",
        "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0z",
        "M6 12c0-1.7.7-3.2 1.8-4.2",
        "M10 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z",
        "M18 12c0 1.7-.7 3.2-1.8 4.2",
    )
}
val PosIcons.Disc3: ImageVector get() = _Disc3

private val _Zap: ImageVector by lazy {
    lucideFromPaths(
        "Zap",
        "M4 14a1 1 0 0 1-.78-1.63l9.9-10.2a.5.5 0 0 1 .86.46l-1.92 6.02A1 1 0 0 0 13 10h7a1 1 0 0 1 .78 1.63l-9.9 10.2a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14z",
    )
}
val PosIcons.Zap: ImageVector get() = _Zap

private val _Droplet: ImageVector by lazy {
    lucideFromPaths(
        "Droplet",
        "M12 22a7 7 0 0 0 7-7c0-2-1-3.9-3-5.5s-3.5-4-4-6.5c-.5 2.5-2 4.9-4 6.5C6 11.1 5 13 5 15a7 7 0 0 0 7 7z",
    )
}
val PosIcons.Droplet: ImageVector get() = _Droplet

private val _Sparkles: ImageVector by lazy {
    lucideFromPaths(
        "Sparkles",
        "M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1 0 1.966l-5.558 1.051a2 2 0 0 0-1.594 1.594l-1.051 5.558a1 1 0 0 1-1.966 0l-1.051-5.558a2 2 0 0 0-1.594-1.594l-5.558-1.051a1 1 0 0 1 0-1.966l5.558-1.051a2 2 0 0 0 1.594-1.594z",
        "M20 2v4",
        "M22 4h-4",
        "M2 20a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z",
    )
}
val PosIcons.Sparkles: ImageVector get() = _Sparkles

private val _Gauge: ImageVector by lazy {
    lucideFromPaths(
        "Gauge",
        "m12 14 4-4",
        "M3.34 19a10 10 0 1 1 17.32 0",
    )
}
val PosIcons.Gauge: ImageVector get() = _Gauge

private val _ShieldCheck: ImageVector by lazy {
    lucideFromPaths(
        "ShieldCheck",
        "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z",
        "m9 12 2 2 4-4",
    )
}
val PosIcons.ShieldCheck: ImageVector get() = _ShieldCheck

private val _ChevronLeft: ImageVector by lazy {
    lucideFromPaths(
        "ChevronLeft",
        "m15 18-6-6 6-6",
    )
}
val PosIcons.ChevronLeft: ImageVector get() = _ChevronLeft

private val _Clock: ImageVector by lazy {
    lucideFromPaths(
        "Clock",
        "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0z",
        "M12 6v6l4 2",
    )
}
val PosIcons.Clock: ImageVector get() = _Clock

private val _Pin: ImageVector by lazy {
    lucideFromPaths(
        "Pin",
        "M12 17v5",
        "M9 10.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V7a1 1 0 0 1 1-1 2 2 0 0 0 0-4H8a2 2 0 0 0 0 4 1 1 0 0 1 1 1z",
    )
}
val PosIcons.Pin: ImageVector get() = _Pin

private val _PinOff: ImageVector by lazy {
    lucideFromPaths(
        "PinOff",
        "M12 17v5",
        "M15 9.34V7a1 1 0 0 1 1-1 2 2 0 0 0 0-4H7.89",
        "m2 2 20 20",
        "M9 9v1.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h11",
    )
}
val PosIcons.PinOff: ImageVector get() = _PinOff

private val _Trash2: ImageVector by lazy {
    lucideFromPaths(
        "Trash2",
        "M10 11v6",
        "M14 11v6",
        "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
        "M3 6h18",
        "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
    )
}
val PosIcons.Trash2: ImageVector get() = _Trash2

private val _EllipsisVertical: ImageVector by lazy {
    lucideFromPaths(
        "EllipsisVertical",
        "M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0z",
        "M11 5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0z",
        "M11 19a1 1 0 1 0 2 0a1 1 0 1 0 -2 0z",
    )
}
val PosIcons.EllipsisVertical: ImageVector get() = _EllipsisVertical

private val _CreditCard: ImageVector by lazy {
    lucideFromPaths(
        "CreditCard",
        "M4 5h16a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2z",
        "M2 10L22 10",
    )
}
val PosIcons.CreditCard: ImageVector get() = _CreditCard

private val _Pause: ImageVector by lazy {
    lucideFromPaths(
        "Pause",
        "M15 3h3a1 1 0 0 1 1 1v16a1 1 0 0 1 -1 1h-3a1 1 0 0 1 -1 -1v-16a1 1 0 0 1 1 -1z",
        "M6 3h3a1 1 0 0 1 1 1v16a1 1 0 0 1 -1 1h-3a1 1 0 0 1 -1 -1v-16a1 1 0 0 1 1 -1z",
    )
}
val PosIcons.Pause: ImageVector get() = _Pause

private val _ScanBarcode: ImageVector by lazy {
    lucideFromPaths(
        "ScanBarcode",
        "M3 7V5a2 2 0 0 1 2-2h2",
        "M17 3h2a2 2 0 0 1 2 2v2",
        "M21 17v2a2 2 0 0 1-2 2h-2",
        "M7 21H5a2 2 0 0 1-2-2v-2",
        "M8 7v10",
        "M12 7v10",
        "M17 7v10",
    )
}
val PosIcons.ScanBarcode: ImageVector get() = _ScanBarcode

private val _CircleAlert: ImageVector by lazy {
    lucideFromPaths(
        "CircleAlert",
        "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0z",
        "M12 8L12 12",
        "M12 16L12.01 16",
    )
}
val PosIcons.CircleAlert: ImageVector get() = _CircleAlert

private val _CircleCheck: ImageVector by lazy {
    lucideFromPaths(
        "CircleCheck",
        "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0z",
        "m9 12 2 2 4-4",
    )
}
val PosIcons.CircleCheck: ImageVector get() = _CircleCheck

private val _ArrowRight: ImageVector by lazy {
    lucideFromPaths(
        "ArrowRight",
        "M5 12h14",
        "m12 5 7 7-7 7",
    )
}
val PosIcons.ArrowRight: ImageVector get() = _ArrowRight

private val _Banknote: ImageVector by lazy {
    lucideFromPaths(
        "Banknote",
        "M4 6h16a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-8a2 2 0 0 1 2 -2z",
        "M10 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z",
        "M6 12h.01M18 12h.01",
    )
}
val PosIcons.Banknote: ImageVector get() = _Banknote

private val _Smartphone: ImageVector by lazy {
    lucideFromPaths(
        "Smartphone",
        "M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-16a2 2 0 0 1 2 -2z",
        "M12 18h.01",
    )
}
val PosIcons.Smartphone: ImageVector get() = _Smartphone

private val _Send: ImageVector by lazy {
    lucideFromPaths(
        "Send",
        "M14.536 21.686a.5.5 0 0 0 .937-.024l6.5-19a.496.496 0 0 0-.635-.635l-19 6.5a.5.5 0 0 0-.024.937l7.93 3.18a2 2 0 0 1 1.112 1.11z",
        "m21.854 2.147-10.94 10.939",
    )
}
val PosIcons.Send: ImageVector get() = _Send

private val _Building2: ImageVector by lazy {
    lucideFromPaths(
        "Building2",
        "M10 12h4",
        "M10 8h4",
        "M14 21v-3a2 2 0 0 0-4 0v3",
        "M6 10H4a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-2",
        "M6 21V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v16",
    )
}
val PosIcons.Building2: ImageVector get() = _Building2

private val _Play: ImageVector by lazy {
    lucideFromPaths(
        "Play",
        "M5 5a2 2 0 0 1 3.008-1.728l11.997 6.998a2 2 0 0 1 .003 3.458l-12 7A2 2 0 0 1 5 19z",
    )
}
val PosIcons.Play: ImageVector get() = _Play

private val _ChevronUp: ImageVector by lazy {
    lucideFromPaths(
        "ChevronUp",
        "m18 15-6-6-6 6",
    )
}
val PosIcons.ChevronUp: ImageVector get() = _ChevronUp

private val _Wallet: ImageVector by lazy {
    lucideFromPaths(
        "Wallet",
        "M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1",
        "M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4",
    )
}
val PosIcons.Wallet: ImageVector get() = _Wallet

private val _ArrowDownToLine: ImageVector by lazy {
    lucideFromPaths(
        "ArrowDownToLine",
        "M12 17V3",
        "m6 11 6 6 6-6",
        "M19 21H5",
    )
}
val PosIcons.ArrowDownToLine: ImageVector get() = _ArrowDownToLine

private val _ArrowUpFromLine: ImageVector by lazy {
    lucideFromPaths(
        "ArrowUpFromLine",
        "m18 9-6-6-6 6",
        "M12 3v14",
        "M5 21h14",
    )
}
val PosIcons.ArrowUpFromLine: ImageVector get() = _ArrowUpFromLine

private val _UserRoundCheck: ImageVector by lazy {
    lucideFromPaths(
        "UserRoundCheck",
        "M2 21a8 8 0 0 1 13.292-6",
        "M5 8a5 5 0 1 0 10 0a5 5 0 1 0 -10 0z",
        "m16 19 2 2 4-4",
    )
}
val PosIcons.UserRoundCheck: ImageVector get() = _UserRoundCheck
