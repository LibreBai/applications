package com.shiguang.yinxiang.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

/**
 * 从 content URI 读取图片的 EXIF 元数据。
 * 耗时 IO，应在 Dispatchers.IO 中调用。
 */
object ExifParser {

    private const val TAG = "ExifParser"

    /** 解析失败返回 null，界面提示无法读取。 */
    fun parse(context: Context, uri: Uri): PhotoExif? {
        val exif = try {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
        } catch (e: IOException) {
            Log.w(TAG, "打开图片失败: $uri", e)
            null
        } ?: return null

        val (date, time, dateTime) = parseDateTime(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))

        var gpsDms: String? = null
        var gpsDecimal: String? = null
        exif.latLong?.let { (lat, lon) ->
            gpsDms = formatLatLng(lat, lon)
            gpsDecimal = String.format(Locale.US, "%.6f, %.6f", lat, lon)
        }

        val device = listOfNotNull(
            exif.getAttribute(ExifInterface.TAG_MAKE)?.trim(),
            exif.getAttribute(ExifInterface.TAG_MODEL)?.trim(),
        ).distinct().joinToString(" ").ifBlank { null }

        return PhotoExif(
            date = date,
            time = time,
            dateTime = dateTime,
            gpsDms = gpsDms,
            gpsDecimal = gpsDecimal,
            device = device,
        )
    }

    // ---- 时间 ----

    // EXIF 标准格式 "yyyy:MM:dd HH:mm:ss"，一般不含时区，按原值展示。
    private const val EXIF_TIME_PATTERN = "yyyy:MM:dd HH:mm:ss"

    /** 解析原始串，返回 (日期, 时间, 日期+时间)；解析失败时原样返回作兜底。 */
    fun parseDateTime(raw: String?): Triple<String?, String?, String?> {
        if (raw.isNullOrBlank()) return Triple(null, null, null)
        val value = raw.trim()
        // 解析频率极低（每次选图一次），局部创建格式器，天然线程安全
        val parsed = try {
            SimpleDateFormat(EXIF_TIME_PATTERN, Locale.US).parse(value)
        } catch (e: ParseException) {
            null
        }
        return if (parsed != null) {
            Triple(
                SimpleDateFormat("yyyy-MM-dd", Locale.US).format(parsed),
                SimpleDateFormat("HH:mm:ss", Locale.US).format(parsed),
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(parsed),
            )
        } else {
            // 非法格式：无法拆分，整串作为「日期 时间」兜底展示
            Triple(null, null, value)
        }
    }

    // ---- 经纬度 ----

    /** 十进制度 → 度分秒，如 25.0761 → "25°04′33.9″"。 */
    fun toDms(value: Double): String {
        val abs = abs(value)
        val deg = abs.toInt()
        val minFull = (abs - deg) * 60
        val min = minFull.toInt()
        val sec = (minFull - min) * 60
        return String.format(Locale.US, "%d°%02d′%04.1f″", deg, min, sec)
    }

    /** 度分秒带半球，如 "N25°04′33.9″ E121°31′45.2″"。 */
    fun formatLatLng(lat: Double, lon: Double): String {
        val latStr = toDms(lat) + if (lat >= 0) "N" else "S"
        val lonStr = toDms(lon) + if (lon >= 0) "E" else "W"
        return "$latStr $lonStr"
    }
}
