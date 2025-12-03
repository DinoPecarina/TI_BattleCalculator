package com.dino.tibattlecalculator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.ByteArrayOutputStream
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import kotlin.math.roundToInt
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import com.dino.tibattlecalculator.domain.DetectedShip
import com.dino.tibattlecalculator.domain.PlayerColor
import com.dino.tibattlecalculator.domain.ShipType
import com.dino.tibattlecalculator.ml.YoloTI
import com.dino.tibattlecalculator.util.ColorUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import kotlin.math.min

class ScanActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var recordButton: Button
    private lateinit var infoButton: ImageButton

    private lateinit var scanProgress: android.widget.ProgressBar

    private lateinit var detector: YoloTI
    private var videoCapture: VideoCapture<Recorder>? = null
    private var currentRecording: androidx.camera.video.Recording? = null
    private var isRecording: Boolean = false

    private val uiShipTypes = listOf(
        ShipType.Fighter,
        ShipType.Destroyer,
        ShipType.Cruiser,
        ShipType.Dreadnought,
        ShipType.Carrier,
        ShipType.WarSun,
        ShipType.Flagship
    )

    private data class FleetData(
        val player1Color: PlayerColor,
        val player2Color: PlayerColor,
        val player1Counts: IntArray,
        val player2Counts: IntArray
    )


    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        detector = YoloTI(this)
        supportActionBar?.title = "Scan battle"

        previewView = findViewById(R.id.previewView)
        recordButton = findViewById(R.id.recordButton)
        infoButton = findViewById(R.id.infoButton)
        scanProgress = findViewById(R.id.scanProgress)
        scanProgress.isVisible = false

        recordButton.setBackgroundColor(
            ContextCompat.getColor(this, R.color.scan_start_green)
        )

        infoButton.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.point_camera_at_the_battle))
                .setMessage(
                    "For best detection results:\n\n" +
                            "• Ensure both fleets are fully visible in the frame.\n" +
                            "• Use good lighting with minimal glare.\n" +
                            "• Hold the camera 20–25 cm above the table.\n" +
                            "• Tilt the phone at a 30° angle.\n"
                )

                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }

        recordButton.setOnClickListener {
            if (!isRecording) {
                startRecording()
            } else {
                stopRecording()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        scanProgress.isVisible = false
        recordButton.isEnabled = true
        recordButton.text = "Start scan"
        recordButton.setBackgroundColor(getColor(R.color.scan_start_green))

        if (hasCameraPermission()) {
            startCamera()
        }
    }


    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val qualitySelector = QualitySelector.from(
                Quality.FHD,
                FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
            )

            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .build()

            val videoCaptureUseCase = VideoCapture.withOutput(recorder)
            videoCapture = videoCaptureUseCase

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    videoCaptureUseCase
                )
            } catch (exc: Exception) {
                Toast.makeText(
                    this,
                    "Failed to start camera: ${exc.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startRecording() {
        val videoCapture = this.videoCapture ?: return


        scanProgress.isVisible = false

        val videoFile = File.createTempFile("battle_scan_", ".mp4", cacheDir)
        val outputOptions = FileOutputOptions.Builder(videoFile).build()

        recordButton.text = "Stop scan"
        recordButton.setBackgroundColor(getColor(R.color.scan_stop_red))

        isRecording = true

        currentRecording = videoCapture.output
            .prepareRecording(this, outputOptions)
            .start(ContextCompat.getMainExecutor(this)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                    }
                    is VideoRecordEvent.Finalize -> {
                        isRecording = false
                        recordButton.text = "Start scan"
                        recordButton.setBackgroundColor(getColor(R.color.scan_start_green))

                        if (event.hasError()) {
                            Toast.makeText(
                                this,
                                "Recording error: ${event.error}",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {

                            scanProgress.isVisible = true
                            recordButton.isEnabled = false
                            processRecordedVideo(videoFile)
                        }
                    }
                }
            }
    }


    private fun stopRecording() {
        currentRecording?.stop()
        currentRecording = null

    }

    override fun onDestroy() {
        currentRecording?.close()
        currentRecording = null
        super.onDestroy()
    }

    private fun processRecordedVideo(videoFile: File) {
        Thread {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(videoFile.absolutePath)

                val durationMs = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L

                val middleUs = (durationMs / 2L) * 1000L

                val frameBitmap: Bitmap? = retriever.getFrameAtTime(
                    middleUs,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )

                retriever.release()

                if (frameBitmap == null) {
                    runOnUiThread {
                        // ADDED: reset UI on failure
                        scanProgress.isVisible = false      // ADDED
                        recordButton.isEnabled = true       // ADDED

                        Toast.makeText(this, "Failed to extract frame", Toast.LENGTH_SHORT).show()
                    }
                    return@Thread
                }

                val cropped = centerCropToSquare640(frameBitmap)
                frameBitmap.recycle()

                val detectedShips = detectShips(cropped)
                val fleetData = buildFleetsFromDetections(detectedShips)

                val detectMs = detector.lastDetectMsDisplay
                val modelMs = detector.lastModelMsDisplay
                val fps = if (detectMs > 0.0) 1000.0 / detectMs else 0.0

                runOnUiThread {
                    // ADDED: ALWAYS reset UI once processing is done
                    scanProgress.isVisible = false          // ADDED
                    recordButton.isEnabled = true           // ADDED

                    if (fleetData == null) {
                        val nonUnknownColors = detectedShips
                            .map { it.playerColor }
                            .filter { it != PlayerColor.Unknown }
                            .distinct()

                        val colorCount = nonUnknownColors.size
                        val colorNames = if (colorCount > 0) {
                            nonUnknownColors.joinToString(", ") { it.displayName }
                        } else {
                            "None"
                        }

                        val baseMsg = when (colorCount) {
                            0 -> "Bad scan: no player colors detected. Please try again."
                            1 -> "Bad scan: only one player color detected. Please try again."
                            else -> "Bad scan: $colorCount colors detected. Please try again."
                        }

                        val fullMsg = "$baseMsg\n\nDetected colors: $colorNames"

                        Toast.makeText(this, fullMsg, Toast.LENGTH_LONG).show()

                        // NOTE: we just stay on ScanActivity, with record button enabled again
                        return@runOnUiThread
                    }

                    Toast.makeText(
                        this,
                        "Scan complete. P1: ${fleetData.player1Color.displayName}, " +
                                "P2: ${fleetData.player2Color.displayName}",
                        Toast.LENGTH_SHORT
                    ).show()

                    val annotated = drawDetections(cropped, detectedShips)

                    val byteStream = ByteArrayOutputStream()
                    annotated.compress(Bitmap.CompressFormat.PNG, 100, byteStream)
                    val annotatedBytes = byteStream.toByteArray()

                    val intent = Intent(this, AdjustActivity::class.java).apply {
                        putExtra("PLAYER1_COLOR_NAME", fleetData.player1Color.displayName)
                        putExtra("PLAYER2_COLOR_NAME", fleetData.player2Color.displayName)
                        putExtra("PLAYER1_COUNTS", fleetData.player1Counts)
                        putExtra("PLAYER2_COUNTS", fleetData.player2Counts)
                        putExtra("CROPPED_SAMPLE", annotatedBytes)
                        putExtra("DETECT_LATENCY_MS", detectMs)
                        putExtra("MODEL_LATENCY_MS", modelMs)
                        putExtra("DETECT_FPS", fps)
                    }
                    startActivity(intent)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    // ADDED: reset UI on exception too
                    scanProgress.isVisible = false          // ADDED
                    recordButton.isEnabled = true           // ADDED

                    Toast.makeText(
                        this,
                        "Error processing video: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }.start()
    }

    private fun centerCropToSquare640(src: Bitmap): Bitmap {
        val minDim = min(src.width, src.height)
        val x = (src.width - minDim) / 2
        val y = (src.height - minDim) / 2

        val square = Bitmap.createBitmap(src, x, y, minDim, minDim)
        return if (minDim == 640) {
            square
        } else {
            Bitmap.createScaledBitmap(square, 640, 640, true)
        }
    }

    private fun detectShips(src: Bitmap): List<DetectedShip> {
        val rawDets = detector.detect(src)
        val ships = mutableListOf<DetectedShip>()

        for (d in rawDets) {
            var x1 = d.x1.toInt().coerceIn(0, src.width - 1)
            var y1 = d.y1.toInt().coerceIn(0, src.height - 1)
            var x2 = d.x2.toInt().coerceIn(x1 + 1, src.width)
            var y2 = d.y2.toInt().coerceIn(y1 + 1, src.height)

            val insetX = ((x2 - x1) * 0.10f).toInt()
            val insetY = ((y2 - y1) * 0.10f).toInt()
            x1 = (x1 + insetX).coerceIn(0, src.width - 2)
            y1 = (y1 + insetY).coerceIn(0, src.height - 2)
            x2 = (x2 - insetX).coerceIn(x1 + 1, src.width)
            y2 = (y2 - insetY).coerceIn(y1 + 1, src.height)

            val cropWidth = x2 - x1
            val cropHeight = y2 - y1
            if (cropWidth <= 0 || cropHeight <= 0) continue

            val color: PlayerColor = ColorUtils.guessPlayerColorFromRegion(
                src = src,
                x = x1,
                y = y1,
                width = cropWidth,
                height = cropHeight
            )

            val shipType = ShipType.fromYoloClassId(d.cls)
            val bbox = RectF(d.x1, d.y1, d.x2, d.y2)

            ships.add(
                DetectedShip(
                    shipType = shipType,
                    playerColor = color,
                    confidence = d.score,
                    bbox = bbox
                )
            )
        }

        return ships
    }



    private fun drawDetections(src: Bitmap, detections: List<DetectedShip>): Bitmap {
        val annotated = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(annotated)

        val boxPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.GREEN
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            style = Paint.Style.FILL
            textSize = 24f
            color = Color.GREEN
            isAntiAlias = true
        }

        detections.forEach { det ->
            val r = det.bbox

            canvas.drawRect(r, boxPaint)

            val confidencePercent = (det.confidence * 100.0).roundToInt()
            val label = "${det.shipType.displayName} (${det.playerColor.displayName}) $confidencePercent%"

            val textX = r.left + 4f
            val textY = (r.top - 8f).coerceAtLeast(24f)

            canvas.drawText(label, textX, textY, textPaint)
        }

        return annotated
    }



    private fun buildFleetsFromDetections(detectedShips: List<DetectedShip>): FleetData? {

        val byColor = detectedShips
            .filter { it.playerColor != PlayerColor.Unknown }
            .groupBy { it.playerColor }

        val distinctColors = byColor.keys


        if (distinctColors.size != 2) {
            return null
        }

        val sortedColors = byColor.entries
            .sortedByDescending { it.value.size }
            .map { it.key }

        val p1Color = sortedColors[0]
        val p2Color = sortedColors[1]

        val p1Counts = IntArray(uiShipTypes.size)
        val p2Counts = IntArray(uiShipTypes.size)

        for (ship in detectedShips) {
            val typeIndex = uiShipTypes.indexOf(ship.shipType)
            if (typeIndex == -1) continue

            when (ship.playerColor) {
                p1Color -> p1Counts[typeIndex]++
                p2Color -> p2Counts[typeIndex]++
                else -> { /* ignore extra/unknown colors */ }
            }
        }

        return FleetData(
            player1Color = p1Color,
            player2Color = p2Color,
            player1Counts = p1Counts,
            player2Counts = p2Counts
        )
    }
}
