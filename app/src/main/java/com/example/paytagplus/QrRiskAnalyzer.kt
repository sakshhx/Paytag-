package com.example.paytagplus

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

class QrRiskAnalyzer(context: Context) {

    companion object {
        private const val MODEL_NAME = "qr_fraud_model.tflite"
        private const val INPUT_SIZE = 224
        private const val CHANNELS = 3
        private const val BYTES_PER_CHANNEL = 4 // float32
    }

    private val interpreter: Interpreter

    init {
        interpreter = Interpreter(loadModelFile(context))
    }

    // ---------- PUBLIC API ----------
    // Returns probability in range 0.0 – 1.0
    fun analyze(bitmap: Bitmap): Float {
        val inputBuffer = preprocess(bitmap)
        val output = Array(1) { FloatArray(1) }

        interpreter.run(inputBuffer, output)

        // Clamp safety
        return min(1f, max(0f, output[0][0]))
    }

    // ---------- PREPROCESSING ----------
    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)

        val buffer = ByteBuffer.allocateDirect(
            INPUT_SIZE * INPUT_SIZE * CHANNELS * BYTES_PER_CHANNEL
        ).apply {
            order(ByteOrder.nativeOrder())
        }

        for (y in 0 until INPUT_SIZE) {
            for (x in 0 until INPUT_SIZE) {
                val pixel = resized.getPixel(x, y)

                // MobileNetV2 normalization: [-1, 1]
                buffer.putFloat((android.graphics.Color.red(pixel) / 127.5f) - 1f)
                buffer.putFloat((android.graphics.Color.green(pixel) / 127.5f) - 1f)
                buffer.putFloat((android.graphics.Color.blue(pixel) / 127.5f) - 1f)
            }
        }

        return buffer
    }

    // ---------- MODEL LOADER ----------
    private fun loadModelFile(context: Context): ByteBuffer {
        val fileDescriptor = context.assets.openFd(MODEL_NAME)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
}
