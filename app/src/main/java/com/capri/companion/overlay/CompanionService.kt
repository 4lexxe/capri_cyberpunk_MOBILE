package com.capri.companion.overlay

import android.animation.ValueAnimator
import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import com.capri.companion.CapriApplication
import com.capri.companion.MainActivity
import com.capri.companion.R
import com.capri.companion.avatar.CapriSpriteView
import com.capri.companion.avatar.Pose
import kotlinx.coroutines.*
import kotlin.math.abs

class CompanionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app get() = application as CapriApplication
    private lateinit var manager: WindowManager
    private var sprite: CapriSpriteView? = null
    private var params: WindowManager.LayoutParams? = null
    private var balloon: LinearLayout? = null
    private var balloonText: TextView? = null
    private var balloonParams: WindowManager.LayoutParams? = null
    private var actions: LinearLayout? = null
    private var actionParams: WindowManager.LayoutParams? = null
    private var snap: ValueAnimator? = null
    private var hideBalloon: Job? = null
    private var hideActions: Job? = null
    private var messageShownAt = 0L
    private var lastSpeechId = 0L
    private var menuOpen = false
    private var receiverRegistered = false
    private val touchHandler = Handler(Looper.getMainLooper())
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val off = intent?.action == Intent.ACTION_SCREEN_OFF
            sprite?.suspendAnimation(off)
            if (off) { dismissBalloon(); dismissActions() }
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(WINDOW_SERVICE) as WindowManager
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Compañera Capri", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP || !Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(1, notification())
            if (sprite == null) attach()
        } catch (_: Exception) { app.notice.value = "No se pudo mostrar a Capri. Revisá el permiso de superposición."; stopSelf() }
        return START_NOT_STICKY
    }
    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, CompanionService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Capri está contigo").setContentText("Tocá a Capri para escribir o hablar. Mantené pulsado para ver opciones.")
            .setContentIntent(open).setOngoing(true).setCategory(Notification.CATEGORY_SERVICE)
            .addAction(Notification.Action.Builder(null, "Detener Capri", stop).build()).build()
    }
    private fun window(width: Int, height: Int) = WindowManager.LayoutParams(width, height,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START }
    private fun attach() {
        val character = CapriSpriteView(this, app.atlas).apply {
            compactOverlay = true; seated = app.settings.value.seated
            contentDescription = "Capri. Tocá para escribir o hablar; mantené pulsado para opciones; arrastrá para moverla."
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            isClickable = true; isLongClickable = true
            setOnClickListener {
                cue(if (seated) Pose.PERCH_BLINK_GLITCH else Pose.GRIN)
                if (actions == null) showActions(false) else dismissActions()
            }
            setOnLongClickListener { showActions(true); true }
            setOnTouchListener(DragListener())
        }
        sprite = character
        val size = app.settings.value.sizeDp
        params = window(dp(size), spriteHeight(size, character.seated)).apply {
            x = app.store.overlayX.takeUnless { it == Int.MIN_VALUE } ?: screenWidth() - dp(size + 10)
            y = app.store.overlayY.takeUnless { it == Int.MIN_VALUE } ?: screenHeight() / 2
        }
        manager.addView(character, params); app.overlayRunning.value = true
        character.post { clampAndUpdate() }
        lastSpeechId = app.messages.value.lastOrNull { it.role == "assistant" }?.id ?: 0
        val filter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED) else registerReceiver(screenReceiver, filter)
        receiverRegistered = true
        scope.launch { app.settings.collect { settings ->
            character.seated = settings.seated; character.sleepMinutes = settings.sleepMinutes
            params?.apply { width = dp(settings.sizeDp); height = spriteHeight(settings.sizeDp, settings.seated) }; clampAndUpdate()
        } }
        scope.launch { app.speaking.collect { character.speaking = it; scheduleBalloonDismiss() } }
        scope.launch { app.voice.preparing.collect { scheduleBalloonDismiss() } }
        scope.launch { app.hideOverlayForCapture.collect { hide ->
            val visibility = if (hide) View.INVISIBLE else View.VISIBLE
            character.visibility = visibility; balloon?.visibility = visibility; actions?.visibility = visibility
        } }
        scope.launch { app.amplitude.collect { character.amplitude = it } }
        scope.launch { app.avatarCue.collect { character.cue(if (character.seated && it == Pose.GRIN) Pose.PERCH_GLITCH else it) } }
        scope.launch { app.messages.collect { messages ->
            val last = messages.lastOrNull { it.role == "assistant" }
            if (last != null && last.id != lastSpeechId) {
                lastSpeechId = last.id; character.cue(if (character.seated) Pose.PERCH_BLINK_GLITCH else Pose.GRIN); showBalloon(last.text)
            } else if (messages.isEmpty()) dismissBalloon()
        } }
        // Notices such as "settings saved" stay in Mainframe, never in the speech balloon.
        scope.launch { app.contextual.state.collect { if (menuOpen) showActions(true) } }
        scope.launch {
            var wasActive = false
            app.voiceChat.collect { state ->
                if (state.active || wasActive) showActions(false)
                wasActive = state.active
            }
        }
    }
    private fun showBalloon(message: String) {
        dismissBalloon(); messageShownAt = SystemClock.uptimeMillis()
        val messageView = TextView(this).apply {
            text = SpannableString("CAPRI: $message").apply { setSpan(ForegroundColorSpan(Color.rgb(255, 49, 90)), 0, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            setTextColor(Color.rgb(116, 234, 248)); textSize = 12.5f; typeface = resources.getFont(R.font.share_tech_mono)
            maxLines = 5; ellipsize = TextUtils.TruncateAt.END; setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(dp(12), dp(10), dp(2), dp(19))
            contentDescription = "Capri dice: $message. Tocá para abrir la conversación."
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setOnClickListener { openChat(false) }
        }
        val text = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = SpeechBubbleDrawable(resources.displayMetrics.density)
            addView(messageView, LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(this@CompanionService).apply {
                this.text = "×"; textSize = 19f; setTextColor(Color.rgb(116,234,248)); gravity = Gravity.CENTER
                contentDescription = "Cerrar mensaje de Capri"; setOnClickListener { dismissBalloon() }
            }, LinearLayout.LayoutParams(dp(36), dp(40)))
        }
        balloonText = messageView
        balloon = text; balloonParams = window(minOf(dp(264), screenWidth() - dp(20)), WindowManager.LayoutParams.WRAP_CONTENT)
        manager.addView(text, balloonParams); text.post { positionPanels() }; scheduleBalloonDismiss()
    }
    private fun scheduleBalloonDismiss() {
        hideBalloon?.cancel()
        if (balloon == null || app.speaking.value || app.voice.preparing.value) return
        val remaining = (OverlayTiming.readingMs(balloonText?.text?.length ?: 0) - (SystemClock.uptimeMillis() - messageShownAt)).coerceAtLeast(2500)
        hideBalloon = scope.launch { delay(remaining); dismissBalloon() }
    }
    private fun dismissBalloon() {
        hideBalloon?.cancel(); hideBalloon = null
        balloon?.let { runCatching { manager.removeView(it) } }; balloon = null; balloonText = null; balloonParams = null
    }
    private fun showActions(expanded: Boolean) {
        dismissActions(); menuOpen = expanded
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val pill = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; background = pillBackground() }
        fun icon(kind: Int, description: String, action: () -> Unit) = object : View(this) {
            private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(212, 225, 235); strokeWidth = dp(2).toFloat(); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
            init { contentDescription = description; isClickable = true; isFocusable = true; setOnClickListener { action() } }
            override fun onDraw(canvas: Canvas) {
                canvas.save(); canvas.translate(width / 2f, height / 2f)
                when (kind) {
                    0 -> { canvas.save(); canvas.rotate(40f); canvas.drawRect(-dp(3).toFloat(), -dp(9).toFloat(), dp(3).toFloat(), dp(5).toFloat(), ink); canvas.drawLine(-dp(3).toFloat(), dp(5).toFloat(), 0f, dp(9).toFloat(), ink); canvas.drawLine(0f, dp(9).toFloat(), dp(3).toFloat(), dp(5).toFloat(), ink); canvas.restore() }
                    1 -> for (i in -2..2) { val h = dp(if (i == 0) 9 else if (abs(i) == 1) 6 else 3).toFloat(); canvas.drawLine(dp(i*4).toFloat(), -h, dp(i*4).toFloat(), h, ink) }
                    else -> { ink.style = Paint.Style.FILL; for (i in -1..1) canvas.drawCircle(dp(i*6).toFloat(), 0f, dp(1).toFloat()+.5f, ink); ink.style = Paint.Style.STROKE }
                }; canvas.restore()
            }
            override fun performClick(): Boolean { super.performClick(); return true }
        }
        pill.addView(icon(0, "Escribir a Capri") { openChat(false) }, LinearLayout.LayoutParams(dp(44), dp(44)))
        pill.addView(icon(1, if (app.voiceChat.value.active) "Cerrar chat de voz" else "Hablar con Capri") {
            com.capri.companion.voice.OverlayVoiceActivity.request(this)
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        pill.addView(icon(2, "Más opciones de Capri") { showActions(!menuOpen) }, LinearLayout.LayoutParams(dp(44), dp(44)))
        if (expanded) {
            val menu = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = pillBackground() }
            fun item(label: String, action: () -> Unit) {
                menu.addView(TextView(this).apply {
                    text = label; setTextColor(Color.rgb(214, 225, 235)); textSize = 12f; typeface = resources.getFont(R.font.share_tech_mono)
                    gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), 0, dp(14), 0); setOnClickListener { dismissActions(); action() }
                }, LinearLayout.LayoutParams(-1, dp(44)))
            }
            item(if (app.settings.value.seated) "Levantarse" else "Sentarse") { app.save(app.settings.value.copy(seated = !app.settings.value.seated)) }
            if (app.voiceChat.value.active) item("Cerrar chat de voz") { com.capri.companion.voice.OverlayVoiceService.stop(this) }
            if (app.speaking.value || app.voice.preparing.value) item("Detener voz") { app.voice.stop() }
            if (com.capri.companion.BuildConfig.MANUAL_CAPTURE || app.contextual.state.value.active) {
                item(if (com.capri.companion.BuildConfig.MANUAL_CAPTURE) "Tomar captura" else "Comentar pantalla ahora") { app.contextual.lookNow() }
                if (app.contextual.state.value.active) item(if (com.capri.companion.BuildConfig.MANUAL_CAPTURE) "Olvidar captura" else "Detener vista") { app.stopScreenContext() }
            }
            item("Ocultar Capri") { sprite?.leave { stopSelf() } }
            column.addView(menu, LinearLayout.LayoutParams(dp(208), -2).apply { bottomMargin = dp(5) })
        }
        if (app.voiceChat.value.active) column.addView(TextView(this).apply {
            text = app.voiceChat.value.partial.ifBlank { app.voiceChat.value.status }
            setTextColor(Color.rgb(116,234,248)); textSize = 10f; gravity = Gravity.CENTER
            typeface = resources.getFont(R.font.share_tech_mono); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            contentDescription = "Estado del chat de voz: $text"
        }, LinearLayout.LayoutParams(dp(208), dp(26)))
        column.addView(pill, LinearLayout.LayoutParams(dp(132), dp(44)))
        actions = column; actionParams = window(dp(if (expanded || app.voiceChat.value.active) 208 else 132), WindowManager.LayoutParams.WRAP_CONTENT)
        manager.addView(column, actionParams); column.post { positionPanels() }
        if (!app.voiceChat.value.active) hideActions = scope.launch { delay(if (expanded) 12000 else 5000); dismissActions() }
    }
    private fun dismissActions() {
        hideActions?.cancel(); hideActions = null; menuOpen = false
        actions?.let { runCatching { manager.removeView(it) } }; actions = null; actionParams = null; positionPanels()
    }
    private fun positionPanels() {
        val character = sprite ?: return; val p = params ?: return
        val center = p.x + p.width / 2
        var top = p.y + dp(4)
        actions?.let { view -> actionParams?.let { layout ->
            view.measure(View.MeasureSpec.makeMeasureSpec(layout.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(screenHeight(), View.MeasureSpec.AT_MOST))
            layout.x = (center - layout.width / 2).coerceIn(dp(4), maxOf(dp(4), screenWidth() - layout.width - dp(4)))
            val fitsAbove = top - view.measuredHeight - dp(3) >= dp(24)
            val fitsBelow = p.y + p.height + dp(4) + view.measuredHeight <= screenHeight() - dp(30)
            val beside = !fitsAbove && !fitsBelow
            if (beside) {
                layout.x = (if (p.x + p.width + dp(6) + layout.width <= screenWidth()) p.x + p.width + dp(6)
                    else p.x - layout.width - dp(6)).coerceIn(dp(4), maxOf(dp(4), screenWidth() - layout.width - dp(4)))
            }
            layout.y = (if (fitsAbove) top - view.measuredHeight - dp(3) else if (beside) p.y else p.y + p.height + dp(4))
                .coerceIn(dp(24), maxOf(dp(24), screenHeight() - view.measuredHeight - dp(30)))
            if (fitsAbove) top = layout.y - dp(3)
            runCatching { manager.updateViewLayout(view, layout) }
        } }
        balloon?.let { view -> balloonParams?.let { layout ->
            view.measure(View.MeasureSpec.makeMeasureSpec(layout.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(dp(220), View.MeasureSpec.AT_MOST))
            val below = top - view.measuredHeight < dp(24)
            (view.background as SpeechBubbleDrawable).pointsUp = below
            balloonText?.setPadding(dp(12), dp(if (below) 19 else 10), dp(2), dp(if (below) 10 else 19))
            layout.x = (center - layout.width / 2).coerceIn(dp(4), maxOf(dp(4), screenWidth() - layout.width - dp(4)))
            val belowTop = actionParams?.takeIf { it.y >= p.y + p.height }?.let { layoutBelow ->
                layoutBelow.y + (actions?.measuredHeight ?: 0) + dp(4)
            } ?: (p.y + character.height - dp(3))
            layout.y = (if (below) belowTop else top - view.measuredHeight).coerceIn(dp(24), maxOf(dp(24), screenHeight() - view.measuredHeight - dp(30)))
            (view.background as SpeechBubbleDrawable).tailX = (center - layout.x).toFloat()
            runCatching { manager.updateViewLayout(view, layout) }
        } }
    }
    private inner class DragListener : View.OnTouchListener {
        private var downX = 0f; private var downY = 0f; private var startX = 0; private var startY = 0
        private var moved = false; private var held = false
        private val slop = ViewConfiguration.get(this@CompanionService).scaledTouchSlop
        private val hold = Runnable { if (!moved) { held = true; sprite?.performLongClick() } }
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val layout = params ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { snap?.cancel(); downX = event.rawX; downY = event.rawY; startX = layout.x; startY = layout.y; moved = false; held = false; sprite?.interact(); touchHandler.postDelayed(hold, ViewConfiguration.getLongPressTimeout().toLong()); return true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    if (!moved && (abs(dx) > slop || abs(dy) > slop)) { moved = true; touchHandler.removeCallbacks(hold); dismissActions(); sprite?.drag(true) }
                    if (moved) { layout.x = startX + dx.toInt(); layout.y = startY + dy.toInt(); clampAndUpdate() }; return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    touchHandler.removeCallbacks(hold)
                    if (moved) { sprite?.drag(false); snapToEdge() } else if (!held && event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                    return true
                }
            }; return false
        }
    }
    private fun snapToEdge() {
        val p = params ?: return; val maxX = (screenWidth() - p.width).coerceAtLeast(0)
        snap = ValueAnimator.ofInt(p.x, if (p.x < maxX / 2) 0 else maxX).apply { duration = 220
            addUpdateListener { p.x = it.animatedValue as Int; clampAndUpdate(); app.store.overlayX = p.x; app.store.overlayY = p.y }; start() }
    }
    private fun clampAndUpdate() {
        val view = sprite ?: return; val p = params ?: return
        p.x = p.x.coerceIn(0, (screenWidth() - p.width).coerceAtLeast(0)); p.y = p.y.coerceIn(dp(24), maxOf(dp(24), screenHeight() - p.height - dp(30)))
        try { manager.updateViewLayout(view, p); positionPanels() } catch (_: Exception) { stopSelf() }
    }
    private fun openChat(microphone: Boolean) {
        dismissActions(); sprite?.interact()
        startActivity(Intent(this, MainActivity::class.java).putExtra("chat", true).putExtra("microphone", microphone).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }
    private fun screenWidth() = if (Build.VERSION.SDK_INT >= 30) manager.currentWindowMetrics.bounds.width() else resources.displayMetrics.widthPixels
    private fun screenHeight() = if (Build.VERSION.SDK_INT >= 30) manager.currentWindowMetrics.bounds.height() else resources.displayMetrics.heightPixels
    private fun spriteHeight(size: Int, seated: Boolean) = dp(if (seated) size * 332 / 216 else size * 8 / 5)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun pillBackground() = GradientDrawable().apply { setColor(Color.rgb(23, 28, 34)); cornerRadius = dp(22).toFloat(); setStroke(dp(1), Color.rgb(59, 72, 80)) }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); sprite?.post { clampAndUpdate() } }
    override fun onDestroy() {
        com.capri.companion.voice.OverlayVoiceService.stop(this)
        app.stopScreenContext(); scope.cancel(); snap?.cancel(); touchHandler.removeCallbacksAndMessages(null)
        if (receiverRegistered) unregisterReceiver(screenReceiver)
        dismissBalloon(); dismissActions(); sprite?.let { runCatching { manager.removeView(it) } }; sprite = null; params = null
        app.overlayRunning.value = false; app.voice.stop(); stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy()
    }
    companion object { const val STOP = "com.capri.companion.STOP"; private const val CHANNEL = "capri.companion" }
}

object OverlayTiming { fun readingMs(characters: Int): Long = (3000L + characters * 45L).coerceIn(6500, 20000) }
