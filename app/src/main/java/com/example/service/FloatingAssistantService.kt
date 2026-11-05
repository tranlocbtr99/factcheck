package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.FactCheckApplication
import com.example.MainActivity
import com.example.R
import com.example.data.repository.FactCheckRepository
import com.example.engine.AudioSpeechEngine
import com.example.engine.ClaimExtractor
import com.example.engine.FactCheckEngine
import com.example.engine.FactCheckServiceManager
import com.example.engine.OcrProcessor
import com.example.engine.ScreenFrameDetector
import com.example.model.AnalysisPhase
import com.example.model.AssistantStatus
import com.example.model.FactCheckRecord
import com.example.model.FactCheckVerdict
import com.example.model.PrimaryVerdict
import com.example.model.ReferenceSource
import com.example.ui.TransparentProjectionActivity
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.math.abs

class FloatingAssistantService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager
    private lateinit var mediaProjectionManager: MediaProjectionManager

    // UI Overlay Views
    private var floatingRootView: FrameLayout? = null
    private var floatingBubbleView: FrameLayout? = null
    private var quickPanelLayout: LinearLayout? = null
    private var floatingResultCardView: LinearLayout? = null
    private var floatingChatCardView: LinearLayout? = null
    private var statusDotView: View? = null
    private var alertBadgeView: TextView? = null

    // Window Layout Params
    private lateinit var bubbleLayoutParams: WindowManager.LayoutParams

    // MediaProjection & Capture
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var isProjectionPaused: Boolean = false
    private var latestScreenBitmap: Bitmap? = null

    // Audio Analysis & Speech-To-Text
    private var audioSpeechEngine: AudioSpeechEngine? = null

    // Analysis Pipeline
    private val frameDetector = ScreenFrameDetector(0.05f)
    private val ocrProcessor = OcrProcessor()
    private val claimExtractor = ClaimExtractor()
    private val factCheckEngine = FactCheckEngine()
    private lateinit var repository: FactCheckRepository

    // Touch & Drag state for bubble
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var isDragging: Boolean = false
    private val clickThreshold = 12

    // Debounce & deduplication
    private var lastAnalyzedClaim: String? = null
    private var lastAnalysisTimestamp: Long = 0L

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        repository = FactCheckRepository.getInstance(this)

        initAudioSpeechEngine()

        // Bắt đầu Foreground Service an toàn (SpecialUse trước khi có MediaProjection)
        safeStartForegroundNotification(false)

        if (canDrawOverlays()) {
            setupFloatingWindow()
            FactCheckServiceManager.setOverlayActive(true)
        } else {
            FactCheckServiceManager.setOverlayActive(false)
        }

        observeUserEvents()
    }

    private fun initAudioSpeechEngine() {
        audioSpeechEngine = AudioSpeechEngine(
            context = this,
            onSpeechRecognized = { transcribedText, isFinal ->
                serviceScope.launch(Dispatchers.Main) {
                    FactCheckServiceManager.updateLiveAudio(transcribedText)
                    if (isFinal && transcribedText.length >= 10) {
                        processSpokenText(transcribedText)
                    }
                }
            },
            onErrorOccurred = { errorMsg ->
                serviceScope.launch(Dispatchers.Main) {
                    Log("Speech: $errorMsg")
                }
            }
        )
    }

    private fun Log(msg: String) {
        android.util.Log.d("LTM_FactCheck", msg)
    }

    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_PROJECTION -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                val triggerInstantCheck = intent.getBooleanExtra(EXTRA_TRIGGER_INSTANT_CHECK, false)
                if (resultCode != 0 && resultData != null) {
                    initMediaProjection(resultCode, resultData, triggerInstantCheck)
                }
            }
            ACTION_PAUSE -> pauseMonitoring()
            ACTION_RESUME -> resumeMonitoring()
            ACTION_STOP -> stopMonitoring()
            ACTION_INSTANT_CHECK -> executeInstantScreenCheck()
            ACTION_TOGGLE_AUDIO -> toggleAudioListening()
            ACTION_SHOW_RESULT -> {
                val state = FactCheckServiceManager.assistantState.value
                val record = state.latestRecord
                if (record != null) {
                    showOnScreenResultCard(record)
                } else {
                    executeInstantScreenCheck()
                }
            }
            ACTION_DISMISS_ALERT -> {
                FactCheckServiceManager.clearAlert()
                updateBubbleUi()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun safeStartForegroundNotification(isProjectionActive: Boolean) {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pauseIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingAssistantService::class.java).apply { action = ACTION_PAUSE },
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, FloatingAssistantService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (isProjectionActive) {
            "Đang đọc màn hình & nhận diện âm thanh video thời gian thực"
        } else {
            "Trợ lý nổi sẵn sàng. Nhấn để bắt đầu đọc màn hình"
        }

        val notification = NotificationCompat.Builder(this, FactCheckApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("LTM FactCheck AI (Đang giám sát)")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Tạm dừng", pauseIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dừng", stopIntent)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (isProjectionActive) {
                    var fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        fgsType = fgsType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    }
                    startForeground(FactCheckApplication.FOREGROUND_NOTIFICATION_ID, notification, fgsType)
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        startForeground(FactCheckApplication.FOREGROUND_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                    } else {
                        startForeground(FactCheckApplication.FOREGROUND_NOTIFICATION_ID, notification)
                    }
                }
            } else {
                startForeground(FactCheckApplication.FOREGROUND_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingWindow() {
        if (!canDrawOverlays()) return

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val display = windowManager.defaultDisplay
        val size = Point()
        display.getSize(size)
        val screenWidth = size.x

        val density = resources.displayMetrics.density
        val btnSizeDp = FactCheckServiceManager.settings.value.floatingButtonSizeDp
        val btnSizePx = (btnSizeDp * density).toInt()

        bubbleLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - btnSizePx - (16 * density).toInt()
            y = (200 * density).toInt()
        }

        val root = FrameLayout(this)
        floatingRootView = root

        val bubble = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(btnSizePx, btnSizePx)
            background = createLiquidGlassBubbleBackground()
            elevation = 12f * density
        }

        val iconView = ImageView(this).apply {
            val pad = (btnSizePx * 0.22f).toInt()
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setPadding(pad, pad, pad, pad)
            setImageResource(R.drawable.ic_factcheck_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        bubble.addView(iconView)

        val dot = View(this).apply {
            val dotSize = (14 * density).toInt()
            val params = FrameLayout.LayoutParams(dotSize, dotSize).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, (4 * density).toInt(), (4 * density).toInt())
            }
            layoutParams = params
            background = createDotDrawable(Color.GRAY)
        }
        statusDotView = dot
        bubble.addView(dot)

        val alertBadge = TextView(this).apply {
            val badgeSize = (20 * density).toInt()
            val params = FrameLayout.LayoutParams(badgeSize, badgeSize).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(0, (2 * density).toInt(), (2 * density).toInt(), 0)
            }
            layoutParams = params
            setTextColor(Color.WHITE)
            textSize = 10f
            gravity = Gravity.CENTER
            visibility = View.GONE
            background = createDotDrawable(Color.parseColor("#EF4444"))
        }
        alertBadgeView = alertBadge
        bubble.addView(alertBadge)

        floatingBubbleView = bubble
        root.addView(bubble)

        setupQuickPanel(root, density)
        setupOnScreenResultCard(root, density, screenWidth)
        setupOnScreenChatCard(root, density, screenWidth)

        bubble.setOnTouchListener { _, event ->
            handleBubbleTouch(event, screenWidth, btnSizePx)
        }

        try {
            windowManager.addView(root, bubbleLayoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        updateBubbleUi()
    }

    private fun handleBubbleTouch(event: MotionEvent, screenWidth: Int, btnSizePx: Int): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = bubbleLayoutParams.x
                initialY = bubbleLayoutParams.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()

                if (abs(dx) > clickThreshold || abs(dy) > clickThreshold) {
                    isDragging = true
                    bubbleLayoutParams.x = initialX + dx
                    bubbleLayoutParams.y = initialY + dy
                    try {
                        windowManager.updateViewLayout(floatingRootView, bubbleLayoutParams)
                    } catch (_: Exception) {}
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    val state = FactCheckServiceManager.assistantState.value
                    if (floatingResultCardView?.visibility == View.VISIBLE) {
                        floatingResultCardView?.visibility = View.GONE
                    } else if (state.latestRecord != null && (state.status == AssistantStatus.ALERT || state.latestRecord?.isAlerted == true)) {
                        state.latestRecord?.let { showOnScreenResultCard(it) }
                    } else {
                        toggleQuickPanel()
                    }
                } else {
                    val midPoint = screenWidth / 2
                    val targetX = if (bubbleLayoutParams.x + (btnSizePx / 2) < midPoint) {
                        16
                    } else {
                        screenWidth - btnSizePx - 16
                    }
                    animateSnapToEdge(targetX)
                }
                return true
            }
        }
        return false
    }

    private fun animateSnapToEdge(targetX: Int) {
        val startX = bubbleLayoutParams.x
        val handler = Handler(Looper.getMainLooper())
        var step = 0
        val totalSteps = 10
        val runnable = object : Runnable {
            override fun run() {
                step++
                val fraction = step.toFloat() / totalSteps
                bubbleLayoutParams.x = (startX + (targetX - startX) * fraction).toInt()
                try {
                    windowManager.updateViewLayout(floatingRootView, bubbleLayoutParams)
                } catch (_: Exception) {}
                if (step < totalSteps) {
                    handler.postDelayed(this, 12)
                }
            }
        }
        handler.post(runnable)
    }

    private fun setupQuickPanel(root: FrameLayout, density: Float) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (14 * density).toInt()
            setPadding(pad, pad, pad, pad)
            background = createLiquidGlassPanelBackground()
            visibility = View.GONE
            elevation = 16f * density

            val params = FrameLayout.LayoutParams(
                (280 * density).toInt(),
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                setMargins(0, (68 * density).toInt(), 0, 0)
            }
            layoutParams = params
        }

        val header = TextView(this).apply {
            text = "LTM FactCheck AI (Màn hình & Video)"
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, 0, (8 * density).toInt())
            layoutParams = params
        }
        panel.addView(header)

        addPanelItem(panel, "🔍 Đọc & Kiểm chứng ngay màn hình", density) {
            toggleQuickPanel()
            executeInstantScreenCheck()
        }

        val isAudioOn = FactCheckServiceManager.assistantState.value.isAudioTranscriptionActive
        val audioTitle = if (isAudioOn) "🎙️ Tắt nhận diện âm thanh video" else "🎙️ Bật nhận diện âm thanh video (STT)"
        addPanelItem(panel, audioTitle, density) {
            toggleQuickPanel()
            toggleAudioListening()
        }

        addPanelItem(panel, "📋 Xem kết quả gần nhất tại đây", density) {
            toggleQuickPanel()
            val record = FactCheckServiceManager.assistantState.value.latestRecord
            if (record != null) {
                showOnScreenResultCard(record)
            } else {
                executeInstantScreenCheck()
            }
        }

        addPanelItem(panel, "💬 Hỏi AI trực tiếp trên màn hình", density) {
            toggleQuickPanel()
            showOnScreenChatCard()
        }

        addPanelItem(panel, "⏯️ Tạm dừng / Tiếp tục giám sát", density) {
            toggleQuickPanel()
            if (isProjectionPaused) resumeMonitoring() else pauseMonitoring()
        }

        addPanelItem(panel, "📱 Mở ứng dụng LTM FactCheck", density) {
            toggleQuickPanel()
            openMainActivity()
        }

        addPanelItem(panel, "⏹️ Dừng giám sát & Đóng nút nổi", density) {
            toggleQuickPanel()
            stopMonitoring()
            stopSelf()
        }

        quickPanelLayout = panel
        root.addView(panel)
    }

    private fun toggleAudioListening() {
        val currentlyActive = FactCheckServiceManager.assistantState.value.isAudioTranscriptionActive
        if (currentlyActive) {
            audioSpeechEngine?.stopListening()
            FactCheckServiceManager.setAudioActive(false)
            Toast.makeText(this, "Đã tắt nhận diện âm thanh video", Toast.LENGTH_SHORT).show()
        } else {
            audioSpeechEngine?.startListening()
            FactCheckServiceManager.setAudioActive(true)
            Toast.makeText(this, "Đang lắng nghe âm thanh video (TikTok, YouTube, Facebook)...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processSpokenText(spokenText: String) {
        serviceScope.launch {
            val claims = claimExtractor.extractClaims(spokenText)
            if (claims.isNotEmpty()) {
                val topClaim = claims.first()
                if (topClaim.normalizedClaim != lastAnalyzedClaim) {
                    lastAnalyzedClaim = topClaim.normalizedClaim
                    lastAnalysisTimestamp = System.currentTimeMillis()

                    val record = factCheckEngine.verifyClaim(
                        extractedClaim = topClaim.normalizedClaim,
                        sourceApp = "Âm thanh video (${topClaim.detectedAppName})",
                        rawScreenText = "Nhận diện từ lời nói video: \"$spokenText\""
                    )

                    repository.saveRecord(record)
                    FactCheckServiceManager.onNewFactCheckRecord(record)
                    updateBubbleUi()

                    // Hiển thị trực tiếp kết quả nếu phát hiện sai lệch hoặc có yêu cầu
                    showOnScreenResultCard(record)
                    if (record.isAlerted) {
                        triggerAlertNotification(record)
                    }
                }
            }
        }
    }

    private fun setupOnScreenResultCard(root: FrameLayout, density: Float, screenWidth: Int) {
        val cardWidth = (screenWidth * 0.94f).toInt().coerceAtMost((400 * density).toInt())

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * density).toInt()
            setPadding(pad, pad, pad, pad)
            background = createLiquidGlassPanelBackground()
            visibility = View.GONE
            elevation = 20f * density

            val params = FrameLayout.LayoutParams(
                cardWidth,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                setMargins((10 * density).toInt(), (72 * density).toInt(), 0, 0)
            }
            layoutParams = params
        }

        floatingResultCardView = card
        root.addView(card)
    }

    /**
     * Hiển thị kết quả kiểm chứng trực tiếp trên màn hình ứng dụng đang xem
     * Kèm bằng chứng thực chất đã đọc được từ màn hình và âm thanh video
     */
    private fun showOnScreenResultCard(record: FactCheckRecord) {
        val card = floatingResultCardView ?: return
        card.removeAllViews()
        val density = resources.displayMetrics.density

        // 1. Header Row: Primary Verdict Badge + Nút đóng
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, 0, (10 * density).toInt())
            layoutParams = params
        }

        val verdictBadge = TextView(this).apply {
            val primary = record.verdict.primaryVerdict
            text = "● ${primary.titleVi}: ${record.verdict.titleVi.uppercase()}"
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            val padH = (12 * density).toInt()
            val padV = (5 * density).toInt()
            setPadding(padH, padV, padH, padV)

            val badgeColor = when (primary) {
                PrimaryVerdict.DUNG -> Color.parseColor("#10B981")
                PrimaryVerdict.SAI -> Color.parseColor("#EF4444")
                PrimaryVerdict.CHUA_DU_CO_SO -> Color.parseColor("#A855F7")
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f * density
                setColor(badgeColor)
            }
        }
        headerRow.addView(verdictBadge)

        val spacer = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }
        headerRow.addView(spacer)

        val closeBtn = TextView(this).apply {
            text = "✕"
            textSize = 18f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
            isClickable = true
            setOnClickListener { card.visibility = View.GONE }
        }
        headerRow.addView(closeBtn)
        card.addView(headerRow)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (300 * density).toInt()
            )
        }
        val contentLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // 2. Chứng thực nguồn thu thập (Màn hình OCR hay Âm thanh Video)
        val sourceMeta = TextView(this).apply {
            val frames = FactCheckServiceManager.assistantState.value.framesCapturedCount
            text = "📍 ${record.sourceApp} • Khung hình quét: #$frames"
            textSize = 11f
            setTextColor(Color.parseColor("#38BDF8"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        contentLayout.addView(sourceMeta)

        val claimBody = TextView(this).apply {
            text = "\"${record.normalizedClaim}\""
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, (2 * density).toInt(), 0, (8 * density).toInt())
            layoutParams = params
        }
        contentLayout.addView(claimBody)

        // 3. Phân tích cụ thể & Bằng chứng thực chất
        val explanationTv = TextView(this).apply {
            text = "${record.shortExplanation}\n\n${record.detailedExplanation}"
            textSize = 12.5f
            setTextColor(Color.parseColor("#E2E8F0"))
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, 0, (8 * density).toInt())
            layoutParams = params
        }
        contentLayout.addView(explanationTv)

        if (record.evidence.isNotBlank()) {
            val evidenceBox = TextView(this).apply {
                text = "✓ Bằng chứng đối chiếu: ${record.evidence}"
                textSize = 11.5f
                setTextColor(Color.parseColor("#BBF7D0"))
                setPadding((8 * density).toInt(), (6 * density).toInt(), (8 * density).toInt(), (6 * density).toInt())
                background = GradientDrawable().apply {
                    cornerRadius = 8f * density
                    setColor(Color.parseColor("#2614532D"))
                }
                val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                params.setMargins(0, 0, 0, (8 * density).toInt())
                layoutParams = params
            }
            contentLayout.addView(evidenceBox)
        }

        // 4. Bằng chứng thực tế đọc được từ Màn hình / Âm thanh
        if (record.rawScreenText.isNotBlank()) {
            val rawSnippetBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((8 * density).toInt(), (6 * density).toInt(), (8 * density).toInt(), (6 * density).toInt())
                background = GradientDrawable().apply {
                    cornerRadius = 8f * density
                    setColor(Color.parseColor("#1A38BDF8"))
                    setStroke(1, Color.parseColor("#3338BDF8"))
                }
                val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                params.setMargins(0, 0, 0, (8 * density).toInt())
                layoutParams = params
            }

            val snippetHeader = TextView(this).apply {
                text = "DỮ LIỆU ĐỌC ĐƯỢC TỪ MÀN HÌNH:"
                textSize = 10f
                setTextColor(Color.parseColor("#38BDF8"))
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            rawSnippetBox.addView(snippetHeader)

            val snippetText = TextView(this).apply {
                text = record.rawScreenText.take(160) + if (record.rawScreenText.length > 160) "..." else ""
                textSize = 11f
                setTextColor(Color.parseColor("#94A3B8"))
            }
            rawSnippetBox.addView(snippetText)
            contentLayout.addView(rawSnippetBox)
        }

        // 5. Danh sách Nguồn kiểm chứng mở được trực tiếp
        if (record.references.isNotEmpty()) {
            val refTitle = TextView(this).apply {
                text = "NGUỒN THAM KHẢO XÁC MINH:"
                textSize = 11f
                setTextColor(Color.parseColor("#38BDF8"))
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            contentLayout.addView(refTitle)

            record.references.forEach { ref ->
                val refItem = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding((8 * density).toInt(), (6 * density).toInt(), (8 * density).toInt(), (6 * density).toInt())
                    background = GradientDrawable().apply {
                        cornerRadius = 8f * density
                        setColor(Color.parseColor("#331E293B"))
                        setStroke(1, Color.parseColor("#4D38BDF8"))
                    }
                    val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    params.setMargins(0, (4 * density).toInt(), 0, (6 * density).toInt())
                    layoutParams = params
                }

                val refHeader = TextView(this).apply {
                    text = "🌐 ${ref.publisher} (${ref.publishDate})"
                    textSize = 11.5f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.parseColor("#38BDF8"))
                }
                refItem.addView(refHeader)

                val refSnippet = TextView(this).apply {
                    text = "\"${ref.supportingSnippet}\""
                    textSize = 11f
                    setTextColor(Color.parseColor("#CBD5E1"))
                }
                refItem.addView(refSnippet)

                val openLinkBtn = TextView(this).apply {
                    text = "↗ Mở nguồn kiểm chứng gốc"
                    textSize = 11f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.parseColor("#38BDF8"))
                    isClickable = true
                    setOnClickListener {
                        try {
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(ref.url)).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(browserIntent)
                        } catch (e: Exception) {
                            Toast.makeText(this@FloatingAssistantService, "Không thể mở liên kết: ${ref.url}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                refItem.addView(openLinkBtn)
                contentLayout.addView(refItem)
            }
        }

        scroll.addView(contentLayout)
        card.addView(scroll)

        // Bottom Actions
        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, (8 * density).toInt(), 0, 0)
            layoutParams = params
        }

        val askAiBtn = TextView(this).apply {
            text = "💬 Hỏi AI"
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding((10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 8f * density
                setColor(Color.parseColor("#4D38BDF8"))
            }
            isClickable = true
            setOnClickListener {
                card.visibility = View.GONE
                showOnScreenChatCard()
            }
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            params.setMargins(0, 0, (6 * density).toInt(), 0)
            layoutParams = params
        }
        actionsRow.addView(askAiBtn)

        val fullAppBtn = TextView(this).apply {
            text = "📱 Xem trong App"
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding((10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 8f * density
                setColor(Color.parseColor("#331E293B"))
            }
            isClickable = true
            setOnClickListener {
                card.visibility = View.GONE
                openResultDetail(record.id)
            }
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = params
        }
        actionsRow.addView(fullAppBtn)

        card.addView(actionsRow)
        card.visibility = View.VISIBLE
    }

    private fun setupOnScreenChatCard(root: FrameLayout, density: Float, screenWidth: Int) {
        val cardWidth = (screenWidth * 0.94f).toInt().coerceAtMost((400 * density).toInt())

        val chatCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (14 * density).toInt()
            setPadding(pad, pad, pad, pad)
            background = createLiquidGlassPanelBackground()
            visibility = View.GONE
            elevation = 22f * density

            val params = FrameLayout.LayoutParams(
                cardWidth,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                setMargins((10 * density).toInt(), (72 * density).toInt(), 0, 0)
            }
            layoutParams = params
        }

        floatingChatCardView = chatCard
        root.addView(chatCard)
    }

    private fun showOnScreenChatCard() {
        val chatCard = floatingChatCardView ?: return
        chatCard.removeAllViews()
        val density = resources.displayMetrics.density

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, 0, (8 * density).toInt())
            layoutParams = params
        }

        val title = TextView(this).apply {
            text = "💬 Hỏi AI về màn hình đang xem"
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
        }
        headerRow.addView(title)

        val spacer = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }
        headerRow.addView(spacer)

        val closeBtn = TextView(this).apply {
            text = "✕"
            textSize = 18f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding((6 * density).toInt(), (2 * density).toInt(), (6 * density).toInt(), (2 * density).toInt())
            isClickable = true
            setOnClickListener { chatCard.visibility = View.GONE }
        }
        headerRow.addView(closeBtn)
        chatCard.addView(headerRow)

        val responseTv = TextView(this).apply {
            val state = FactCheckServiceManager.assistantState.value
            val initialAnswer = if (state.latestRecord != null) {
                "Nội dung đang xem: \"${state.latestRecord?.normalizedClaim}\"\n\nKết luận: [${state.latestRecord?.verdict?.primaryVerdict?.titleVi}] - ${state.latestRecord?.shortExplanation}"
            } else {
                "Sẵn sàng giải đáp! Bạn có thể chọn câu hỏi nhanh bên dưới hoặc nhập câu hỏi."
            }
            text = initialAnswer
            textSize = 12f
            setTextColor(Color.parseColor("#E2E8F0"))
            setPadding((10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 8f * density
                setColor(Color.parseColor("#331E293B"))
            }
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, 0, (8 * density).toInt())
            layoutParams = params
        }
        chatCard.addView(responseTv)

        val quickQuestions = listOf(
            "Thông tin này đúng hay sai?",
            "Nguồn gốc từ đâu?",
            "Có bằng chứng phản bác không?"
        )
        val qContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, 0, (8 * density).toInt())
            layoutParams = params
        }
        quickQuestions.forEach { q ->
            val qBtn = TextView(this).apply {
                text = q
                textSize = 10f
                setTextColor(Color.parseColor("#38BDF8"))
                setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
                background = GradientDrawable().apply {
                    cornerRadius = 12f * density
                    setColor(Color.parseColor("#2638BDF8"))
                    setStroke(1, Color.parseColor("#6638BDF8"))
                }
                isClickable = true
                setOnClickListener {
                    handleQuickQuestion(q, responseTv)
                }
                val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                params.setMargins(0, 0, (4 * density).toInt(), 0)
                layoutParams = params
            }
            qContainer.addView(qBtn)
        }
        chatCard.addView(qContainer)

        chatCard.visibility = View.VISIBLE
    }

    private fun handleQuickQuestion(question: String, responseTv: TextView) {
        val state = FactCheckServiceManager.assistantState.value
        val record = state.latestRecord

        val answer = when {
            question.contains("đúng hay sai") -> {
                if (record != null) {
                    "Kết luận: [${record.verdict.primaryVerdict.titleVi}] - ${record.verdict.titleVi}\n\n${record.shortExplanation}"
                } else {
                    "Chưa quét được nội dung cụ thể trên màn hình. Hãy bấm 'Kiểm chứng ngay' để AI phân tích."
                }
            }
            question.contains("Nguồn") -> {
                if (record != null && record.references.isNotEmpty()) {
                    val ref = record.references.first()
                    "Dữ liệu kiểm chứng từ ${ref.publisher} (${ref.publishDate}): \"${ref.supportingSnippet}\""
                } else {
                    "Theo tiêu chuẩn xác minh, các cơ quan chuyên môn như Bộ Y Tế, WHO, TTXVN là nguồn kiểm chứng chuẩn."
                }
            }
            question.contains("phản bác") -> {
                if (record != null && record.counterEvidence.isNotBlank()) {
                    "Bằng chứng phản bác: ${record.counterEvidence}"
                } else {
                    "Không có tài liệu khoa học chính thống nào hỗ trợ cho luận điểm sai lệch này."
                }
            }
            else -> "Đang phân tích..."
        }
        responseTv.text = answer
    }

    private fun addPanelItem(parent: LinearLayout, title: String, density: Float, onClick: () -> Unit) {
        val tv = TextView(this).apply {
            text = title
            textSize = 13f
            setTextColor(Color.parseColor("#E2E8F0"))
            val padV = (10 * density).toInt()
            val padH = (12 * density).toInt()
            setPadding(padH, padV, padH, padV)
            background = createPanelItemRipple()
            isClickable = true
            setOnClickListener { onClick() }
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, (3 * density).toInt(), 0, (3 * density).toInt())
            layoutParams = params
        }
        parent.addView(tv)
    }

    private fun toggleQuickPanel() {
        val panel = quickPanelLayout ?: return
        if (panel.visibility == View.VISIBLE) {
            panel.visibility = View.GONE
        } else {
            floatingResultCardView?.visibility = View.GONE
            floatingChatCardView?.visibility = View.GONE
            panel.visibility = View.VISIBLE
        }
    }

    private fun createLiquidGlassBubbleBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#E60F172A"))
            setStroke(3, Color.parseColor("#8038BDF8"))
        }
    }

    private fun createLiquidGlassPanelBackground(): GradientDrawable {
        val density = resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20f * density
            setColor(Color.parseColor("#F20F172A"))
            setStroke(2, Color.parseColor("#4D38BDF8"))
        }
    }

    private fun createPanelItemRipple(): GradientDrawable {
        val density = resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 10f * density
            setColor(Color.parseColor("#331E293B"))
        }
    }

    private fun createDotDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(2, Color.WHITE)
        }
    }

    private fun updateBubbleUi() {
        val state = FactCheckServiceManager.assistantState.value
        val color = when (state.status) {
            AssistantStatus.IDLE -> Color.GRAY
            AssistantStatus.MONITORING -> Color.parseColor("#10B981")
            AssistantStatus.PAUSED -> Color.parseColor("#F59E0B")
            AssistantStatus.ALERT -> Color.parseColor("#EF4444")
        }
        statusDotView?.background = createDotDrawable(color)

        if (state.totalAlertCount > 0) {
            alertBadgeView?.visibility = View.VISIBLE
            alertBadgeView?.text = state.totalAlertCount.toString()
        } else {
            alertBadgeView?.visibility = View.GONE
        }
    }

    private fun initMediaProjection(resultCode: Int, data: Intent, triggerInstantCheck: Boolean = false) {
        try {
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    releaseCaptureResources()
                    FactCheckServiceManager.setMediaProjectionActive(false)
                    safeStartForegroundNotification(false)
                    updateBubbleUi()
                }
            }, Handler(Looper.getMainLooper()))

            safeStartForegroundNotification(true)

            setupVirtualDisplay()
            FactCheckServiceManager.setMediaProjectionActive(true)
            FactCheckServiceManager.updateStatus(AssistantStatus.MONITORING)
            updateBubbleUi()

            startContinuousScreenAnalysisLoop()

            if (triggerInstantCheck) {
                serviceScope.launch {
                    delay(350)
                    executeInstantScreenCheck()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            FactCheckServiceManager.setMediaProjectionActive(false)
            safeStartForegroundNotification(false)
        }
    }

    /**
     * Khởi tạo VirtualDisplay kết nối với ImageReader kèm Handler chuyên dụng
     * Bắt tín hiệu khung hình thực tế từ hệ thống Android
     */
    private fun setupVirtualDisplay() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val densityDpi = metrics.densityDpi

        val scale = 0.75f
        val captureWidth = (width * scale).toInt()
        val captureHeight = (height * scale).toInt()

        captureThread = HandlerThread("ScreenCaptureThread").apply { start() }
        captureHandler = Handler(captureThread!!.looper)

        imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 3)
        imageReader?.setOnImageAvailableListener({ reader ->
            try {
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                val bitmap = convertImageToBitmap(image)
                image.close()
                if (bitmap != null) {
                    latestScreenBitmap?.recycle()
                    latestScreenBitmap = bitmap
                    FactCheckServiceManager.recordFrameCaptured()
                }
            } catch (_: Exception) {}
        }, captureHandler)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "FactCheckScreenCapture",
            captureWidth,
            captureHeight,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
    }

    private fun startContinuousScreenAnalysisLoop() {
        serviceScope.launch(Dispatchers.Default) {
            while (isActive) {
                val settings = FactCheckServiceManager.settings.value
                val sampleIntervalMs = (settings.sampleRateSeconds * 1000L).coerceAtLeast(800L)

                if (!isProjectionPaused && mediaProjection != null) {
                    processScreenFrame()
                }
                delay(sampleIntervalMs)
            }
        }
    }

    private suspend fun processScreenFrame() {
        val bitmap = latestScreenBitmap
        if (bitmap == null || bitmap.isRecycled) return

        try {
            val bitmapCopy = synchronized(this) {
                if (bitmap.isRecycled) return
                bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
            } ?: return

            val hasChanged = frameDetector.hasFrameChanged(bitmapCopy)
            if (!hasChanged) {
                bitmapCopy.recycle()
                return
            }

            withContext(Dispatchers.Main) {
                FactCheckServiceManager.updateAnalysisPhase(AnalysisPhase.RUNNING_OCR)
            }

            val ocrText = ocrProcessor.recognizeText(bitmapCopy)
            bitmapCopy.recycle()

            if (ocrText.isBlank()) {
                withContext(Dispatchers.Main) {
                    FactCheckServiceManager.updateAnalysisPhase(AnalysisPhase.IDLE)
                }
                return
            }

            withContext(Dispatchers.Main) {
                FactCheckServiceManager.updateAnalysisPhase(AnalysisPhase.EXTRACTING_CLAIMS)
            }
            val claims = claimExtractor.extractClaims(ocrText)
            if (claims.isEmpty()) {
                withContext(Dispatchers.Main) {
                    FactCheckServiceManager.updateAnalysisPhase(AnalysisPhase.IDLE)
                }
                return
            }

            val topClaim = claims.first()
            if (topClaim.normalizedClaim == lastAnalyzedClaim && System.currentTimeMillis() - lastAnalysisTimestamp < 8000L) {
                withContext(Dispatchers.Main) {
                    FactCheckServiceManager.updateAnalysisPhase(AnalysisPhase.IDLE)
                }
                return
            }

            lastAnalyzedClaim = topClaim.normalizedClaim
            lastAnalysisTimestamp = System.currentTimeMillis()

            withContext(Dispatchers.Main) {
                FactCheckServiceManager.updateScreenContent(
                    rawText = ocrText,
                    claim = topClaim.normalizedClaim,
                    detectedApp = topClaim.detectedAppName
                )
                FactCheckServiceManager.updateAnalysisPhase(AnalysisPhase.RETRIEVING_EVIDENCE)
            }

            val record = factCheckEngine.verifyClaim(
                extractedClaim = topClaim.normalizedClaim,
                sourceApp = topClaim.detectedAppName,
                rawScreenText = ocrText
            )

            repository.saveRecord(record)

            withContext(Dispatchers.Main) {
                FactCheckServiceManager.onNewFactCheckRecord(record)
                FactCheckServiceManager.updateAnalysisPhase(AnalysisPhase.FINISHED)
                updateBubbleUi()

                if (record.isAlerted) {
                    showOnScreenResultCard(record)
                    triggerAlertNotification(record)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                FactCheckServiceManager.updateAnalysisPhase(AnalysisPhase.IDLE)
            }
        }
    }

    private fun convertImageToBitmap(image: Image): Bitmap? {
        return try {
            val planes = image.planes
            if (planes.isEmpty()) return null
            val buffer: ByteBuffer = planes[0].buffer
            buffer.rewind()
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val rawBitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            rawBitmap.copyPixelsFromBuffer(buffer)

            if (rowPadding == 0) {
                rawBitmap
            } else {
                val cropped = Bitmap.createBitmap(rawBitmap, 0, 0, image.width, image.height)
                rawBitmap.recycle()
                cropped
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * THỰC HIỆN KIỂM CHỨNG TỨC THỜI BẰNG CÁCH CHỤP THỰC TẾ KHUNG HÌNH HIỆN TẠI
     * HIỂN THỊ KẾT QUẢ TRỰC TIẾP TRÊN MÀN HÌNH ĐANG XEM (TIKTOK, YOUTUBE, BÁO CHÍ...), KHÔNG QUAY VỀ APP CHÍNH
     */
    private fun executeInstantScreenCheck() {
        serviceScope.launch {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@FloatingAssistantService, "🔍 Đang đọc và kiểm chứng màn hình hiện tại...", Toast.LENGTH_SHORT).show()
                FactCheckServiceManager.updateAnalysisPhase(AnalysisPhase.CAPTURING_FRAME)
            }

            // Nếu chưa được cấp quyền đọc màn hình, gọi màn hình trong suốt để xin cấp quyền ngay trên app hiện tại
            if (mediaProjection == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FloatingAssistantService, "Vui lòng cho phép quyền chia sẻ màn hình để đọc nội dung", Toast.LENGTH_SHORT).show()
                }
                requestMediaProjectionFromFloating()
                return@launch
            }

            var ocrText = ""
            var targetApp = "Màn hình trực tiếp"

            // 1. Thử lấy khung hình tươi mới nhất từ ImageReader
            var currentBmp: Bitmap? = null
            try {
                val directImage = imageReader?.acquireLatestImage()
                if (directImage != null) {
                    currentBmp = convertImageToBitmap(directImage)
                    directImage.close()
                }
            } catch (_: Exception) {}

            // 2. Nếu ImageReader chưa có sẵn, sử dụng khung hình gần nhất đã lưu trong bộ đệm
            if (currentBmp == null) {
                val cached = latestScreenBitmap
                if (cached != null && !cached.isRecycled) {
                    currentBmp = synchronized(this) {
                        if (!cached.isRecycled) cached.copy(cached.config ?: Bitmap.Config.ARGB_8888, false) else null
                    }
                }
            }

            // 3. Nếu vẫn chưa có, đợi ngắn 250ms cho VirtualDisplay render khung hình đầu tiên
            if (currentBmp == null) {
                delay(250)
                try {
                    val directImage = imageReader?.acquireLatestImage()
                    if (directImage != null) {
                        currentBmp = convertImageToBitmap(directImage)
                        directImage.close()
                    }
                } catch (_: Exception) {}
            }

            // 4. Nhận diện chữ viết (OCR) từ khung hình thu thập được
            if (currentBmp != null && !currentBmp.isRecycled) {
                withContext(Dispatchers.Main) {
                    FactCheckServiceManager.updateAnalysisPhase(AnalysisPhase.RUNNING_OCR)
                }
                ocrText = ocrProcessor.recognizeText(currentBmp)
                currentBmp.recycle()
            }

            val liveAudio = FactCheckServiceManager.assistantState.value.liveTranscribedAudio

            val combinedContent = if (ocrText.isNotBlank()) {
                ocrText
            } else if (liveAudio.isNotBlank()) {
                targetApp = "Âm thanh video"
                liveAudio
            } else {
                FactCheckServiceManager.assistantState.value.currentScreenText
            }

            if (combinedContent.isNotBlank()) {
                withContext(Dispatchers.Main) {
                    FactCheckServiceManager.updateAnalysisPhase(AnalysisPhase.EXTRACTING_CLAIMS)
                }
                val claims = claimExtractor.extractClaims(combinedContent)
                val topClaim = claims.firstOrNull()?.normalizedClaim ?: combinedContent.lines().firstOrNull { it.length > 10 } ?: combinedContent.take(120)

                withContext(Dispatchers.Main) {
                    FactCheckServiceManager.updateScreenContent(
                        rawText = combinedContent,
                        claim = topClaim,
                        detectedApp = targetApp
                    )
                    FactCheckServiceManager.updateAnalysisPhase(AnalysisPhase.RETRIEVING_EVIDENCE)
                }

                val record = factCheckEngine.verifyClaim(
                    extractedClaim = topClaim,
                    sourceApp = targetApp,
                    rawScreenText = combinedContent
                )

                repository.saveRecord(record)
                withContext(Dispatchers.Main) {
                    FactCheckServiceManager.onNewFactCheckRecord(record)
                    FactCheckServiceManager.updateAnalysisPhase(AnalysisPhase.FINISHED)
                    updateBubbleUi()
                    showOnScreenResultCard(record)
                }
            } else {
                // CHƯA ĐỦ CƠ SỞ: Không phát hiện văn bản rõ ràng hoặc âm thanh
                // Hiển thị trực tiếp Thẻ kết quả "CHƯA ĐỦ CƠ SỞ" ngay trên màn hình, KHÔNG quay về MainActivity
                withContext(Dispatchers.Main) {
                    FactCheckServiceManager.updateAnalysisPhase(AnalysisPhase.IDLE)
                    val noContentRecord = FactCheckRecord(
                        id = UUID.randomUUID().toString(),
                        originalClaim = "Không phát hiện văn bản trên màn hình hiện tại",
                        normalizedClaim = "Chưa phát hiện văn bản hoặc tuyên bố rõ ràng trên khung hình hiện tại",
                        sourceApp = "Màn hình ứng dụng hiện tại",
                        rawScreenText = "Khung hình hiện tại không phát hiện văn bản hoặc phụ đề rõ ràng.",
                        verdict = FactCheckVerdict.CHUA_DU_BANG_CHUNG,
                        confidence = 0.5f,
                        shortExplanation = "Chưa đủ cơ sở kết luận: Khung hình hiện tại chưa có văn bản, số liệu hoặc tuyên bố để kiểm chứng.",
                        detailedExplanation = "Hệ thống đã quét màn hình thành công nhưng không tìm thấy đoạn văn bản rõ nét hoặc phụ đề nhận diện được.\n\n• Hướng dẫn kiểm chứng:\n1. Cuộn màn hình đến phần bài viết, tiêu đề hoặc phần bình luận của bài đăng.\n2. Nếu đang xem video có tiếng nói (TikTok, YouTube Shorts, Reels), hãy chạm nút '🎙️ Bật nhận diện âm thanh video (STT)' trên bảng điều khiển để hệ thống nghe và kiểm chứng trực tiếp lời nói trong video.\n3. Hoặc chạm vào '💬 Hỏi AI' để nhập trực tiếp nội dung bạn muốn kiểm tra.",
                        evidence = "Hệ thống OCR cục bộ và Speech-To-Text chưa nhận diện được tín hiệu văn bản/lời nói trên màn hình tại thời điểm này.",
                        references = listOf(
                            ReferenceSource(
                                title = "Hướng dẫn kiểm chứng màn hình & video",
                                publisher = "LTM FactCheck AI",
                                url = "https://factcheck.org",
                                publishDate = "Hôm nay",
                                supportingSnippet = "Hỗ trợ đọc chữ từ bài viết, tiêu đề, bình luận hoặc chuyển giọng nói video thành văn bản.",
                                isPrimarySource = true
                            )
                        ),
                        checkedAt = System.currentTimeMillis(),
                        isAlerted = false
                    )
                    showOnScreenResultCard(noContentRecord)
                }
            }
        }
    }

    private fun requestMediaProjectionFromFloating() {
        try {
            val intent = Intent(this, TransparentProjectionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Không thể mở yêu cầu cấp quyền đọc màn hình", Toast.LENGTH_SHORT).show()
        }
    }

    private fun triggerAlertNotification(record: FactCheckRecord) {
        val openDetailIntent = PendingIntent.getActivity(
            this,
            record.id.hashCode(),
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("NAVIGATE_TO_DETAIL", record.id)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, FactCheckApplication.ALERT_CHANNEL_ID)
            .setContentTitle("⚠️ Cảnh báo: ${record.verdict.primaryVerdict.titleVi} (${record.verdict.titleVi})")
            .setContentText(record.normalizedClaim)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${record.shortExplanation}\n\nNguồn: ${record.references.firstOrNull()?.publisher ?: "Kiểm chứng độc lập"}"))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openDetailIntent)
            .build()

        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(FactCheckApplication.ALERT_NOTIFICATION_ID, notification)
    }

    private fun pauseMonitoring() {
        isProjectionPaused = true
        FactCheckServiceManager.updateStatus(AssistantStatus.PAUSED)
        updateBubbleUi()
    }

    private fun resumeMonitoring() {
        isProjectionPaused = false
        FactCheckServiceManager.updateStatus(AssistantStatus.MONITORING)
        updateBubbleUi()
    }

    private fun stopMonitoring() {
        releaseCaptureResources()
        audioSpeechEngine?.stopListening()
        FactCheckServiceManager.setAudioActive(false)
        FactCheckServiceManager.setMediaProjectionActive(false)
        FactCheckServiceManager.updateStatus(AssistantStatus.IDLE)
        safeStartForegroundNotification(false)
        updateBubbleUi()
    }

    private fun releaseCaptureResources() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            captureThread?.quitSafely()
            captureThread = null
            captureHandler = null
            mediaProjection?.stop()
            mediaProjection = null
            latestScreenBitmap?.recycle()
            latestScreenBitmap = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun observeUserEvents() {
        serviceScope.launch {
            FactCheckServiceManager.userEvents.collect { event ->
                when (event) {
                    FactCheckServiceManager.UserActionEvent.RequestStartMonitoring -> {
                        if (mediaProjection == null) {
                            requestMediaProjectionFromFloating()
                        } else {
                            resumeMonitoring()
                        }
                    }
                    FactCheckServiceManager.UserActionEvent.RequestPauseMonitoring -> pauseMonitoring()
                    FactCheckServiceManager.UserActionEvent.RequestResumeMonitoring -> resumeMonitoring()
                    FactCheckServiceManager.UserActionEvent.RequestStopMonitoring -> stopMonitoring()
                    FactCheckServiceManager.UserActionEvent.RequestInstantScreenCheck -> executeInstantScreenCheck()
                    FactCheckServiceManager.UserActionEvent.RequestToggleAudio -> toggleAudioListening()
                    is FactCheckServiceManager.UserActionEvent.OpenChatWithScreenContext -> showOnScreenChatCard()
                    FactCheckServiceManager.UserActionEvent.DismissAlert -> {
                        FactCheckServiceManager.clearAlert()
                        updateBubbleUi()
                    }
                }
            }
        }
    }

    private fun openMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun openResultDetail(recordId: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("NAVIGATE_TO_DETAIL", recordId)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        FactCheckServiceManager.setOverlayActive(false)
        stopMonitoring()
        ocrProcessor.close()
        audioSpeechEngine?.stopListening()
        floatingRootView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_START_PROJECTION = "com.example.action.START_PROJECTION"
        const val ACTION_PAUSE = "com.example.action.PAUSE"
        const val ACTION_RESUME = "com.example.action.RESUME"
        const val ACTION_STOP = "com.example.action.STOP"
        const val ACTION_INSTANT_CHECK = "com.example.action.INSTANT_CHECK"
        const val ACTION_TOGGLE_AUDIO = "com.example.action.TOGGLE_AUDIO"
        const val ACTION_SHOW_RESULT = "com.example.action.SHOW_RESULT"
        const val ACTION_DISMISS_ALERT = "com.example.action.DISMISS_ALERT"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_TRIGGER_INSTANT_CHECK = "extra_trigger_instant_check"
    }
}
