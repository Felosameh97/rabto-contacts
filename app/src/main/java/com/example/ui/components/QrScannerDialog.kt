package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.domain.model.ContactRecord
import com.example.util.QrUtils
import com.example.util.VCardUtils
import java.util.concurrent.Executors

@Composable
fun QrScannerDialog(
    onDismiss: () -> Unit,
    onContactScanned: (ContactRecord) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var manualPayload by remember { mutableStateOf("") }
    var parseError by remember { mutableStateOf<String?>(null) }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مسح QR جهة اتصال") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (hasCameraPermission) {
                    CameraQrPreview(
                        onQrDecoded = { text ->
                            val parsed = VCardUtils.parseQrPayload(text)
                            if (parsed != null) {
                                onContactScanned(parsed)
                            } else {
                                parseError = "تمت قراءة كود غير متعرف عليه كجهة اتصال"
                            }
                        }
                    )
                } else {
                    Text(
                        "لم يتم منح صلاحية الكاميرا. يمكنك منحها أو لصق محتوى QR / vCard بالأسفل.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("منح صلاحية الكاميرا")
                    }
                }

                OutlinedTextField(
                    value = manualPayload,
                    onValueChange = { manualPayload = it; parseError = null },
                    label = { Text("أو الصق نص vCard / QR هنا") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("qr_manual_input"),
                )
                if (parseError != null) {
                    Text(parseError.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = VCardUtils.parseQrPayload(manualPayload)
                    if (parsed != null) {
                        onContactScanned(parsed)
                    } else {
                        parseError = "أدخل نص vCard أو رقم هاتف صالح"
                    }
                },
                enabled = manualPayload.isNotBlank(),
            ) { Text("استيراد") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        },
    )
}

@Composable
private fun CameraQrPreview(
    onQrDecoded: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var alreadyDecoded by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(executor) { imageProxy ->
                        try {
                            if (!alreadyDecoded && imageProxy.planes.isNotEmpty()) {
                                val buffer = imageProxy.planes[0].buffer
                                val bytes = ByteArray(buffer.remaining())
                                buffer.get(bytes)
                                val decoded = QrUtils.decodeQrFromYuv(bytes, imageProxy.width, imageProxy.height)
                                if (!decoded.isNullOrBlank()) {
                                    alreadyDecoded = true
                                    ContextCompat.getMainExecutor(ctx).execute {
                                        onQrDecoded(decoded)
                                    }
                                }
                            }
                        } finally {
                            imageProxy.close()
                        }
                    }
                    runCatching {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            text = "وجّه الكاميرا نحو QR",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(8.dp),
        )
    }
}
