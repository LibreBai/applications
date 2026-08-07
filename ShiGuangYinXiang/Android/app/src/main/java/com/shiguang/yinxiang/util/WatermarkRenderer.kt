package com.shiguang.yinxiang.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.min

/**
 * 把水印文字画到照片上。
 * - 白色文字 + 黑色投影，任意背景下可读；底部垫半透明黑圆角矩形。
 * - 字号按图片尺寸自适应。
 * - 返回一张新的 ARGB 位图；透明底（PNG）会先填白底，避免存 JPEG 后变黑。
 */
object WatermarkRenderer {

    enum class Position { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    fun render(
        base: Bitmap,
        lines: List<String>,
        position: Position = Position.BOTTOM_LEFT,
    ): Bitmap {
        val out = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(base, 0f, 0f, null)

        if (lines.isEmpty()) return out

        val w = out.width.toFloat()
        val h = out.height.toFloat()

        val textSize = (min(w, h) * 0.04f).coerceIn(20f, 200f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            isFakeBoldText = true
            setShadowLayer(textSize * 0.25f, 0f, 0f, Color.BLACK)
        }

        val fm = paint.fontMetrics
        val lineHeight = fm.descent - fm.ascent
        val lineSpacing = textSize * 0.35f
        val padding = textSize

        val maxTextWidth = lines.maxOf { paint.measureText(it) }
        val bgWidth = (maxTextWidth + padding * 2).coerceAtMost(w - padding * 2)
        val bgHeight = lines.size * lineHeight + (lines.size - 1) * lineSpacing + padding * 2

        val horizontalLeft = when (position) {
            Position.TOP_LEFT, Position.BOTTOM_LEFT -> padding
            Position.TOP_RIGHT, Position.BOTTOM_RIGHT -> w - bgWidth - padding
        }
        val horizontalTop = when (position) {
            Position.TOP_LEFT, Position.TOP_RIGHT -> padding
            Position.BOTTOM_LEFT, Position.BOTTOM_RIGHT -> h - bgHeight - padding
        }

        // 半透明黑底，保证浅色照片上文字也清晰
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(110, 0, 0, 0) }
        canvas.drawRoundRect(
            RectF(horizontalLeft, horizontalTop, horizontalLeft + bgWidth, horizontalTop + bgHeight),
            textSize * 0.3f,
            textSize * 0.3f,
            bgPaint,
        )

        // 逐行画文字；第一行基线 = 内容区顶部 - ascent（ascent 为负）
        var baseline = horizontalTop + padding - fm.ascent
        for (line in lines) {
            canvas.drawText(line, horizontalLeft + padding, baseline, paint)
            baseline += lineHeight + lineSpacing
        }

        return out
    }
}
