package com.example.facesecurityapp

import android.content.Context
import android.graphics.Bitmap
import androidx.collection.objectFloatMap
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FaceRecognizer(context: Context) {

    private val interpreter: Interpreter

    init {
        val model = context.assets.open("mobile_face_net.tflite").use {
            it.readBytes()
        }

        val modelBuffer = ByteBuffer
            .allocateDirect(model.size)
            .order(ByteOrder.nativeOrder())

        modelBuffer.put(model)
        modelBuffer.rewind()

        interpreter = Interpreter(modelBuffer)
    }

    fun getFaceEmbedding(faceBitmap: Bitmap): FloatArray {

        val resizedBitmap = Bitmap.createScaledBitmap(
            faceBitmap,
            112,
            112,
            true
        )

        val inputBuffer = ByteBuffer
            .allocateDirect(112 * 112 * 3 * 4)
            .order(ByteOrder.nativeOrder())

        for (y in 0 until 112) {
            for (x in 0 until 112) {

                val pixel = resizedBitmap.getPixel(x, y)

                val red = ((pixel shr 16) and 0xFF)
                val green = ((pixel shr 8) and 0xFF)
                val blue = (pixel and 0xFF)

                inputBuffer.putFloat((red - 127.5f) / 127.5f)
                inputBuffer.putFloat((green - 127.5f) / 127.5f)
                inputBuffer.putFloat((blue - 127.5f) / 127.5f)
            }
        }

        val output = Array(1) {
            FloatArray(192)
        }

        inputBuffer.rewind()

        interpreter.run(
            inputBuffer,
            output
        )

        return output[0]
    }

    fun close() {
        interpreter.close()
    }

    fun compareEmbeddings(
        embedding1: FloatArray,
        embedding2: FloatArray
    ): Float {

        var sum = 0f

        for (i in embedding1.indices) {
            val difference = embedding1[i] - embedding2[i]
            sum += difference * difference
        }

        return kotlin.math.sqrt(sum)
    }

    fun isSameFace(
        embedding1: FloatArray,
        embedding2: FloatArray,
        threshold: Float = 1.0f
    ): Boolean {

        val distance = compareEmbeddings(
            embedding1,
            embedding2
        )

        return distance < threshold
    }
} // closes FaceRecognizer class