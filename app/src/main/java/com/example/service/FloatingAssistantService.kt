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
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.FactCheckApplication
import com.example.MainActivity
import com.example.R
import com.example.data.repository.FactCheckRepository
import com.example.engine.ClaimExtractor
import com.example.engine.FactCheckEngine
import com.example.engine.FactCheckServiceManager
import com.example.engine.OcrProcessor
import com.example.engine.ScreenFrameDetector
import com.example.model.AnalysisPhase
import com.example.model.AssistantStatus
import com.example.model.FactCheckRecord
import com.example.model.FactCheckVerdict
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
    private var statusDotView: View? = null
    private var alertBadgeView: TextView? = null

    // Window Layout Params
    private lateinit var bubbleLayoutParams: WindowManager.LayoutParams

    // MediaProjection & Capture
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var isProjectionPaused: Boolean = false

    // Analysis Pipeline
    private val frameDetector = ScreenFrameDetector(0.06f)
    private val ocrProcessor = OcrProcessor()
    private val claimExtractor = ClaimExtractor()
    private val factCheckEngine = FactCheckEngine()
    private lateinit var repository: FactCheckRepository

    // Touch & Drag state
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var isDragging: Boolean = false
    private val clickThreshold = 10

    // Debounce & deduplication
    private var lastAnalyzedClaim: String? = null
    private var lastAnalysisTimestamp: Long = 0L

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        repository = FactCheckRepository.getInstance(this)

        startForegroundNotification()
        setupFloatingWindow()
        FactCheckServiceManager.setOverlayActive(true)

        observeUserEvents()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_PROJECTION -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (resultCode != 0 && resultData != null) {
                    initMediaProjection(resultCode, resultData)
                }
            }
            ACTION_PAUSE -> pauseMonitoring()
            ACTION_RESUME -> resumeMonitoring()
            ACTION_STOP -> stopMonitoring()
            ACTION_INSTANT_CHECK -> executeInstantScreenCheck()
            ACTION_DISMISS_ALERT -> {
                FactCheckServiceManager.clearAlert()
                updateBubbleUi()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundNotification() {
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

        val notification = NotificationCompat.Builder(this, FactCheckApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("LTM FactCheck AI đang chạy")
            .setContentText("Trợ lý nổi sẵn sàng kiểm chứng thông tin màn hình")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Tạm dừng", pauseIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dừng", stopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FactCheckApplication.FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(FactCheckApplication.FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingWindow() {
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

        val btnSizeDp = FactCheckServiceManager.settings.value.floatingButtonSizeDp
        val density = resources.displayMetrics.density
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

        // Tạo container root cho Bubble & Panel
        val root = FrameLayout(this)
        floatingRootView = root

        // Bubble tròn Liquid Glass
        val bubble = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(btnSizePx, btnSizePx)
            background = createLiquidGlassBubbleBackground()
            elevation = 12f * density
        }

        // Icon chính bên trong bubble
        val iconView = ImageView(this).apply {
            val pad = (btnSizePx * 0.22f).toInt()
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setPadding(pad, pad, pad, pad)
            setImageResource(R.drawable.ic_factcheck_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        bubble.addView(iconView)

        // Status indicator dot
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

        // Cảnh báo đếm số lượng thông tin sai lệch
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

        // Tạo Panel điều khiển nổi (Quick Action Panel)
        setupQuickPanel(root, density)

        // Thiết lập Touch Listener: Kéo thả tự do & Tự động bám cạnh màn hình
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
                    // Click sự kiện: Mở / Đóng Quick Panel
                    toggleQuickPanel()
                } else {
                    // Tự bám cạnh màn hình (Snap to Edge)
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
                (260 * density).toInt(),
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                setMargins(0, (68 * density).toInt(), 0, 0)
            }
            layoutParams = params
        }

        // Tiêu đề Panel
        val header = TextView(this).apply {
            text = "LTM FactCheck AI"
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, 0, (8 * density).toInt())
            layoutParams = params
        }
        panel.addView(header)

        // Các mục chức năng
        addPanelItem(panel, "🔍 Kiểm chứng màn hình này", density) {
            toggleQuickPanel()
            executeInstantScreenCheck()
        }

        addPanelItem(panel, "💬 Hỏi AI về nội dung đang xem", density) {
            toggleQuickPanel()
            openAiChat()
        }

        addPanelItem(panel, "⏯️ Tạm dừng / Tiếp tục", density) {
            toggleQuickPanel()
            if (isProjectionPaused) resumeMonitoring() else pauseMonitoring()
        }

        addPanelItem(panel, "📱 Mở ứng dụng chính", density) {
            toggleQuickPanel()
            openMainActivity()
        }

        addPanelItem(panel, "⏹️ Dừng giám sát & Đóng nút", density) {
            toggleQuickPanel()
            stopMonitoring()
            stopSelf()
        }

        quickPanelLayout = panel
        root.addView(panel)
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
            panel.visibility = View.VISIBLE
        }
    }

    private fun createLiquidGlassBubbleBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#E60F172A")) // Translucent Obsidian
            setStroke(3, Color.parseColor("#8038BDF8")) // Neon Cyan Glass Border
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
            AssistantStatus.MONITORING -> Color.parseColor("#10B981") // Xanh lá
            AssistantStatus.PAUSED -> Color.parseColor("#F59E0B") // Vàng
            AssistantStatus.ALERT -> Color.parseColor("#EF4444") // Đỏ
        }
        statusDotView?.background = createDotDrawable(color)

        if (state.totalAlertCount > 0) {
            alertBadgeView?.visibility = View.VISIBLE
            alertBadgeView?.text = state.totalAlertCount.toString()
        } else {
            alertBadgeView?.visibility = View.GONE
        }
    }

    private fun initMediaProjection(resultCode: Int, data: Intent) {
        try {
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    releaseCaptureResources()
                    FactCheckServiceManager.setMediaProjectionActive(false)
                    updateBubbleUi()
                }
            }, Handler(Looper.getMainLooper()))

            setupVirtualDisplay()
            FactCheckServiceManager.setMediaProjectionActive(true)
            FactCheckServiceManager.updateStatus(AssistantStatus.MONITORING)
            updateBubbleUi()

            startContinuousScreenAnalysisLoop()
        } catch (e: Exception) {
            e.printStackTrace()
            FactCheckServiceManager.setMediaProjectionActive(false)
        }
    }

    private fun setupVirtualDisplay() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val densityDpi = metrics.densityDpi

        // Giảm độ phân giải chụp để tối ưu CPU & RAM
        val scale = 0.5f
        val captureWidth = (width * scale).toInt()
        val captureHeight = (height * scale).toInt()

        imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
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
        val reader = imageReader ?: return
        var image: Image? = null
        var bitmap: Bitmap? = null

        try {
            image = reader.acquireLatestImage() ?: return
            bitmap = convertImageToBitmap(image)
        } catch (_: Exception) {
            // Khung hình bận hoặc drop
        } finally {
            image?.close()
        }

        if (bitmap == null) return

        try {
            // 1. Frame difference detection (Loại bỏ khung hình trùng lặp)
            val hasChanged = frameDetector.hasFrameChanged(bitmap)
            if (!hasChanged) {
                bitmap.recycle()
                return
            }

            withContext(Dispatchers.Main) {
                FactCheckServiceManager.updateAnalysisPhase(AnalysisPhase.RUNNING_OCR)
            }

            // 2. OCR nhận diện văn bản cục bộ
            val ocrText = ocrProcessor.recognizeText(bitmap)
            bitmap.recycle()

            if (ocrText.isBlank()) {
                withContext(Dispatchers.Main) {
                    FactCheckServiceManager.updateAnalysisPhase(AnalysisPhase.IDLE)
                }
                return
            }

            // 3. Claim Extraction (Trích xuất các mệnh đề có thể kiểm chứng)
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
                // Đã xử lý gần đây (Deduplication)
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

            // 4. Evidence Retrieval & Verification
            val record = factCheckEngine.verifyClaim(
                extractedClaim = topClaim.normalizedClaim,
                sourceApp = topClaim.detectedAppName,
                rawScreenText = ocrText
            )

            // Lưu vào database
            repository.saveRecord(record)

            withContext(Dispatchers.Main) {
                FactCheckServiceManager.onNewFactCheckRecord(record)
                FactCheckServiceManager.updateAnalysisPhase(AnalysisPhase.FINISHED)
                updateBubbleUi()

                // Cảnh báo nếu phát hiện thông tin sai lệch
                if (record.isAlerted) {
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
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    private fun executeInstantScreenCheck() {
        serviceScope.launch {
            val state = FactCheckServiceManager.assistantState.value
            val claimToCheck = state.currentClaim ?: "Thông tin màn hình hiện tại"
            val record = factCheckEngine.verifyClaim(
                extractedClaim = claimToCheck,
                sourceApp = state.currentAppDetected,
                rawScreenText = state.currentScreenText
            )
            repository.saveRecord(record)
            FactCheckServiceManager.onNewFactCheckRecord(record)
            updateBubbleUi()
            openResultDetail(record.id)
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
            .setContentTitle("⚠️ Cảnh báo: ${record.verdict.titleVi}")
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
        FactCheckServiceManager.setMediaProjectionActive(false)
        FactCheckServiceManager.updateStatus(AssistantStatus.IDLE)
        updateBubbleUi()
    }

    private fun releaseCaptureResources() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            mediaProjection?.stop()
            mediaProjection = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun observeUserEvents() {
        serviceScope.launch {
            FactCheckServiceManager.userEvents.collect { event ->
                when (event) {
                    FactCheckServiceManager.UserActionEvent.RequestStartMonitoring -> {
                        openMainActivity()
                    }
                    FactCheckServiceManager.UserActionEvent.RequestPauseMonitoring -> pauseMonitoring()
                    FactCheckServiceManager.UserActionEvent.RequestResumeMonitoring -> resumeMonitoring()
                    FactCheckServiceManager.UserActionEvent.RequestStopMonitoring -> stopMonitoring()
                    FactCheckServiceManager.UserActionEvent.RequestInstantScreenCheck -> executeInstantScreenCheck()
                    is FactCheckServiceManager.UserActionEvent.OpenChatWithScreenContext -> openAiChat()
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

    private fun openAiChat() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("NAVIGATE_TO_CHAT", true)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        FactCheckServiceManager.setOverlayActive(false)
        stopMonitoring()
        ocrProcessor.close()
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
        const val ACTION_DISMISS_ALERT = "com.example.action.DISMISS_ALERT"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
    }
}
