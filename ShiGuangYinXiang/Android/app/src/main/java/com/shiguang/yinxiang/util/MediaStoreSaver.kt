package com.shiguang.yinxiang.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 把合成好的照片保存到系统相册的「时光印象」目录。
 * - API 29+：MediaStore RELATIVE_PATH，零权限。
 * - API 26-28：需先申请 WRITE_EXTERNAL_STORAGE，写公共 Pictures 再注册进媒体库。
 * 耗时 IO，应在 Dispatchers.IO 中调用。
 */
object MediaStoreSaver {

    private const val ALBUM_NAME = "时光印象"

    /** 保存失败时返回 failure（如未授权、磁盘满）。 */
    fun save(context: Context, bitmap: Bitmap): Result<Uri> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToMediaStore(context, bitmap)
        } else {
            saveLegacy(context, bitmap)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToMediaStore(context: Context, bitmap: Bitmap): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/" + ALBUM_NAME,
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("无法创建相册条目")

        try {
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) {
                    throw IOException("图片编码失败")
                }
            } ?: throw IOException("无法打开输出流")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null,
            )
            return uri
        } catch (e: Exception) {
            // 清理半成品，避免相册出现坏图
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(context: Context, bitmap: Bitmap): Uri {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            ALBUM_NAME,
        )
        if (!dir.exists() && !dir.mkdirs()) throw IOException("无法创建目录")
        val file = File(dir, "IMG_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) {
                throw IOException("图片编码失败")
            }
        }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DATA, file.absolutePath)
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }
        return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("无法注册到媒体库")
    }
}
