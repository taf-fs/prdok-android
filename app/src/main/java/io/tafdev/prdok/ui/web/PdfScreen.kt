package io.tafdev.prdok.ui.web

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.tafdev.prdok.R
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** A PDF the page opened: where to fetch it from, and what to call it. */
data class PdfSource(val url: String, val fileName: String, val cookies: String?)

private sealed class PdfLoad {
    data object Loading : PdfLoad()
    data class Ready(val document: PdfDocument) : PdfLoad()
    data object Failed : PdfLoad()
}

/**
 * Shows a PDF inside the app, the way Safari does on iOS and Android's WebView never has.
 *
 * The file is fetched into the cache first, because [PdfDocument] reads it by seeking around
 * rather than streaming. [onDownload] saves a copy the user keeps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfScreen(
    source: PdfSource,
    httpClient: OkHttpClient,
    onDownload: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Composed after the page's own handlers, so back closes the PDF first.
    BackHandler(onBack = onClose)

    val context = LocalContext.current
    val load by produceState<PdfLoad>(PdfLoad.Loading, source) {
        val document = try {
            val file = File(context.cacheDir, "pdf/${source.url.hashCode()}.pdf")
            if (!file.exists()) httpClient.fetch(source, into = file)
            PdfDocument(file)
        } catch (_: Exception) {
            value = PdfLoad.Failed
            return@produceState
        }
        value = PdfLoad.Ready(document)
        // Holds the document open until the screen goes or the source changes, then closes it.
        awaitDispose { document.close() }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = source.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.browser_close))
                    }
                },
                actions = {
                    IconButton(onClick = onDownload) {
                        Icon(Icons.Default.Download, contentDescription = stringResource(R.string.pdf_download))
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            when (val current = load) {
                PdfLoad.Loading -> CircularProgressIndicator()
                PdfLoad.Failed -> Text(
                    text = stringResource(R.string.pdf_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
                is PdfLoad.Ready -> PdfPages(current.document)
            }
        }
    }
}

@Composable
private fun PdfPages(document: PdfDocument) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(document.pageRatios.size) { index -> PdfPage(document, index) }
    }
}

/**
 * One page, drawn only once the list reaches it and only at the width it will fill. Its shape
 * is known in advance, so it holds its place in the list while still blank.
 */
@Composable
private fun PdfPage(document: PdfDocument, index: Int) {
    var widthPx by remember { mutableIntStateOf(0) }
    val bitmap by produceState<Bitmap?>(null, document, index, widthPx) {
        if (widthPx > 0) value = document.render(index, widthPx)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(document.pageRatios[index])
            .background(Color.White)
            .onSizeChanged { widthPx = it.width },
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
        }
    }
}

/** Fetches [source] with the session cookies the WebView would have sent. */
private suspend fun OkHttpClient.fetch(source: PdfSource, into: File) = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url(source.url)
        .apply { source.cookies?.let { header("Cookie", it) } }
        .build()
    newCall(request).execute().use { response ->
        val body = response.body
        if (!response.isSuccessful || body == null) throw IOException("HTTP ${response.code}")
        into.parentFile?.mkdirs()
        into.outputStream().use { body.byteStream().copyTo(it) }
    }
}
