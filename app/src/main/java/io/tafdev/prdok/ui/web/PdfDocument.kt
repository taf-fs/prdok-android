package io.tafdev.prdok.ui.web

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import java.io.Closeable
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * An open PDF, ready to be drawn page by page.
 *
 * [PdfRenderer] is Android's own PDF engine, and a spare one: it turns a page into a bitmap
 * and nothing else. It also insists on one open page at a time, so [render] is serialised by
 * a lock; the shapes of every page are read up front instead, while nothing else is running.
 */
class PdfDocument(file: File) : Closeable {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)
    private val lock = Mutex()

    /** Width divided by height for each page, so the list can lay out before anything is drawn. */
    val pageRatios: List<Float> = List(renderer.pageCount) { index ->
        renderer.openPage(index).use { it.width.toFloat() / it.height }
    }

    /** Null if the document was closed first: a page and the screen can go at the same moment. */
    suspend fun render(index: Int, widthPx: Int): Bitmap? = lock.withLock {
        withContext(Dispatchers.Default) {
            runCatching {
                renderer.openPage(index).use { page ->
                    val bitmap = createBitmap(widthPx, (widthPx / pageRatios[index]).roundToInt())
                    // Pages draw as transparent where the paper is, which reads as black.
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }.getOrNull()
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        descriptor.close()
    }
}
