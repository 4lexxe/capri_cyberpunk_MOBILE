package com.capri.companion.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.util.LruCache
import android.view.View
import kotlin.random.Random

class SpriteAtlas(private val context: Context) {
    private val cache = object : LruCache<String, Bitmap>(28 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    @Synchronized fun bitmap(pose: Pose): Bitmap = cache[pose.asset] ?: context.assets.open("sprites/${pose.asset}.png").use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: error("Falta la animación ${pose.asset}")
    }.also { cache.put(pose.asset, it) }
    fun trim() = cache.evictAll()
}

class CapriSpriteView(context: Context, private val atlas: SpriteAtlas) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val source = Rect()
    private val target = RectF()
    private val viewport = Rect()
    private var started = SystemClock.uptimeMillis()
    private var pose = Pose.ARRIVE
    private var nextBlink = started + 4000
    private var lastInteraction = started
    private var forced: Pose? = null
    private var dragging = false
    private var sleeping = false
    private var visibleOnScreen = true
    var compactOverlay = false
    var seated = false
        set(value) { if (field != value) { field = value; play(rest()) } }
    var sleepMinutes = 5
    var amplitude = -1f
    var speaking = false
        set(value) { if (field != value) { field = value; if (value) interact(); invalidate() } }
    var onAnimationFinished: (() -> Unit)? = null

    init { contentDescription = "Capri, compañera animada"; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun performClick(): Boolean { super.performClick(); return true }

    private fun rest() = if (seated) Pose.PERCH else Pose.IDLE
    fun interact() {
        lastInteraction = SystemClock.uptimeMillis()
        if (sleeping) { sleeping = false; play(Pose.WAKE) }
        invalidate()
    }
    fun drag(active: Boolean) {
        dragging = active
        interact()
        play(if (active) Pose.DRAG else if (seated) Pose.PERCH_GLITCH else Pose.GLITCH)
    }
    fun preview(value: Pose?) { if (forced != value) { forced = value; sleeping = false; play(value ?: rest()) } }
    fun cue(value: Pose) { if (forced == null && !dragging && onAnimationFinished == null) { interact(); play(value) } }
    fun leave(done: () -> Unit) {
        onAnimationFinished = done
        play(if (seated) Pose.PERCH_LEAVE else Pose.LEAVE)
    }
    private fun play(value: Pose) { pose = value; started = SystemClock.uptimeMillis(); invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.uptimeMillis()
        if (forced == null && !dragging) {
            if (!pose.loops && now - started >= pose.durationMs) {
                onAnimationFinished?.let { onAnimationFinished = null; it(); return }
                play(rest())
            }
            if (!speaking && sleepMinutes > 0 && now - lastInteraction > sleepMinutes * 60_000L && !sleeping) {
                sleeping = true; play(Pose.SLEEP)
            }
            if (!sleeping && !speaking && pose == rest() && now > nextBlink) {
                play(if (seated) Pose.PERCH_BLINK else Pose.BLINK)
                nextBlink = now + Random.nextLong(3500, 7500)
            }
        }
        val bitmap = atlas.bitmap(pose)
        val cell = bitmap.width / pose.frames
        val crop = viewport.apply { if (compactOverlay && pose.asset.startsWith("perch")) set(64, 52, 280, 384) else set(0, 0, cell, bitmap.height) }
        val scale = minOf(width.toFloat() / crop.width(), height.toFloat() / crop.height())
        val wide = crop.width() * scale
        val tall = crop.height() * scale
        target.set((width - wide) / 2, height - tall, (width + wide) / 2, height.toFloat())
        val frame = pose.frame(now - started)
        source.set(frame * cell + crop.left, crop.top, frame * cell + crop.right, crop.bottom)
        canvas.drawBitmap(bitmap, source, target, paint)
        if (speaking && pose in setOf(Pose.IDLE, Pose.BLINK)) {
            val mouth = atlas.bitmap(Pose.MOUTH)
            val mouthFrame = if (amplitude >= 0) (amplitude * 4).toInt().coerceIn(0, 4) else ((now / 95) % 5).toInt()
            val mouthCell = mouth.width / 5
            source.set(mouthFrame * mouthCell, 0, (mouthFrame + 1) * mouthCell, mouth.height)
            canvas.drawBitmap(mouth, source, target, paint)
        }
        if (isAttachedToWindow && windowVisibility == VISIBLE && visibleOnScreen) postInvalidateDelayed(if (sleeping) 166 else 66)
    }

    fun suspendAnimation(suspend: Boolean) { visibleOnScreen = !suspend; if (!suspend) invalidate() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); if (visibility == VISIBLE) invalidate() }
}
