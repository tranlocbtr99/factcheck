package com.example.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import com.example.FactCheckApplication
import com.example.MainActivity
import com.example.R
import com.example.engine.*
import com.example.model.*
import com.example.ui.TransparentProjectionActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One owner per capture session. Cancelling a session invalidates every in-flight result. */
class FloatingAssistantService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pipeline = Mutex()
    private val frameLock = Any()
    private val ocr = OcrProcessor()
    private val sampling = Mutex()
    private val engine
        get() = (application as FactCheckApplication).repository.engine

    private val repository
        get() = (application as FactCheckApplication).repository

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var frame: Bitmap? = null
    @Volatile private var lastFrameAt = 0L
    @Volatile private var captureNow = false
    private var analysisJob: Job? = null
    private var instantJob: Job? = null
    private var audioJob: Job? = null
    private var session = 0L
    private var analysisEpoch = 0L
    private var paused = false
    private var lastText = ""
    private var lastScreenResult: FactCheckRecord? = null
    private var latestScreenText = ""
    private var queuedText = ""
    private var checkingText = ""
    private var retryAt = 0L
    private var requestFailures = 0
    private var verificationJob: Job? = null
    private var screenQueue: Channel<ScreenSnapshot>? = null

    private data class ScreenSnapshot(
        val text: String,
        val raw: String,
        val session: Long,
        val epoch: Long,
        val force: Boolean,
    )

    private var audio: AudioCaptureEngine? = null
    private var microphone = false
    private var audioSerial = 0L
    private lateinit var wm: WindowManager
    private var bubble: TextView? = null
    private var panel: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        foreground(false)
        showBubble()
        scope.launch {
            FactCheckServiceManager.userEvents.collect { event ->
                when (event) {
                    FactCheckServiceManager.UserActionEvent.RequestPauseMonitoring -> pause()
                    FactCheckServiceManager.UserActionEvent.RequestResumeMonitoring -> resume()
                    FactCheckServiceManager.UserActionEvent.RequestStopMonitoring -> stopSession()
                    FactCheckServiceManager.UserActionEvent.RequestInstantScreenCheck -> instant()
                    FactCheckServiceManager.UserActionEvent.RequestToggleAudio -> toggleAudio(false)
                    FactCheckServiceManager.UserActionEvent.DismissAlert ->
                        FactCheckServiceManager.clearAlert()
                    else -> Unit
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_PROJECTION -> {
                val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (data != null)
                    startProjection(
                        intent.getIntExtra(EXTRA_RESULT_CODE, 0),
                        data,
                        intent.getBooleanExtra(EXTRA_TRIGGER_INSTANT_CHECK, false),
                    )
            }
            ACTION_INSTANT_CHECK -> instant()
            ACTION_TOGGLE_AUDIO -> toggleAudio(false)
            ACTION_TOGGLE_MICROPHONE -> toggleAudio(true)
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> stopSession()
            ACTION_SHOW_RESULT -> openApp()
            ACTION_DISMISS_ALERT -> FactCheckServiceManager.clearAlert()
        }
        return START_NOT_STICKY
    }

    private fun foreground(capturing: Boolean) {
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val stop =
            PendingIntent.getService(
                this,
                1,
                Intent(this, javaClass).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat.Builder(this, FactCheckApplication.NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("LTM FactCheck")
                .setContentText(
                    if (capturing) "Đang chia sẻ màn hình • Chạm để quản lý"
                    else "Trợ lý nổi sẵn sàng"
                )
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Kết thúc", stop)
                .build()
        if (Build.VERSION.SDK_INT >= 29) {
            var type =
                if (capturing) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                else if (Build.VERSION.SDK_INT >= 34)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                else 0
            if (microphone) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            startForeground(FactCheckApplication.FOREGROUND_NOTIFICATION_ID, notification, type)
        } else startForeground(FactCheckApplication.FOREGROUND_NOTIFICATION_ID, notification)
    }

    private fun startProjection(code: Int, data: Intent, instant: Boolean) {
        releaseSession()
        try {
            foreground(true) // Must precede getMediaProjection on recent Android releases.
            val p =
                (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
                    .getMediaProjection(code, data)
                    ?: throw FactCheckException("Không nhận được quyền chia sẻ màn hình.")
            projection = p
            val id = session
            p.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        if (id == session) {
                            releaseSession()
                            foreground(false)
                            status(AssistantStatus.IDLE)
                        }
                    }

                    override fun onCapturedContentResize(width: Int, height: Int) {
                        if (id == session && width > 0 && height > 0) resize(width, height)
                    }
                },
                Handler(Looper.getMainLooper()),
            )
            val metrics = resources.displayMetrics
            captureThread = HandlerThread("LtmCapture").apply { start() }
            createReader(metrics.widthPixels, metrics.heightPixels)
            display =
                p.createVirtualDisplay(
                    "LtmFactCheck",
                    reader!!.width,
                    reader!!.height,
                    metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader!!.surface,
                    null,
                    null,
                )
            FactCheckServiceManager.clearContext()
            FactCheckServiceManager.setMediaProjectionActive(true)
            paused = false
            status(AssistantStatus.MONITORING)
            screenQueue = Channel(Channel.CONFLATED)
            startVerifier()
            analysisJob =
                scope.launch {
                    while (isActive) {
                        delay(
                            FactCheckServiceManager.settings.value.sampleRateSeconds.coerceAtLeast(
                                3
                            ) * 1000L
                        )
                        if (!paused && FactCheckServiceManager.settings.value.continuousMonitoring)
                            processFrame(false, id)
                    }
                }
            if (instant)
                instantJob =
                    scope.launch {
                        delay(600)
                        processFrame(true, id)
                    }
        } catch (e: Exception) {
            releaseSession()
            foreground(false)
            fail(e)
        }
    }

    private fun createReader(width: Int, height: Int) {
        val scale = minOf(1f, 1440f / maxOf(width, height))
        reader =
            ImageReader.newInstance(
                (width * scale).toInt().coerceAtLeast(1),
                (height * scale).toInt().coerceAtLeast(1),
                PixelFormat.RGBA_8888,
                2,
            )
        val ownerSession = session
        reader!!.setOnImageAvailableListener(
            { r ->
                runCatching {
                    r.acquireLatestImage()?.use { image ->
                        if (ownerSession != session || r !== reader) return@use
                        val now = SystemClock.elapsedRealtime()
                        if (!captureNow && now - lastFrameAt < 800L) return@use
                        captureNow = false
                        lastFrameAt = now
                        val plane = image.planes[0]
                        val paddedWidth =
                            image.width +
                                (plane.rowStride - plane.pixelStride * image.width) /
                                    plane.pixelStride
                        val padded =
                            Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
                        padded.copyPixelsFromBuffer(plane.buffer)
                        val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
                        if (cropped !== padded) padded.recycle()
                        synchronized(frameLock) {
                            if (ownerSession == session && r === reader) {
                                frame?.recycle()
                                frame = cropped
                            } else cropped.recycle()
                        }
                        FactCheckServiceManager.recordFrameCaptured()
                    }
                }
            },
            Handler(captureThread!!.looper),
        )
    }

    private fun resize(w: Int, h: Int) {
        runCatching {
                display?.surface = null
                reader?.close()
                createReader(w, h)
                display?.resize(
                    reader!!.width,
                    reader!!.height,
                    resources.displayMetrics.densityDpi,
                )
                display?.surface = reader!!.surface
                latestScreenText = ""
            }
            .onFailure { fail(it) }
    }

    private fun instant() {
        if (projection == null) {
            startActivity(
                Intent(this, TransparentProjectionActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        if (instantJob?.isActive == true) return
        instantJob = scope.launch { processFrame(true, session) }
    }

    private suspend fun processFrame(force: Boolean, id: Long) {
        if (sampling.isLocked) return
        sampling.withLock {
            val epoch = analysisEpoch
            var image: Bitmap? = null
            val previousPanel = panel
            val previousVisibility = previousPanel?.visibility
            try {
                if (paused || id != session) return
                previousPanel?.visibility = View.GONE
                bubble?.visibility = View.INVISIBLE
                delay(60)
                captureNow = true
                delay(180)
                image =
                    synchronized(frameLock) {
                        frame?.takeUnless { it.isRecycled }?.copy(Bitmap.Config.ARGB_8888, false)
                    }
                bubble?.visibility = View.VISIBLE
                if (panel === previousPanel && previousVisibility != null)
                    previousPanel.visibility = previousVisibility
                val bitmap = image ?: return
                if (force && checkingText.isBlank())
                    FactCheckServiceManager.updateAnalysisPhase(AnalysisPhase.RUNNING_OCR)
                val raw = ocr.recognizeText(bitmap).trim()
                if (paused || id != session || epoch != analysisEpoch) return
                val text = ScreenTextExtractor.extract(raw)
                if (text != latestScreenText) {
                    latestScreenText = text
                    FactCheckServiceManager.clearContext()
                    FactCheckServiceManager.updateScreenContent(
                        raw.take(12000),
                        text,
                        "Màn hình được chia sẻ",
                    )
                    lastScreenResult
                        ?.takeIf { it.originalClaim == text }
                        ?.let {
                            FactCheckServiceManager.onNewFactCheckRecord(it, countAsCheck = false)
                            FactCheckServiceManager.updateAnalysisPhase(AnalysisPhase.FINISHED)
                            showResult(it)
                        }
                }
                if (text.isBlank()) {
                    if (force)
                        fail(
                            FactCheckException(
                                "Không đọc được chữ. Thử nội dung có phụ đề hoặc nhập văn bản."
                            )
                        )
                    return
                }
                if (
                    !force &&
                        (text == lastText ||
                            text == queuedText ||
                            text == checkingText ||
                            SystemClock.elapsedRealtime() < retryAt)
                )
                    return
                queuedText = text
                screenQueue?.trySend(ScreenSnapshot(text, raw.take(12000), id, epoch, force))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (id == session) fail(e)
            } finally {
                image?.recycle()
                bubble?.visibility = View.VISIBLE
                if (panel === previousPanel && previousVisibility != null)
                    previousPanel.visibility = previousVisibility
            }
        }
    }

    private fun startVerifier() {
        verificationJob?.cancel()
        val queue = screenQueue ?: return
        verificationJob =
            scope.launch {
                for (snapshot in queue) {
                    if (queuedText == snapshot.text) queuedText = ""
                    if (paused || snapshot.session != session || snapshot.epoch != analysisEpoch)
                        continue
                    if (
                        !snapshot.force &&
                            (snapshot.text != latestScreenText ||
                                SystemClock.elapsedRealtime() < retryAt)
                    )
                        continue
                    pipeline.withLock {
                        if (
                            paused || snapshot.session != session || snapshot.epoch != analysisEpoch
                        )
                            return@withLock
                        if (
                            !snapshot.force &&
                                (snapshot.text != latestScreenText ||
                                    SystemClock.elapsedRealtime() < retryAt)
                        )
                            return@withLock
                        checkingText = snapshot.text
                        try {
                            FactCheckServiceManager.updateAnalysisPhase(
                                AnalysisPhase.RETRIEVING_EVIDENCE
                            )
                            val record =
                                engine.verifyClaim(
                                    snapshot.text,
                                    "Màn hình được chia sẻ",
                                    snapshot.raw,
                                )
                            if (
                                paused ||
                                    snapshot.session != session ||
                                    snapshot.epoch != analysisEpoch
                            )
                                return@withLock
                            repository.saveRecord(record)
                            if (
                                paused ||
                                    snapshot.session != session ||
                                    snapshot.epoch != analysisEpoch
                            )
                                return@withLock
                            lastText = snapshot.text
                            lastScreenResult = record
                            retryAt = 0L
                            requestFailures = 0
                            // Retain completed checks in history, but never show them over newer
                            // screen content.
                            if (snapshot.text == latestScreenText) {
                                FactCheckServiceManager.onNewFactCheckRecord(record)
                                FactCheckServiceManager.reportError(null)
                                FactCheckServiceManager.updateAnalysisPhase(AnalysisPhase.FINISHED)
                                status(
                                    if (record.isAlerted) AssistantStatus.ALERT
                                    else AssistantStatus.MONITORING
                                )
                                showResult(record)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            requestFailures++
                            val seconds =
                                RequestBackoff.seconds(
                                    requestFailures,
                                    (e as? FactCheckException)?.retryAfterSeconds,
                                )
                            retryAt = SystemClock.elapsedRealtime() + seconds * 1000L
                            if (snapshot.session == session) fail(e)
                        } finally {
                            if (snapshot.session == session && snapshot.epoch == analysisEpoch)
                                checkingText = ""
                        }
                    }
                }
            }
    }

    private fun toggleAudio(useMic: Boolean) {
        if (audioJob?.isActive == true) {
            stopAudio()
            return
        }
        if (!useMic && projection == null) {
            fail(FactCheckException("Bắt đầu chia sẻ màn hình trước khi thu âm thanh nội bộ."))
            return
        }
        if (
            androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            openApp()
            fail(FactCheckException("Cấp quyền âm thanh trong ứng dụng trước."))
            return
        }
        microphone = useMic
        try {
            foreground(projection != null)
        } catch (e: Exception) {
            microphone = false
            fail(e)
            return
        }
        val id = session
        val serial = ++audioSerial
        audio = AudioCaptureEngine(this)
        FactCheckServiceManager.setAudioActive(true)
        audioJob =
            scope.launch {
                try {
                    audio!!.capture(projection, useMic) { wav ->
                        val spoken = engine.transcribe(wav).trim()
                        if (spoken.isNotBlank() && id == session && !paused) {
                            withContext(Dispatchers.Main) {
                                pipeline.withLock {
                                    FactCheckServiceManager.clearContext()
                                    FactCheckServiceManager.updateLiveAudio(spoken)
                                    FactCheckServiceManager.updateScreenContent(
                                        spoken,
                                        spoken,
                                        if (useMic) "Micro" else "Âm thanh nội bộ",
                                    )
                                    FactCheckServiceManager.updateAnalysisPhase(
                                        AnalysisPhase.RETRIEVING_EVIDENCE
                                    )
                                    val record =
                                        engine.verifyClaim(
                                            spoken,
                                            if (useMic) "Micro" else "Âm thanh nội bộ",
                                            spoken,
                                        )
                                    if (id == session && !paused) {
                                        repository.saveRecord(record)
                                        FactCheckServiceManager.onNewFactCheckRecord(record)
                                        showResult(record)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (isActive) fail(e)
                } finally {
                    if (serial == audioSerial) {
                        FactCheckServiceManager.setAudioActive(false)
                        microphone = false
                        runCatching { foreground(projection != null) }
                    }
                }
            }
    }

    private fun stopAudio() {
        audioSerial++
        audioJob?.cancel()
        audioJob = null
        audio?.stop()
        audio = null
        microphone = false
        FactCheckServiceManager.setAudioActive(false)
    }

    private fun pause() {
        analysisEpoch++
        paused = true
        verificationJob?.cancel()
        checkingText = ""
        queuedText = ""
        instantJob?.cancel()
        stopAudio()
        status(AssistantStatus.PAUSED)
    }

    private fun resume() {
        paused = false
        retryAt = 0L
        requestFailures = 0
        FactCheckServiceManager.reportError(null)
        if (projection != null) startVerifier()
        status(if (projection != null) AssistantStatus.MONITORING else AssistantStatus.IDLE)
    }

    private fun stopSession() {
        releaseSession()
        FactCheckServiceManager.clearContext()
        status(AssistantStatus.IDLE)
        stopSelf()
    }

    private fun releaseSession() {
        session++
        verificationJob?.cancel()
        verificationJob = null
        screenQueue?.close()
        screenQueue = null
        analysisJob?.cancel()
        analysisJob = null
        instantJob?.cancel()
        instantJob = null
        stopAudio()
        val old = projection
        projection = null
        runCatching { display?.release() }
        display = null
        runCatching { reader?.close() }
        reader = null
        captureThread?.quitSafely()
        captureThread = null
        synchronized(frameLock) {
            frame?.recycle()
            frame = null
        }
        runCatching { old?.stop() }
        lastText = ""
        lastScreenResult = null
        latestScreenText = ""
        queuedText = ""
        checkingText = ""
        retryAt = 0L
        requestFailures = 0
        FactCheckServiceManager.setMediaProjectionActive(false)
        FactCheckServiceManager.updateAnalysisPhase(AnalysisPhase.IDLE)
    }

    private fun status(value: AssistantStatus) {
        FactCheckServiceManager.updateStatus(value)
        bubble?.text = if (value == AssistantStatus.ALERT) "!" else "✓"
    }

    private fun fail(error: Throwable) {
        if (error is FactCheckException && error.pauseMonitoring) pause()
        val message = error.message ?: "Không hoàn tất thao tác."
        FactCheckServiceManager.reportError(message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun glass() =
        GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xF5F6F8FC.toInt(), 0xE8DBE4F1.toInt()),
            )
            .apply {
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), 0xFFFFFFFF.toInt())
            }

    private fun showBubble() {
        if (!Settings.canDrawOverlays(this)) return
        val view =
            TextView(this).apply {
                text = "✓"
                textSize = 26f
                gravity = Gravity.CENTER
                setTextColor(0xFF007AFF.toInt())
                background = glass()
                elevation = dp(8).toFloat()
                contentDescription = "Trợ lý LTM FactCheck. Chạm để mở thao tác."
            }
        val type =
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE
        val lp =
            WindowManager.LayoutParams(
                    dp(56),
                    dp(56),
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT,
                )
                .apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = dp(12)
                    y = dp(160)
                }
        var startX = 0
        var startY = 0
        var tx = 0f
        var ty = 0f
        var moved = false
        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x
                    startY = lp.y
                    tx = e.rawX
                    ty = e.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - tx
                    val dy = e.rawY - ty
                    moved = moved || kotlin.math.abs(dx) + kotlin.math.abs(dy) > dp(8)
                    lp.x =
                        (startX + dx.toInt()).coerceIn(
                            0,
                            (resources.displayMetrics.widthPixels - dp(56)).coerceAtLeast(0),
                        )
                    lp.y =
                        (startY + dy.toInt()).coerceIn(
                            0,
                            (resources.displayMetrics.heightPixels - dp(80)).coerceAtLeast(0),
                        )
                    wm.updateViewLayout(view, lp)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) showMenu()
                    true
                }
                else -> false
            }
        }
        wm.addView(view, lp)
        bubble = view
        params = lp
        FactCheckServiceManager.setOverlayActive(true)
    }

    private fun createPanel(): LinearLayout {
        panel?.let { runCatching { wm.removeView(it) } }
        val layout =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = glass()
                setPadding(dp(18), dp(12), dp(18), dp(12))
                elevation = dp(12).toFloat()
            }
        val p =
            WindowManager.LayoutParams(
                    minOf(dp(320), resources.displayMetrics.widthPixels - dp(24)),
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    if (Build.VERSION.SDK_INT >= 26)
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT,
                )
                .apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = dp(90)
                }
        wm.addView(layout, p)
        panel = layout
        return layout
    }

    private fun row(parent: LinearLayout, text: String, action: () -> Unit) {
        parent.addView(
            TextView(this).apply {
                this.text = text
                textSize = 16f
                setTextColor(0xFF172235.toInt())
                setPadding(0, dp(14), 0, dp(14))
                setOnClickListener {
                    panel?.visibility = View.GONE
                    action()
                }
            }
        )
    }

    private fun showMenu() {
        val p = createPanel()
        row(p, "Kiểm chứng màn hình hiện tại") { instant() }
        row(p, if (paused) "Tiếp tục" else "Tạm dừng") { if (paused) resume() else pause() }
        row(p, "Âm thanh nội bộ: bật / tắt") { toggleAudio(false) }
        row(p, "Mở kết quả và hỏi đáp") { openApp() }
        row(p, "Kết thúc phiên") { stopSession() }
        row(p, "Đóng") {}
    }

    private fun showResult(record: FactCheckRecord) {
        val p = createPanel()
        if (record.references.isEmpty()) {
            p.addView(TextView(this).apply {
                text = KnowledgeAssessment.NOTICE
                textSize = 12f
                setTextColor(0xFF465468.toInt())
                setPadding(0, dp(4), 0, dp(4))
            })
        }
        row(p, record.verdict.titleVi + "\n" + record.shortExplanation.take(220)) {
            openResult(record)
        }
        record.references.take(2).forEach { source ->
            row(p, "Nguồn: ${source.title.take(70)}") {
                runCatching {
                    startActivity(
                        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(source.url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
        row(p, "Xem nhận định và giải thích") { openResult(record) }
        row(p, "Đóng") {}
        if (record.isAlerted) {
            val pending =
                PendingIntent.getActivity(
                    this,
                    2,
                    Intent(this, MainActivity::class.java)
                        .putExtra("NAVIGATE_TO_DETAIL", record.id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val notification =
                NotificationCompat.Builder(this, FactCheckApplication.ALERT_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(record.verdict.titleVi)
                    .setContentText(record.shortExplanation)
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .setSilent(!FactCheckServiceManager.settings.value.hapticFeedback)
                    .build()
            runCatching {
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(
                    1002,
                    notification,
                )
            }
        }
    }

    private fun openResult(r: FactCheckRecord) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra("NAVIGATE_TO_DETAIL", r.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    private fun openApp() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    override fun onDestroy() {
        releaseSession()
        scope.cancel()
        ocr.close()
        panel?.let { runCatching { wm.removeView(it) } }
        bubble?.let { runCatching { wm.removeView(it) } }
        FactCheckServiceManager.setOverlayActive(false)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_PROJECTION = "ltm.START_PROJECTION"
        const val ACTION_INSTANT_CHECK = "ltm.INSTANT"
        const val ACTION_TOGGLE_AUDIO = "ltm.AUDIO"
        const val ACTION_TOGGLE_MICROPHONE = "ltm.MICROPHONE"
        const val ACTION_PAUSE = "ltm.PAUSE"
        const val ACTION_RESUME = "ltm.RESUME"
        const val ACTION_STOP = "ltm.STOP"
        const val ACTION_SHOW_RESULT = "ltm.RESULT"
        const val ACTION_DISMISS_ALERT = "ltm.DISMISS"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_TRIGGER_INSTANT_CHECK = "instant"
    }
}
