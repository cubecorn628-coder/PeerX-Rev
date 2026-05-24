package com.example.ui.components

import android.Manifest
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerDialog(
    onScanSuccess: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var hasCameraPermission by remember { mutableStateOf(false) }
    var manualHashInput by remember { mutableStateOf("") }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
        shape = RoundedCornerShape(24.dp),
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Tutup", fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Text(
                text = "Tambah Peer Baru",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (hasCameraPermission) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(Color.Black, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                val executor = Executors.newSingleThreadExecutor()
                                cameraProviderFuture.addListener({
                                    try {
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().apply {
                                            setSurfaceProvider(previewView.surfaceProvider)
                                        }

                                        val scanner = BarcodeScanning.getClient(
                                            BarcodeScannerOptions.Builder()
                                                .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE)
                                                .build()
                                        )

                                        val analysis = ImageAnalysis.Builder()
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build().apply {
                                                setAnalyzer(executor) { imageProxy ->
                                                    val mediaImage = imageProxy.image
                                                    if (mediaImage != null) {
                                                        val image = InputImage.fromMediaImage(
                                                            mediaImage,
                                                            imageProxy.imageInfo.rotationDegrees
                                                        )
                                                        scanner.process(image)
                                                            .addOnSuccessListener { barcodes ->
                                                                for (barcode in barcodes) {
                                                                    val rawValue = barcode.rawValue
                                                                    if (rawValue != null && rawValue.length == 8) {
                                                                        onScanSuccess(rawValue)
                                                                        onDismissRequest()
                                                                        break
                                                                    }
                                                                }
                                                            }
                                                            .addOnFailureListener {
                                                                Log.e("QrScanner", "Scanner error", it)
                                                            }
                                                            .addOnCompleteListener {
                                                                imageProxy.close()
                                                            }
                                                    } else {
                                                        imageProxy.close()
                                                    }
                                                }
                                            }

                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            analysis
                                        )
                                    } catch (e: Exception) {
                                        Log.e("QrScanner", "Camera bindings failed", e)
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Text(
                        text = "Izin kamera dibutuhkan untuk scan QR.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                HorizontalDivider()

                // Fallback manual input
                Text(
                    text = "Atau ketik ID HASH manual (8 Karakter)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = manualHashInput,
                        onValueChange = {
                            if (it.length <= 8) {
                                manualHashInput = it.uppercase()
                            }
                        },
                        placeholder = { Text("E.g. HJKLMNP2") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    Button(
                        onClick = {
                            if (manualHashInput.trim().length == 8) {
                                onScanSuccess(manualHashInput.trim())
                                onDismissRequest()
                            }
                        },
                        enabled = manualHashInput.trim().length == 8,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Tambah")
                    }
                }
            }
        }
    )
}
