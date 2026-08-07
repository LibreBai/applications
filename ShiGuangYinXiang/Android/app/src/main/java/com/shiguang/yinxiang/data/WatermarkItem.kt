package com.shiguang.yinxiang.data

/**
 * 解析一张图片后得到的 EXIF 信息。
 * 各字段均可为空——某图没有对应元数据时即为 null，界面就不显示对应勾选项。
 */
data class PhotoExif(
    val date: String?,          // 拍摄日期，如 "2026-08-06"
    val time: String?,          // 拍摄时刻，如 "14:32:00"
    val dateTime: String?,      // 日期+时间，如 "2026-08-06 14:32:00"
    val gpsDms: String?,        // 经纬度（度分秒），如 "N25°04′33.9″ E121°31′45.2″"
    val gpsDecimal: String?,    // 经纬度（十进制度），如 "25.076100, 121.529200"
    val device: String?,        // 设备型号，如 "Xiaomi 15 Pro"
)

/**
 * 可印到照片上的水印项。哪些项可用由 [PhotoExif] 决定（取数非空才可用），
 * 勾选哪些由用户决定——这正是「具体显示什么内容，由用户来决定」。
 */
enum class WatermarkItem(
    val label: String,
    val extract: (PhotoExif) -> String?,
) {
    DATE("日期", { it.date }),
    TIME("时间", { it.time }),
    DATETIME("日期 时间", { it.dateTime }),
    GPS("经纬度", { it.gpsDms }),
    GPS_DECIMAL("经纬度(十进制度)", { it.gpsDecimal }),
    DEVICE("设备型号", { it.device }),
    ;

    /** 这张图里实际可用的水印项（EXIF 中缺失的项不出现，用户无从勾选）。 */
    companion object {
        fun availableFor(exif: PhotoExif): List<WatermarkItem> =
            entries.filter { it.extract(exif) != null }

        fun defaultSelected(): Set<WatermarkItem> = setOf(DATE, TIME, GPS, DEVICE)

        /**
         * 把勾选集合转成要绘制的水印行。
         * 「日期 时间」已涵盖日期与时间，勾选它时自动忽略单独的两项，避免重复。
         */
        fun linesFor(exif: PhotoExif, selected: Set<WatermarkItem>): List<String> {
            val effective = if (DATETIME in selected) selected - DATE - TIME else selected
            return effective.mapNotNull { it.extract(exif) }
        }
    }
}
