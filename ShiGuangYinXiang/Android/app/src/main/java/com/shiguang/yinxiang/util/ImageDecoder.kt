package com.shiguang.yinxiang.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.IOException

/**
 * 把 content URI 解码为正立位图：先采样压缩（防止大图 OOM），再按 EXIF 方向旋转矫正。
 * 耗时 IO，应在 Dispatchers.IO 中调用。
 */
object ImageDecoder {

    private const val TAG = "ImageDecoder"
    private const val MAX_DIM = 2560   // 最长边上限，兼顾清晰度与内存

    /** 解码失败返回 null。 */
    fun decode(context: Context, uri: Uri): Bitmap? {
        val bounds = readBounds(context, uri) ?: return null
        val sample = computeSampleSize(bounds.first, bounds.second, MAX_DIM)

        val src = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeStream(stream, null, opts)
            }
        } catch (e: IOException) {
            android.util.Log.w(TAG, "解码失败: $uri", e)
            null
        } ?: return null

        val rotation = readRotation(context, uri)
        if (rotation == 0) return src
        val rotated = rotate(src, rotation)
        if (rotated !== src) src.recycle()
        return rotated
    }

    /** 读宽高但不加载像素。 */
    private fun readBounds(context: Context, uri: Uri): Pair<Int, Int>? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, opts)
                opts.outWidth to opts.outHeight
            }
        } catch (e: IOException) {
            null
        }
    }

    /** 2 的幂采样系数，使最长边不超过 [maxDim]。 */
    internal fun computeSampleSize(w: Int, h: Int, maxDim: Int): Int {
        var sample = 1
        while (w / (sample * 2) >= maxDim || h / (sample * 2) >= maxDim) {
            sample *= 2
        }
        return sample
    }

    private fun readRotation(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).getRotationDegrees() } ?: 0
        } catch (e: IOException) {
            0
        }
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
