package com.shiguang.yinxiang

import com.shiguang.yinxiang.data.ExifParser
import com.shiguang.yinxiang.data.PhotoExif
import com.shiguang.yinxiang.data.WatermarkItem
import com.shiguang.yinxiang.util.ImageDecoder
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 纯 JVM 单测：经纬度/时间格式化、水印行组合、采样系数。
 * 不依赖 Android 运行时，./gradlew :app:testDebugUnitTest 可直接跑。
 */
class WatermarkFormatTest {

    // ---- 经纬度 DMS 格式化 ----

    @Test
    fun toDms_formatsPositive() {
        assertEquals("0°00′00.0″", ExifParser.toDms(0.0))
        assertEquals("45°30′00.0″", ExifParser.toDms(45.5))
    }

    @Test
    fun toDms_usesAbsoluteValue() {
        assertEquals("73°07′22.8″", ExifParser.toDms(-73.123))
    }

    @Test
    fun formatLatLng_hemispheres() {
        assertEquals("45°30′00.0″N 73°07′22.8″W", ExifParser.formatLatLng(45.5, -73.123))
    }

    // ---- 时间解析 ----

    @Test
    fun parseDateTime_standard() {
        assertEquals(
            Triple("2026-08-06", "14:32:00", "2026-08-06 14:32:00"),
            ExifParser.parseDateTime("2026:08:06 14:32:00"),
        )
    }

    @Test
    fun parseDateTime_blank_returnsNulls() {
        assertEquals(Triple(null, null, null), ExifParser.parseDateTime(null))
        assertEquals(Triple(null, null, null), ExifParser.parseDateTime("   "))
    }

    @Test
    fun parseDateTime_illegal_fallsBackRaw() {
        assertEquals(Triple(null, null, "badvalue"), ExifParser.parseDateTime("badvalue"))
    }

    // ---- 水印行组合与去重 ----

    private val exif = PhotoExif(
        date = "2026-08-06",
        time = "14:32:00",
        dateTime = "2026-08-06 14:32:00",
        gpsDms = "N25°04′33.9″ E121°31′45.2″",
        gpsDecimal = "25.076100, 121.529200",
        device = "Xiaomi 15 Pro",
    )

    @Test
    fun linesFor_datetime_coversDateAndTime() {
        val selected = setOf(WatermarkItem.DATETIME, WatermarkItem.DATE, WatermarkItem.TIME)
        assertEquals(listOf("2026-08-06 14:32:00"), WatermarkItem.linesFor(exif, selected))
    }

    @Test
    fun linesFor_filtersMissingFields() {
        val emptyExif = PhotoExif(null, null, null, null, null, null)
        assertEquals(emptyList(), WatermarkItem.linesFor(emptyExif, WatermarkItem.defaultSelected()))
    }

    @Test
    fun linesFor_ordersByEnumEntries() {
        val selected = setOf(WatermarkItem.DEVICE, WatermarkItem.DATE, WatermarkItem.GPS)
        assertEquals(
            listOf("2026-08-06", "N25°04′33.9″ E121°31′45.2″", "Xiaomi 15 Pro"),
            WatermarkItem.linesFor(exif, selected),
        )
    }

    // ---- 采样系数 ----

    @Test
    fun computeSampleSize_downscalesHugeImages() {
        assertEquals(2, ImageDecoder.computeSampleSize(10000, 10000, 2560))
        assertEquals(2, ImageDecoder.computeSampleSize(6000, 4000, 2560))
    }

    @Test
    fun computeSampleSize_smallImage_keeps1() {
        assertEquals(1, ImageDecoder.computeSampleSize(100, 100, 2560))
        assertEquals(1, ImageDecoder.computeSampleSize(2560, 1000, 2560))
    }
}
