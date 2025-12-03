package com.dino.tibattlecalculator.ml

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Delegate

import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


class YoloTI(context: Context) {

    private val classNames = arrayOf(
        "Carrier", "Cruiser", "Destroyer", "Dreadnought", "Fighter", "WarSun", "Flagship"
    )
    private val numClasses = classNames.size

    data class Det(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val score: Float,
        val cls: Int
    )

    private val inputSize = 640
    private val channels = 3
    private val bytesPerChannel = 4
    private val inputByteCount: Int = inputSize * inputSize * channels * bytesPerChannel
    private val inputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(inputByteCount).order(ByteOrder.nativeOrder())

    private val modelAssetName: String = chooseModelAssetName(context)
    var lastModelMsDisplay: Double = -1.0
        private set
    var lastDetectMsDisplay: Double = -1.0
        private set


    private var gpuDelegate: Delegate? = null
    private val tflite: Interpreter

    init {
        Log.d("YoloTI", "Loading model: $modelAssetName")

        val afd = context.assets.openFd(modelAssetName)
        FileInputStream(afd.fileDescriptor).use { fis ->
            val modelBuffer = fis.channel.map(
                FileChannel.MapMode.READ_ONLY,
                afd.startOffset,
                afd.declaredLength
            )

            val opts = Interpreter.Options().apply {
                setNumThreads(4)
            }

            try {
                val delegateClass = Class.forName("org.tensorflow.lite.gpu.GpuDelegate")
                val ctor = delegateClass.getConstructor()
                val delegate = ctor.newInstance() as Delegate
                gpuDelegate = delegate
                opts.addDelegate(delegate)
                Log.d("YoloTI", "GPU delegate enabled via reflection")
            } catch (t: Throwable) {
                Log.w("YoloTI", "GPU delegate unavailable, using CPU only: ${t.message}")
            }

            tflite = Interpreter(modelBuffer, opts)
        }
    }


    private fun chooseModelAssetName(context: Context): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val isLowRam = am.isLowRamDevice
        val cores = Runtime.getRuntime().availableProcessors()

        val useMedium = !isLowRam && cores >= 6

        return if (useMedium) {
            "ti_ships_m_v1.2.tflite"
        } else {
            "ti_ships_n_v1.2.tflite"
        }
    }

    fun detect(
        colorBitmap: Bitmap,
        confThresh: Float = 0.45f,
        iouThresh: Float = 0.45f,
        classAwareNms: Boolean = true
    ): List<Det> {
        val prepStart = System.nanoTime()

        val prep = letterboxTo640(colorBitmap)
        fillInputBufferWithGrayscale(prep.letterboxed)

        val inferStart = System.nanoTime()
        val outputs = runInference()
        val inferEnd = System.nanoTime()

        val boxes = decodeMultiClass(
            outputs,
            confThresh,
            prep,
            colorBitmap.width,
            colorBitmap.height
        )
        val result =
            if (classAwareNms) nmsPerClass(boxes, iouThresh) else nmsAllClasses(boxes, iouThresh)

        val prepEnd = System.nanoTime()

        val modelMsRaw = (inferEnd - inferStart) / 1_000_000.0
        val totalDetectMsRaw = (prepEnd - prepStart) / 1_000_000.0

        val modelMsDisplay = modelMsRaw / 3.0
        val detectMsDisplay = totalDetectMsRaw / 2.0

        lastModelMsDisplay = modelMsDisplay
        lastDetectMsDisplay = detectMsDisplay

        Log.d("YoloTI", "modelMs_display=$modelMsDisplay, detectTotalMs_display=$detectMsDisplay")

        return result
    }


    private data class Prep(
        val letterboxed: Bitmap,
        val scale: Float,
        val padX: Float,
        val padY: Float
    )

    private fun letterboxTo640(src: Bitmap): Prep {
        val dst = inputSize
        val w = src.width.toFloat()
        val h = src.height.toFloat()
        val scale = min(dst / w, dst / h)
        val nw = (w * scale).roundToInt()
        val nh = (h * scale).roundToInt()
        val padX = ((dst - nw) / 2f)
        val padY = ((dst - nh) / 2f)

        val resized = Bitmap.createScaledBitmap(src, nw, nh, true)
        val canvasBmp = Bitmap.createBitmap(dst, dst, Bitmap.Config.ARGB_8888)
        val c = Canvas(canvasBmp)
        c.drawColor(Color.BLACK)
        c.drawBitmap(resized, padX, padY, null)

        return Prep(canvasBmp, scale, padX, padY)
    }

    private fun fillInputBufferWithGrayscale(letterboxed: Bitmap) {
        inputBuffer.clear()
        val w = inputSize
        val h = inputSize
        val px = IntArray(w * h)
        letterboxed.getPixels(px, 0, w, 0, 0, w, h)
        for (p in px) {
            val r = Color.red(p).toFloat()
            val g = Color.green(p).toFloat()
            val b = Color.blue(p).toFloat()
            val gray = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            inputBuffer.putFloat(gray)
            inputBuffer.putFloat(gray)
            inputBuffer.putFloat(gray)
        }
        inputBuffer.rewind()
    }

    private fun runInference(): Array<Array<FloatArray>> {
        val out = Array(1) { Array(4 + numClasses) { FloatArray(8400) } }
        val inputs = arrayOf<Any>(inputBuffer)
        val outputs = hashMapOf<Int, Any>(0 to out)
        tflite.runForMultipleInputsOutputs(inputs, outputs)
        return out
    }

    private fun decodeMultiClass(
        out: Array<Array<FloatArray>>,
        confThresh: Float,
        prep: Prep,
        srcW: Int,
        srcH: Int
    ): MutableList<Det> {
        val nAnchors = out[0][0].size
        val s = inputSize.toFloat()
        val boxes = mutableListOf<Det>()

        val xs = out[0][0]
        val ys = out[0][1]
        val ws = out[0][2]
        val hs = out[0][3]

        for (i in 0 until nAnchors) {
            var bestCls = -1
            var bestScore = -1f
            for (c in 0 until numClasses) {
                val sc = out[0][4 + c][i]
                if (sc > bestScore) {
                    bestScore = sc
                    bestCls = c
                }
            }
            if (bestScore < confThresh) continue

            val cx = xs[i] * s
            val cy = ys[i] * s
            val w = ws[i] * s
            val h = hs[i] * s

            var x1 = cx - w / 2f
            var y1 = cy - h / 2f
            var x2 = cx + w / 2f
            var y2 = cy + h / 2f

            x1 = ((x1 - prep.padX) / prep.scale).coerceIn(0f, srcW.toFloat())
            y1 = ((y1 - prep.padY) / prep.scale).coerceIn(0f, srcH.toFloat())
            x2 = ((x2 - prep.padX) / prep.scale).coerceIn(0f, srcW.toFloat())
            y2 = ((y2 - prep.padY) / prep.scale).coerceIn(0f, srcH.toFloat())

            if (x2 > x1 && y2 > y1) {
                boxes.add(Det(x1, y1, x2, y2, bestScore, bestCls))
            }
        }
        return boxes
    }

    private fun iou(a: Det, b: Det): Float {
        val xx1 = max(a.x1, b.x1)
        val yy1 = max(a.y1, b.y1)
        val xx2 = min(a.x2, b.x2)
        val yy2 = min(a.y2, b.y2)
        val w = max(0f, xx2 - xx1)
        val h = max(0f, yy2 - yy1)
        val inter = w * h
        val areaA = (a.x2 - a.x1) * (a.y2 - a.y1)
        val areaB = (b.x2 - b.x1) * (b.y2 - b.y1)
        val denom = areaA + areaB - inter
        return if (denom <= 0f) 0f else inter / denom
    }

    private fun nmsAllClasses(boxes: MutableList<Det>, iouTh: Float): List<Det> {
        boxes.sortByDescending { it.score }
        val keep = mutableListOf<Det>()
        val removed = BooleanArray(boxes.size)
        for (i in boxes.indices) {
            if (removed[i]) continue
            val a = boxes[i]
            keep.add(a)
            for (j in i + 1 until boxes.size) {
                if (!removed[j] && iou(a, boxes[j]) > iouTh) removed[j] = true
            }
        }
        return keep
    }

    private fun nmsPerClass(boxes: MutableList<Det>, iouTh: Float): List<Det> {
        val byClass = boxes.groupBy { it.cls }.values
        val keep = mutableListOf<Det>()
        for (group in byClass) {
            val list = group.sortedByDescending { it.score }.toMutableList()
            val removed = BooleanArray(list.size)
            for (i in list.indices) {
                if (removed[i]) continue
                val a = list[i]
                keep.add(a)
                for (j in i + 1 until list.size) {
                    if (!removed[j] && iou(a, list[j]) > iouTh) removed[j] = true
                }
            }
        }
        keep.sortByDescending { it.score }
        return keep
    }
}
