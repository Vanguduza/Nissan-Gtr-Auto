package co.zw.nissangtr.bridges.escpos

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.provider.Settings
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import kotlin.math.ceil

/**
 * A4 / desktop printing through Android Print Framework. Installed Mopria or vendor PrintService
 * plugins own printer discovery and proprietary transport; the POS only supplies the document.
 */
interface DocumentPrinterBridge {
    fun attachActivity(activity: Activity)
    fun detachActivity()
    fun printTextDocument(jobName: String, lines: List<String>)
    fun openPrintServiceSettings()
}

class AndroidDocumentPrinterBridge(context: Context) : DocumentPrinterBridge {
    private val appContext = context.applicationContext
    private var activityRef: WeakReference<Activity>? = null

    override fun attachActivity(activity: Activity) { activityRef = WeakReference(activity) }
    override fun detachActivity() { activityRef = null }

    override fun printTextDocument(jobName: String, lines: List<String>) {
        require(lines.isNotEmpty()) { "document lines required" }
        val activity = activityRef?.get() ?: error("printer activity not attached")
        val manager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val attrs = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build()
        manager.print(jobName, TextA4Adapter(activity, jobName, lines), attrs)
    }

    override fun openPrintServiceSettings() {
        val activity = activityRef?.get() ?: error("printer activity not attached")
        activity.startActivity(Intent(Settings.ACTION_PRINT_SETTINGS))
    }
}

private class TextA4Adapter(
    private val context: Context,
    private val jobName: String,
    private val lines: List<String>,
) : PrintDocumentAdapter() {
    private var attrs: PrintAttributes = PrintAttributes.Builder()
        .setMediaSize(PrintAttributes.MediaSize.ISO_A4).build()
    private val linesPerPage = 55

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal.isCanceled) { callback.onLayoutCancelled(); return }
        attrs = newAttributes
        val pages = ceil(lines.size / linesPerPage.toDouble()).toInt().coerceAtLeast(1)
        callback.onLayoutFinished(
            PrintDocumentInfo.Builder("$jobName.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(pages)
                .build(),
            oldAttributes != newAttributes,
        )
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback,
    ) {
        val doc = PrintedPdfDocument(context, attrs)
        try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f }
            val totalPages = ceil(lines.size / linesPerPage.toDouble()).toInt().coerceAtLeast(1)
            for (pageIndex in 0 until totalPages) {
                if (cancellationSignal.isCanceled) { callback.onWriteCancelled(); return }
                if (!pages.any { pageIndex in it.start..it.end }) continue
                val page = doc.startPage(pageIndex)
                var y = 54f
                val start = pageIndex * linesPerPage
                val end = minOf(lines.size, start + linesPerPage)
                for (i in start until end) {
                    page.canvas.drawText(lines[i].take(120), 48f, y, paint)
                    y += 14f
                }
                doc.finishPage(page)
            }
            FileOutputStream(destination.fileDescriptor).use { doc.writeTo(it) }
            callback.onWriteFinished(pages)
        } catch (t: Throwable) {
            callback.onWriteFailed(t.message)
        } finally {
            doc.close()
        }
    }
}
