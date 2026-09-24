package io.tafdev.prdok.ui.setup

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import io.tafdev.prdok.R
import io.tafdev.prdok.ui.common.DripLoadingAnimation

private val BOX_SIZE = 320.dp
private val BOX_RADIUS = 22.dp
private val CORNER_LENGTH = 42.dp
private val CORNER_WIDTH = 6.dp

/** The window sits this far above the centre, making room for the bar at the top. */
private val TOOLBAR_OFFSET = 60.dp

/**
 * Pairs by scanning the QR code from the staff website: the camera feed behind a frosted
 * cover with a rounded window cut out of it, four white corner marks around the window.
 *
 * Every code the camera reads is tried once. A code that turns out not to be a pairing
 * code shows its error a single time and is then ignored while it stays in view, so the
 * dialog doesn't come back the moment it's dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    viewModel: PairingViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasCamera by remember { mutableStateOf(context.hasCameraPermission()) }
    var lastCode by remember { mutableStateOf<String?>(null) }

    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasCamera = it
    }
    // Asked as the scanner opens; if Android has stopped showing the dialog, this returns
    // "denied" straight away and the screen offers the way to Settings instead.
    LaunchedEffect(Unit) {
        if (!hasCamera) requestCamera.launch(Manifest.permission.CAMERA)
    }
    // Back from Settings with access granted: the camera starts without a reopen.
    LifecycleResumeEffect(Unit) {
        hasCamera = context.hasCameraPermission()
        onPauseOrDispose { }
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Box(modifier.fillMaxSize().background(if (hasCamera) Color.Black else MaterialTheme.colorScheme.background)) {
        if (hasCamera) {
            QrCameraPreview(
                onCode = { code ->
                    if (code != lastCode && !uiState.isLoading && uiState.error == null) {
                        lastCode = code
                        viewModel.pairWithQr(code)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            ScannerOverlay(isDark, Modifier.fillMaxSize())
        } else {
            CameraNeeded(Modifier.align(Alignment.Center))
        }

        if (uiState.isLoading) ConnectingOverlay()

        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.qr_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            actions = {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.browser_close),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
            },
            // The frosted cover shows through, so the bar has no fill of its own.
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
        )
    }

    uiState.error?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            text = { Text(pairingErrorMessage(error)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.ok)) }
            },
        )
    }
}

/**
 * The camera feed, with ML Kit reading QR codes out of every frame. The controller follows the
 * screen's lifecycle on its own: the camera stops when the app goes to the background and
 * restarts when it returns.
 */
@Composable
private fun QrCameraPreview(onCode: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // The analyzer is set up once, but always calls the newest onCode, which sees the current state.
    val currentOnCode by rememberUpdatedState(onCode)
    val controller = remember {
        LifecycleCameraController(context).apply {
            // Only frames for analysis; no photos are taken, so the capture pipeline stays off.
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
        )
        val mainThread = ContextCompat.getMainExecutor(context)
        controller.setImageAnalysisAnalyzer(
            mainThread,
            MlKitAnalyzer(listOf(scanner), ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL, mainThread) { result ->
                result.getValue(scanner)?.firstNotNullOfOrNull { it.rawValue }?.let(currentOnCode)
            },
        )
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.unbind()
            controller.clearImageAnalysisAnalyzer()
            scanner.close()
        }
    }

    AndroidView(
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                this.controller = controller
            }
        },
        modifier = modifier,
    )
}

/**
 * Everything drawn over the camera. The cover is painted in its own offscreen layer so the window
 * can be erased out of it (BlendMode.Clear), letting the camera show through only there.
 */
@Composable
private fun ScannerOverlay(isDark: Boolean, modifier: Modifier = Modifier) {
    val cover = MaterialTheme.colorScheme.background.copy(alpha = 0.7f)
    val edge = (if (isDark) Color.Black else Color.White).copy(alpha = 0.8f)

    BoxWithConstraints(modifier) {
        // Narrow phones get a smaller window rather than one that runs off the edges.
        val boxSize = min(BOX_SIZE, maxWidth - 32.dp)

        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            drawRect(cover)
            // Darker (or lighter) towards the edges, clear around the middle.
            drawRect(
                Brush.radialGradient(
                    0.16f to Color.Transparent,
                    1f to edge,
                    center = center,
                    radius = 600.dp.toPx(),
                ),
            )

            val side = boxSize.toPx()
            val window = Rect(
                offset = Offset((size.width - side) / 2, (size.height - side) / 2 - TOOLBAR_OFFSET.toPx() / 2),
                size = Size(side, side),
            )
            drawRoundRect(
                color = Color.Black,
                topLeft = window.topLeft,
                size = window.size,
                cornerRadius = CornerRadius(BOX_RADIUS.toPx()),
                blendMode = BlendMode.Clear,
            )
            drawPath(
                path = scannerCorners(window, BOX_RADIUS.toPx(), CORNER_LENGTH.toPx()),
                color = Color.White,
                style = Stroke(width = CORNER_WIDTH.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        Text(
            text = stringResource(R.string.qr_instructions),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = -(boxSize / 2 + TOOLBAR_OFFSET))
                .padding(horizontal = 16.dp),
        )
    }
}

/**
 * Four L-shaped marks around [box]: each is the rounded corner's arc plus a straight leg along
 * both edges, [length] long in total measured from the corner.
 *
 * Angles are in degrees, clockwise from three o'clock (y grows downwards on screen), so the top of
 * a circle is 270° and a negative sweep runs anticlockwise.
 */
private fun scannerCorners(box: Rect, radius: Float, length: Float): Path {
    val r = radius.coerceIn(0f, box.minDimension / 2)
    val leg = (length - r).coerceAtLeast(0f)
    fun arcAround(x: Float, y: Float) = Rect(center = Offset(x, y), radius = r)

    return Path().apply {
        // Top-left: in along the top edge, round the corner, down the left edge.
        moveTo(box.left + r + leg, box.top)
        lineTo(box.left + r, box.top)
        arcTo(arcAround(box.left + r, box.top + r), 270f, -90f, false)
        lineTo(box.left, box.top + r + leg)

        // Top-right
        moveTo(box.right - r - leg, box.top)
        lineTo(box.right - r, box.top)
        arcTo(arcAround(box.right - r, box.top + r), 270f, 90f, false)
        lineTo(box.right, box.top + r + leg)

        // Bottom-right
        moveTo(box.right, box.bottom - r - leg)
        lineTo(box.right, box.bottom - r)
        arcTo(arcAround(box.right - r, box.bottom - r), 0f, 90f, false)
        lineTo(box.right - r - leg, box.bottom)

        // Bottom-left
        moveTo(box.left, box.bottom - r - leg)
        lineTo(box.left, box.bottom - r)
        arcTo(arcAround(box.left + r, box.bottom - r), 180f, -90f, false)
        lineTo(box.left + r + leg, box.bottom)
    }
}

/** A dimmed screen with the drip mark on a card while the pairing runs. */
@Composable
private fun ConnectingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .padding(30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DripLoadingAnimation()
            Text(stringResource(R.string.qr_connecting))
        }
    }
}

/** Shown in place of the camera while access to it is refused. */
@Composable
private fun CameraNeeded(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.qr_camera_needed),
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = context::openAppSettings) { Text(stringResource(R.string.open_settings)) }
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** The app's own page in the system settings, where its permissions are switched. */
private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Every Android build has this screen; nothing sensible to fall back to if one doesn't.
    }
}
