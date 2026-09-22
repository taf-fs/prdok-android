package io.tafdev.prdok.ui.web

import android.app.DownloadManager
import android.os.Build
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.tafdev.prdok.R
import java.net.URLDecoder

/**
 * Saves a file the page hands over, through the system's download manager.
 *
 * That manager makes its own request in another process and knows nothing about the page that
 * asked for it, so the session cookie has to be copied onto it by hand. Without it the portal
 * answers with its sign-in page and the saved payslip turns out to be HTML.
 */
internal fun WebView.startDownload(
    url: String,
    userAgent: String,
    contentDisposition: String?,
    mimeType: String?,
) = enqueue(url, downloadFileName(url, contentDisposition, mimeType), mimeType, userAgent)

/** Saves a PDF that is already open in the viewer, where the name is settled. */
internal fun WebView.startDownload(source: PdfSource) =
    enqueue(source.url, source.fileName, "application/pdf", settings.userAgentString)

/**
 * The PDF this download refers to, or null when it isn't one. The name decides rather than
 * the content type, which portals like to report as a vague `application/octet-stream`.
 */
internal fun WebView.pdfSource(url: String, contentDisposition: String?, mimeType: String?): PdfSource? {
    val fileName = downloadFileName(url, contentDisposition, mimeType)
    if (!fileName.endsWith(".pdf", ignoreCase = true)) return null
    return PdfSource(url, fileName, sessionCookies(url))
}

private fun WebView.enqueue(url: String, fileName: String, mimeType: String?, userAgent: String) {
    val downloads = context.getSystemService<DownloadManager>()
    // A blob: or data: URL is content the page built in memory; there is nothing to fetch.
    if (downloads == null || !URLUtil.isNetworkUrl(url)) {
        Toast.makeText(context, R.string.download_failed, Toast.LENGTH_LONG).show()
        return
    }

    // Naming the destination can fail as readily as the download itself: unmounted storage,
    // no download manager on the device.
    try {
        val request = DownloadManager.Request(url.toUri()).apply {
            setTitle(fileName)
            setMimeType(mimeType)
            sessionCookies(url)?.let { addRequestHeader("Cookie", it) }
            addRequestHeader("User-Agent", userAgent)
            this@enqueue.url?.let { addRequestHeader("Referer", it) }
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            } else {
                // The shared Downloads folder needs a storage permission before Android 10.
                setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            }
        }
        downloads.enqueue(request)
        Toast.makeText(context, context.getString(R.string.download_started, fileName), Toast.LENGTH_SHORT).show()
    } catch (_: RuntimeException) {
        Toast.makeText(context, R.string.download_failed, Toast.LENGTH_LONG).show()
    }
}

/**
 * The cookie jar this WebView drinks from. Ebony runs in a profile of its own, and
 * [CookieManager.getInstance] only ever returns the default profile's jar.
 */
internal fun WebView.sessionCookies(url: String): String? =
    if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
        WebViewCompat.getProfile(this).cookieManager.getCookie(url)
    } else {
        CookieManager.getInstance().getCookie(url)
    }

/**
 * The name to save under, from the best of three sources.
 *
 * Not `URLUtil.guessFileName`: its header regex matches only `attachment;` followed by the
 * name and nothing else, and never `filename*`, the encoded form needed for diacritics.
 * Android 15 replaced it, but only for apps running on 15 and newer. Whenever the regex
 * misses it falls back to the last segment of the address — the PHP script, here.
 */
private fun downloadFileName(url: String, contentDisposition: String?, mimeType: String?): String {
    contentDisposition?.let(::fileNameFromHeader)?.let { return it }
    fileNameFromQuery(url)?.let { return it }
    val segment = url.toUri().lastPathSegment?.asFileName() ?: "downloadfile"
    return segment.withExtensionFor(mimeType)
}

private val DISPOSITION_PARAMETER = Regex("""([\w*]+)\s*=\s*(?:"([^"]*)"|([^;]*))""")

/**
 * The name a `Content-Disposition` header suggests. `inline` counts as readily as
 * `attachment`: the portal shows a PDF rather than offering it, and it still lands here.
 */
internal fun fileNameFromHeader(header: String): String? {
    var plain: String? = null
    var encoded: String? = null
    for (match in DISPOSITION_PARAMETER.findAll(header.substringAfter(';', ""))) {
        val value = match.groupValues[2].ifEmpty { match.groupValues[3] }.trim()
        when (match.groupValues[1].lowercase()) {
            "filename" -> plain = value.repairLatin1()
            "filename*" -> encoded = decodeExtended(value)
        }
    }
    // filename* wins where both are present: it is the one that can carry diacritics.
    return (encoded ?: plain)?.asFileName()
}

/** Decodes RFC 5987's `charset'language'text`, as in `filename*=UTF-8''v%C3%BDplatnice.pdf`. */
private fun decodeExtended(value: String): String? {
    val parts = value.split('\'')
    if (parts.size < 3) return null
    // A plus sign stands for itself here, unlike in a form field, so hide it from the decoder.
    val text = parts.drop(2).joinToString("'").replace("+", "%2B")
    return runCatching { URLDecoder.decode(text, parts[0].ifEmpty { "UTF-8" }) }.getOrNull()
}

/**
 * Headers are Latin-1 by the book, so a UTF-8 name sent without the encoded form arrives as
 * mojibake: "výplatnice" as "vÃ½platnice". Reading the bytes back as UTF-8 repairs it, and
 * leaves the original alone when they turn out not to have been UTF-8 after all.
 */
private fun String.repairLatin1(): String {
    if (all { it.code < 0x80 } || any { it.code > 0xFF }) return this
    val repaired = String(toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
    return if (repaired.contains('�')) this else repaired
}

/** Anything in the query string that looks like a file name: `?soubor=vyplatnice.pdf`. */
private fun fileNameFromQuery(url: String): String? {
    val uri = url.toUri()
    return runCatching { uri.queryParameterNames.mapNotNull(uri::getQueryParameter) }
        .getOrDefault(emptyList())
        .map { it.substringAfterLast('/') }
        .firstOrNull { it.matches(FILE_LIKE) }
        ?.asFileName()
}

private val FILE_LIKE = Regex(""".+\.[A-Za-z0-9]{2,5}""")

/** Corrects an extension that doesn't match [mimeType], so `soubory.php` becomes `soubory.pdf`. */
private fun String.withExtensionFor(mimeType: String?): String {
    val wanted = mimeType?.substringBefore(';')?.trim()
        ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        ?: return this
    if (substringAfterLast('.', "").equals(wanted, ignoreCase = true)) return this
    return "${substringBeforeLast('.')}.$wanted"
}

/** The server wrote this name, so a name that is a path must not be allowed to stay one. */
private fun String.asFileName(): String? {
    val name = substringAfterLast('/').substringAfterLast('\\').trim()
    if (name.isEmpty() || name == "." || name == "..") return null
    return name.replace(UNSAFE_IN_NAME, "_")
}

private val UNSAFE_IN_NAME = Regex("""[\\/:*?"<>|\x00-\x1F]""")
