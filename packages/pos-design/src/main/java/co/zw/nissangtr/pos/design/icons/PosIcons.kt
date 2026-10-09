package co.zw.nissangtr.pos.design.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Vendored Lucide icons (Blueprint §4.7 / SYS-10).
 * Box: 24 dp, Stroke: 1.75 dp, round caps & joins.
 */
object PosIcons {

    private fun lucide(name: String, block: ImageVector.Builder.() -> Unit): ImageVector {
        return ImageVector.Builder(
            name = "Lucide.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply(block).build()
    }

    private fun ImageVector.Builder.stroked(
        strokeWidth: Float = 1.75f,
        builder: PathBuilder.() -> Unit
    ) = path(
        fill = null,
        stroke = SolidColor(Color.White),
        strokeLineWidth = strokeWidth,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = builder
    )

    val Search: ImageVector by lazy {
        lucide("Search") {
            stroked {
                // Circle at 11, 11, r = 8
                moveTo(19f, 11f)
                arcTo(8f, 8f, 0f, isMoreThanHalf = true, isPositiveArc = true, 3f, 11f)
                arcTo(8f, 8f, 0f, isMoreThanHalf = true, isPositiveArc = true, 19f, 11f)
                close()
                // Handle
                moveTo(21f, 21f)
                lineTo(16.65f, 16.65f)
            }
        }
    }

    val Plus: ImageVector by lazy {
        lucide("Plus") {
            stroked {
                moveTo(12f, 5f)
                lineTo(12f, 19f)
                moveTo(5f, 12f)
                lineTo(19f, 12f)
            }
        }
    }

    val Minus: ImageVector by lazy {
        lucide("Minus") {
            stroked {
                moveTo(5f, 12f)
                lineTo(19f, 12f)
            }
        }
    }

    val Check: ImageVector by lazy {
        lucide("Check") {
            stroked {
                moveTo(20f, 6f)
                lineTo(9f, 17f)
                lineTo(4f, 12f)
            }
        }
    }

    val X: ImageVector by lazy {
        lucide("X") {
            stroked {
                moveTo(18f, 6f)
                lineTo(6f, 18f)
                moveTo(6f, 6f)
                lineTo(18f, 18f)
            }
        }
    }

    val ChevronRight: ImageVector by lazy {
        lucide("ChevronRight") {
            stroked {
                moveTo(9f, 18f)
                lineTo(15f, 12f)
                lineTo(9f, 6f)
            }
        }
    }

    val ChevronDown: ImageVector by lazy {
        lucide("ChevronDown") {
            stroked {
                moveTo(6f, 9f)
                lineTo(12f, 15f)
                lineTo(18f, 9f)
            }
        }
    }

    val ArrowLeft: ImageVector by lazy {
        lucide("ArrowLeft") {
            stroked {
                moveTo(19f, 12f)
                lineTo(5f, 12f)
                moveTo(12f, 19f)
                lineTo(5f, 12f)
                lineTo(12f, 5f)
            }
        }
    }

    val ShoppingCart: ImageVector by lazy {
        lucide("ShoppingCart") {
            stroked {
                moveTo(10f, 21f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = true, isPositiveArc = true, 8f, 21f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = true, isPositiveArc = true, 10f, 21f)
                close()
                moveTo(21f, 21f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = true, isPositiveArc = true, 19f, 21f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = true, isPositiveArc = true, 21f, 21f)
                close()
                moveTo(1f, 1f)
                lineTo(5f, 1f)
                lineTo(7.68f, 14.39f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 9.64f, 16f)
                lineTo(19f, 16f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 20.96f, 14.39f)
                lineTo(23f, 6f)
                lineTo(6f, 6f)
            }
        }
    }

    val Scan: ImageVector by lazy {
        lucide("Scan") {
            stroked {
                moveTo(3f, 7f)
                lineTo(3f, 5f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 5f, 3f)
                lineTo(7f, 3f)

                moveTo(17f, 3f)
                lineTo(19f, 3f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 21f, 5f)
                lineTo(21f, 7f)

                moveTo(21f, 17f)
                lineTo(21f, 19f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19f, 21f)
                lineTo(17f, 21f)

                moveTo(7f, 21f)
                lineTo(5f, 21f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3f, 19f)
                lineTo(3f, 17f)
            }
        }
    }

    val Car: ImageVector by lazy {
        lucide("Car") {
            stroked {
                moveTo(19f, 17f)
                horizontalLineTo(5f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3f, 15f)
                verticalLineTo(10f)
                lineTo(5f, 5f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 7f, 4f)
                horizontalLineTo(17f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19f, 5f)
                lineTo(21f, 10f)
                verticalLineTo(15f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19f, 17f)
                close()
                moveTo(7f, 17f)
                verticalLineTo(19f)
                moveTo(17f, 17f)
                verticalLineTo(19f)
            }
        }
    }

    val Wrench: ImageVector by lazy {
        lucide("Wrench") {
            stroked {
                moveTo(14.7f, 6.3f)
                arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 7.3f, 13.7f)
                lineTo(2.5f, 18.5f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 5.5f, 21.5f)
                lineTo(10.3f, 16.7f)
                arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 17.7f, 9.3f)
                lineTo(15f, 12f)
                lineTo(12f, 9f)
                lineTo(14.7f, 6.3f)
                close()
            }
        }
    }

    val Package: ImageVector by lazy {
        lucide("Package") {
            stroked {
                moveTo(16.5f, 9.4f)
                lineTo(7.55f, 4.24f)
                moveTo(21f, 16f)
                verticalLineTo(8f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 20f, 6.27f)
                lineTo(13f, 2.27f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 11f, 2.27f)
                lineTo(4f, 6.27f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 3f, 8f)
                verticalLineTo(16f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 4f, 17.73f)
                lineTo(11f, 21.73f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 13f, 21.73f)
                lineTo(20f, 17.73f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 21f, 16f)
                close()
                moveTo(3.29f, 7f)
                lineTo(12f, 12f)
                lineTo(20.71f, 7f)
                moveTo(12f, 22f)
                verticalLineTo(12f)
            }
        }
    }

    val Printer: ImageVector by lazy {
        lucide("Printer") {
            stroked {
                moveTo(6f, 9f)
                verticalLineTo(2f)
                horizontalLineTo(18f)
                verticalLineTo(9f)
                moveTo(6f, 18f)
                horizontalLineTo(4f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 16f)
                verticalLineTo(11f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4f, 9f)
                horizontalLineTo(20f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 11f)
                verticalLineTo(16f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 18f)
                horizontalLineTo(18f)
                moveTo(6f, 14f)
                horizontalLineTo(18f)
                verticalLineTo(22f)
                horizontalLineTo(6f)
                close()
            }
        }
    }

    val AlertTriangle: ImageVector by lazy {
        lucide("AlertTriangle") {
            stroked {
                moveTo(10.29f, 3.86f)
                lineTo(1.82f, 18f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 3.55f, 21f)
                horizontalLineTo(20.45f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 22.18f, 18f)
                lineTo(13.71f, 3.86f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 10.29f, 3.86f)
                close()
                moveTo(12f, 9f)
                verticalLineTo(13f)
                moveTo(12f, 17f)
                lineTo(12.01f, 17f)
            }
        }
    }

    val AlertCircle: ImageVector by lazy {
        lucide("AlertCircle") {
            stroked {
                moveTo(22f, 12f)
                arcTo(10f, 10f, 0f, isMoreThanHalf = true, isPositiveArc = true, 2f, 12f)
                arcTo(10f, 10f, 0f, isMoreThanHalf = true, isPositiveArc = true, 22f, 12f)
                close()
                moveTo(12f, 8f)
                verticalLineTo(12f)
                moveTo(12f, 16f)
                lineTo(12.01f, 16f)
            }
        }
    }

    val Info: ImageVector by lazy {
        lucide("Info") {
            stroked {
                moveTo(22f, 12f)
                arcTo(10f, 10f, 0f, isMoreThanHalf = true, isPositiveArc = true, 2f, 12f)
                arcTo(10f, 10f, 0f, isMoreThanHalf = true, isPositiveArc = true, 22f, 12f)
                close()
                moveTo(12f, 16f)
                verticalLineTo(12f)
                moveTo(12f, 8f)
                lineTo(12.01f, 8f)
            }
        }
    }

    val HelpCircle: ImageVector by lazy {
        lucide("HelpCircle") {
            stroked {
                moveTo(22f, 12f)
                arcTo(10f, 10f, 0f, isMoreThanHalf = true, isPositiveArc = true, 2f, 12f)
                arcTo(10f, 10f, 0f, isMoreThanHalf = true, isPositiveArc = true, 22f, 12f)
                close()
                moveTo(9.09f, 9f)
                arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 14.83f, 10.3f)
                curveTo(14.83f, 12f, 12f, 12.5f, 12f, 14f)
                moveTo(12f, 17f)
                lineTo(12.01f, 17f)
            }
        }
    }

    val WifiOff: ImageVector by lazy {
        lucide("WifiOff") {
            stroked {
                moveTo(1f, 1f)
                lineTo(23f, 23f)
                moveTo(16.72f, 11.06f)
                arcTo(10.94f, 10.94f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19f, 12.55f)
                moveTo(5f, 12.55f)
                arcTo(10.94f, 10.94f, 0f, isMoreThanHalf = false, isPositiveArc = true, 9.87f, 9.87f)
                moveTo(12f, 20f)
                lineTo(12.01f, 20f)
            }
        }
    }

    val User: ImageVector by lazy {
        lucide("User") {
            stroked {
                moveTo(19f, 21f)
                verticalLineTo(19f)
                arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = false, 15f, 15f)
                horizontalLineTo(9f)
                arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = false, 5f, 19f)
                verticalLineTo(21f)
                moveTo(16f, 7f)
                arcTo(4f, 4f, 0f, isMoreThanHalf = true, isPositiveArc = true, 8f, 7f)
                arcTo(4f, 4f, 0f, isMoreThanHalf = true, isPositiveArc = true, 16f, 7f)
                close()
            }
        }
    }

    val Lock: ImageVector by lazy {
        lucide("Lock") {
            stroked {
                moveTo(19f, 11f)
                horizontalLineTo(5f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 3f, 13f)
                verticalLineTo(20f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 5f, 22f)
                horizontalLineTo(19f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 21f, 20f)
                verticalLineTo(13f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 19f, 11f)
                close()
                moveTo(7f, 11f)
                verticalLineTo(7f)
                arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 17f, 7f)
                verticalLineTo(11f)
            }
        }
    }

    val Tag: ImageVector by lazy {
        lucide("Tag") {
            stroked {
                moveTo(20.59f, 13.41f)
                lineTo(13.42f, 20.58f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 10.59f, 20.58f)
                lineTo(3f, 13f)
                verticalLineTo(3f)
                horizontalLineTo(13f)
                lineTo(20.59f, 10.59f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20.59f, 13.41f)
                close()
                moveTo(7f, 7f)
                lineTo(7.01f, 7f)
            }
        }
    }

    val RefreshCw: ImageVector by lazy {
        lucide("RefreshCw") {
            stroked {
                moveTo(23f, 4f)
                verticalLineTo(10f)
                horizontalLineTo(17f)
                moveTo(1f, 20f)
                verticalLineTo(14f)
                horizontalLineTo(7f)
                moveTo(3.51f, 9f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20.49f, 8.51f)
                lineTo(23f, 10f)
                moveTo(1f, 14f)
                lineTo(3.51f, 15.49f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20.49f, 15f)
            }
        }
    }

    val Trash2: ImageVector by lazy {
        lucide("Trash2") {
            stroked {
                moveTo(3f, 6f)
                horizontalLineTo(21f)
                moveTo(19f, 6f)
                verticalLineTo(20f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 17f, 22f)
                horizontalLineTo(7f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 5f, 20f)
                verticalLineTo(6f)
                moveTo(8f, 6f)
                verticalLineTo(4f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 10f, 2f)
                horizontalLineTo(14f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 16f, 4f)
                verticalLineTo(6f)
                moveTo(10f, 11f)
                verticalLineTo(17f)
                moveTo(14f, 11f)
                verticalLineTo(17f)
            }
        }
    }
}
