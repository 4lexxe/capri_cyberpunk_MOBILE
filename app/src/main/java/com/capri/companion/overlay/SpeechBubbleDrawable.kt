package com.capri.companion.overlay

import android.graphics.*
import android.graphics.drawable.Drawable

class SpeechBubbleDrawable(private val density: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    var tailX = 100f
        set(value) { field = value; invalidateSelf() }
    var pointsUp = false
        set(value) { field = value; invalidateSelf() }
    override fun draw(canvas: Canvas) {
        val d = density
        val left = bounds.left + d; val right = bounds.right - d
        val top = bounds.top + d + if (pointsUp) 9*d else 0f
        val bottom = bounds.bottom - d - if (pointsUp) 0f else 9*d
        val tip = tailX.coerceIn(left + 24*d, maxOf(left + 24*d, right - 24*d))
        path.reset(); path.moveTo(left + 10*d, top)
        if (pointsUp) { path.lineTo(tip - 9*d, top); path.lineTo(tip, top - 9*d); path.lineTo(tip + 6*d, top) }
        path.lineTo(right, top); path.lineTo(right, bottom)
        if (!pointsUp) { path.lineTo(tip + 6*d, bottom); path.lineTo(tip, bottom + 9*d); path.lineTo(tip - 9*d, bottom) }
        path.lineTo(left + 9*d, bottom); path.lineTo(left, bottom - 9*d); path.lineTo(left, top + 10*d); path.close()
        paint.style = Paint.Style.FILL; paint.color = Color.rgb(9, 17, 23); paint.alpha = 245; canvas.drawPath(path, paint)
        canvas.save(); canvas.clipPath(path); paint.color = Color.argb(20, 78, 223, 242); paint.strokeWidth = d / 2
        var y = top + 3*d
        while (y < bottom) { canvas.drawLine(left, y, right, y, paint); y += 4*d }; canvas.restore()
        paint.alpha = 255; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.2f*d; paint.color = Color.rgb(78, 223, 242); canvas.drawPath(path, paint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Android Drawable API") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
