package com.example.facesecurityapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

// Demo password: change this to your preferred password.
private const val REGISTRATION_PASSWORD = "1234"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FaceStorage.initialize(applicationContext)

        setContent {
            MaterialTheme {
                FaceSecurityScreen()
            }
        }
    }
}

@Composable
fun FaceSecurityScreen() {
    var showPasswordDialog by remember { mutableStateOf(false) }
    var registrationPassword by remember { mutableStateOf("") }

    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "scanner")

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scannerPulse"
    )

    var showAccessHistory by remember { mutableStateOf(false) }
    var accessLogs by remember { mutableStateOf<List<String>>(emptyList()) }

    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var faceDetected by remember { mutableStateOf(false) }
    var latestEmbedding by remember { mutableStateOf<FloatArray?>(null) }

    var recognitionStatus by remember {
        mutableStateOf("SEARCHING FOR FACE...")
    }

    var circleColor by remember { mutableStateOf(Color.Cyan) }
    var authorizedForCurrentFace by remember {
        mutableStateOf(false)
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            cameraPermissionGranted = granted

            if (!granted) {
                recognitionStatus = "CAMERA PERMISSION DENIED"
            }
        }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.DarkGray)
    ) {
        // Camera preview and face recognition
        if (cameraPermissionGranted) {
            FaceCamera(
                modifier = Modifier.fillMaxSize(),

                onFaceDetected = { detected ->
                    faceDetected = detected

                    if (!detected) {
                        circleColor = Color.Cyan
                        latestEmbedding = null
                        authorizedForCurrentFace = false
                        blinkDetected = false
                        eyesWereClosed = false
                        recognitionStatus = "SEARCHING FOR FACE..."
                    }
                },

                onEmbedding = { embedding ->
                    latestEmbedding = embedding

                    val registered =
                        FaceStorage.getRegisteredEmbedding()

                    recognitionStatus = when {
                        registered == null -> {
                            authorizedForCurrentFace = false
                            "FACE DETECTED - REGISTER FACE"
                        }

                        FaceMatcher.isMatch(
                            embedding1 = registered,
                            embedding2 = embedding
                        ) -> {
                            when {
                                authorizedForCurrentFace -> {
                                    circleColor = Color.Green
                                    "AUTHORIZED"
                                }

                                blinkDetected -> {
                                    blinkDetected = false
                                    authorizedForCurrentFace = true

                                    AccessLog.addLog(
                                        context,
                                        "AUTHORIZED"
                                    )

                                    "AUTHORIZED"
                                }

                                else -> {
                                    "BLINK TO VERIFY"
                                }
                            }
                        }

                        else -> {
                            circleColor = Color.Red
                            authorizedForCurrentFace = false
                            blinkDetected = false

                            if (
                                recognitionStatus !=
                                "ACCESS DENID  • UNKNOWN FACE"
                            ) {
                                AccessLog.addLog(
                                    context,
                                    "UNAUTHORIZED"
                                )
                            }

                            "UNKNOWN FACE - ACCESS DENIED"
                        }
                    }
                }
            )
        }

        // Circular face guide, grid and animated scanner
        if (cameraPermissionGranted) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(
                    size.width / 2f,
                    size.height * 0.42f
                )

                val radius = min(
                    size.width * 0.42f,
                    size.height * 0.38f
                )

                val scannerColor = when {
                    recognitionStatus.contains(
                        "DENIED",
                        ignoreCase = true
                    ) -> Color.Red

                    recognitionStatus.contains(
                        "AUTHORIZED",
                        ignoreCase = true
                    ) -> Color.Green

                    else -> Color.Cyan
                }

                val circlePath = Path().apply {
                    addOval(
                        Rect(
                            center.x - radius,
                            center.y - radius,
                            center.x + radius,
                            center.y + radius
                        )
                    )
                }

                clipPath(circlePath) {
                    val spacing = radius / 3f

                    for (i in -2..2) {
                        val offset = i * spacing

                        drawLine(
                            color = Color.White.copy(alpha = 0.6f),
                            start = Offset(
                                center.x + offset,
                                center.y - radius
                            ),
                            end = Offset(
                                center.x + offset,
                                center.y + radius
                            ),
                            strokeWidth = 1.dp.toPx()
                        )

                        drawLine(
                            color = Color.White.copy(alpha = 0.6f),
                            start = Offset(
                                center.x - radius,
                                center.y + offset
                            ),
                            end = Offset(
                                center.x + radius,
                                center.y + offset
                            ),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }

                val scanY = center.y + radius *
                        kotlin.math.sin(pulse * Math.PI).toFloat()

                drawLine(
                    color = Color.Cyan.copy(alpha = 0.85f),
                    start = Offset(center.x - radius * 0.85f, scanY),
                    end = Offset(center.x + radius * 0.85f, scanY),
                    strokeWidth = 3.dp.toPx()
                )

                // Soft glow
                drawCircle(
                    color = scannerColor.copy(alpha = 0.28f),
                    radius = radius * pulse * 1.08f,
                    center = center,
                    style = Stroke(width = 12.dp.toPx())
                )

                // Main outline
                drawCircle(
                    color = scannerColor,
                    radius = radius * pulse,
                    center = center,
                    style = Stroke(width = 5.dp.toPx())
                )
            }
        }

        // App title
        Text(
            text = "FACEGUARD AI",
            color = Color.Cyan,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 20.dp)
        )

        // Bottom controls and status
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.78f))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = recognitionStatus,
                color = when {
                    recognitionStatus == "AUTHORIZED" ||
                            recognitionStatus == "FACE REGISTERED" ->
                        Color.Green

                    recognitionStatus.contains("DENIED") ||
                            recognitionStatus.contains("UNKNOWN FACE") ||
                            recognitionStatus.contains("INCORRECT") ->
                        Color.Red

                    else -> Color.Yellow
                },
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    registrationPassword = ""
                    showPasswordDialog = true
                },
                enabled = cameraPermissionGranted &&
                        latestEmbedding != null &&
                        faceDetected,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("REGISTER FACE")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    FaceStorage.clear(context)
                    authorizedForCurrentFace = false
                    blinkDetected = false
                    eyesWereClosed = false

                    recognitionStatus = if (faceDetected) {
                        "FACE DETECTED - REGISTER FACE"
                    } else {
                        "SEARCHING FOR FACE..."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("RESET REGISTRATION", color = Color.White)
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    accessLogs = AccessLog.getLogs(context)
                    showAccessHistory = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("ACCESS HISTORY")
            }

            if (showAccessHistory) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "SAVED ACCESS HISTORY",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (accessLogs.isEmpty()) {
                        Text(
                            text = "No access records yet.",
                            color = Color.White
                        )
                    } else {
                        accessLogs.forEach { entry ->
                            Text(
                                text = entry,
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 3.dp)
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        showAccessHistory = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("CLOSE HISTORY")
                }
            }

            OutlinedButton(
                onClick = {
                    AccessLog.clearLogs(context)
                    accessLogs = emptyList()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("CLEAR HISTORY")
            }

            if (!cameraPermissionGranted) {
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        permissionLauncher.launch(
                            Manifest.permission.CAMERA
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ENABLE CAMERA")
                }
            }
        }

        // Registration password dialog
        if (showPasswordDialog) {
            AlertDialog(
                onDismissRequest = {
                    showPasswordDialog = false
                    registrationPassword = ""
                },
                title = {
                    Text("Registration Password")
                },
                text = {
                    OutlinedTextField(
                        value = registrationPassword,
                        onValueChange = {
                            registrationPassword = it
                        },
                        label = {
                            Text("Enter registration password")
                        },
                        singleLine = true,
                        visualTransformation =
                            PasswordVisualTransformation()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (
                                registrationPassword ==
                                REGISTRATION_PASSWORD
                            ) {
                                val embedding = latestEmbedding

                                if (
                                    embedding != null &&
                                    faceDetected
                                ) {
                                    FaceStorage.save(
                                        context,
                                        embedding
                                    )

                                    authorizedForCurrentFace = false
                                    blinkDetected = false
                                    eyesWereClosed = false
                                    circleColor = Color.Cyan

                                    recognitionStatus = "FACE REGISTERED"
                                } else {
                                    recognitionStatus =
                                        "POSITION YOUR FACE IN THE CAMERA"
                                }

                                showPasswordDialog = false
                                registrationPassword = ""
                            } else {
                                recognitionStatus =
                                    "INCORRECT REGISTRATION PASSWORD"

                                registrationPassword = ""
                            }
                        }
                    ) {
                        Text("REGISTER")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showPasswordDialog = false
                            registrationPassword = ""
                        }
                    ) {
                        Text("CANCEL")
                    }
                }
            )
        }
    }
}

@Composable
fun FaceCamera(
    modifier: Modifier = Modifier,
    onFaceDetected: (Boolean) -> Unit,
    onEmbedding: (FloatArray) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val faceDetector = remember {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(
                FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE
            )
            .setClassificationMode(
                FaceDetectorOptions.CLASSIFICATION_MODE_ALL
            )
            .build()

        FaceDetection.getClient(options)
    }

    val faceRecognizer = remember {
        FaceRecognizer(context)
    }

    val cameraExecutor = remember {
        Executors.newSingleThreadExecutor()
    }

    var previewView by remember {
        mutableStateOf<PreviewView?>(null)
    }

    var cameraProvider by remember {
        mutableStateOf<ProcessCameraProvider?>(null)
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()
            faceDetector.close()
            faceRecognizer.close()
        }
    }

    AndroidView(
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode =
                    PreviewView.ImplementationMode.COMPATIBLE
                previewView = this
            }
        },
        modifier = modifier
    )

    LaunchedEffect(previewView, lifecycleOwner) {
        val view = previewView ?: return@LaunchedEffect

        val providerFuture =
            ProcessCameraProvider.getInstance(context)

        providerFuture.addListener(
            {
                try {
                    val provider = providerFuture.get()
                    cameraProvider = provider

                    val preview = Preview.Builder()
                        .build()
                        .also {
                            it.surfaceProvider = view.surfaceProvider
                        }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(
                            ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                        )
                        .build()

                    imageAnalysis.setAnalyzer(cameraExecutor) {
                            imageProxy ->
                        processImage(
                            imageProxy = imageProxy,
                            faceDetector = faceDetector,
                            faceRecognizer = faceRecognizer,
                            onFaceDetected = onFaceDetected,
                            onEmbedding = onEmbedding
                        )
                    }

                    provider.unbindAll()

                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (exception: Exception) {
                    Log.e(
                        "FaceSecurity",
                        "Camera setup failed",
                        exception
                    )
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }
}

private var eyesWereClosed = false
private var blinkDetected = false

@androidx.annotation.OptIn(
    androidx.camera.core.ExperimentalGetImage::class
)
private fun processImage(
    imageProxy: ImageProxy,
    faceDetector: FaceDetector,
    faceRecognizer: FaceRecognizer,
    onFaceDetected: (Boolean) -> Unit,
    onEmbedding: (FloatArray) -> Unit
) {
    val mediaImage = imageProxy.image

    if (mediaImage == null) {
        imageProxy.close()
        return
    }

    val inputImage = InputImage.fromMediaImage(
        mediaImage,
        imageProxy.imageInfo.rotationDegrees
    )

    faceDetector.process(inputImage)
        .addOnSuccessListener { faces ->
            val detected = faces.isNotEmpty()
            onFaceDetected(detected)

            if (!detected) {
                eyesWereClosed = false
                blinkDetected = false
                return@addOnSuccessListener
            }

            try {
                val face = faces.first()

                val leftEyeOpen =
                    face.leftEyeOpenProbability ?: -1f
                val rightEyeOpen =
                    face.rightEyeOpenProbability ?: -1f

                if (
                    leftEyeOpen >= 0f &&
                    rightEyeOpen >= 0f &&
                    leftEyeOpen < 0.3f &&
                    rightEyeOpen < 0.3f
                ) {
                    eyesWereClosed = true
                }

                if (
                    eyesWereClosed &&
                    leftEyeOpen > 0.7f &&
                    rightEyeOpen > 0.7f
                ) {
                    blinkDetected = true
                    eyesWereClosed = false

                    Log.d(
                        "FaceSecurity",
                        "BLINK DETECTED"
                    )
                }

                Log.d(
                    "FaceSecurity",
                    "Left eye: $leftEyeOpen, Right eye: $rightEyeOpen"
                )

                val bitmap = imageProxy.toBitmap()
                val rotation =
                    imageProxy.imageInfo.rotationDegrees

                val matrix = Matrix().apply {
                    postRotate(rotation.toFloat())
                }

                val rotatedBitmap = if (rotation == 0) {
                    bitmap
                } else {
                    Bitmap.createBitmap(
                        bitmap,
                        0,
                        0,
                        bitmap.width,
                        bitmap.height,
                        matrix,
                        true
                    )
                }

                val bounds = face.boundingBox
                val left = max(0, bounds.left)
                val top = max(0, bounds.top)
                val right = min(
                    rotatedBitmap.width,
                    bounds.right
                )
                val bottom = min(
                    rotatedBitmap.height,
                    bounds.bottom
                )

                if (right <= left || bottom <= top) {
                    return@addOnSuccessListener
                }

                val faceBitmap = Bitmap.createBitmap(
                    rotatedBitmap,
                    left,
                    top,
                    right - left,
                    bottom - top
                )

                val embedding =
                    faceRecognizer.getFaceEmbedding(faceBitmap)

                onEmbedding(embedding)

                Log.d(
                    "FaceSecurity",
                    "FACE EMBEDDING SIZE: ${embedding.size}"
                )
            } catch (exception: Exception) {
                Log.e(
                    "FaceSecurity",
                    "Face processing failed",
                    exception
                )
            }
        }
        .addOnFailureListener { exception ->
            Log.e(
                "FaceSecurity",
                "Face detection failed",
                exception
            )
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}