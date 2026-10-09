package co.zw.nissangtr.catalogapk.domain

import android.os.StatFs
import java.io.File

data class DiskHud(
    val freeBytes: Long,
    val totalBytes: Long,
    val htmlDropped: Long,
    val htmlRetained: Long,
)

object DiskHudReader {
    fun read(filesDir: File, htmlDropped: Long, htmlRetained: Long): DiskHud {
        val stat = StatFs(filesDir.absolutePath)
        val free = stat.availableBlocksLong * stat.blockSizeLong
        val total = stat.blockCountLong * stat.blockSizeLong
        return DiskHud(
            freeBytes = free,
            totalBytes = total,
            htmlDropped = htmlDropped,
            htmlRetained = htmlRetained,
        )
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        return "%.2f GB".format(mb / 1024.0)
    }
}
