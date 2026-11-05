package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.FactCheckServiceManager
import com.example.service.FloatingAssistantService
import com.example.ui.FactCheckViewModel
import com.example.ui.screens.AiChatScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FactCheckDetailScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

enum class AppNavScreen(
    val titleVi: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    DASHBOARD("Kiểm chứng", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    HISTORY("Lịch sử", Icons.Filled.History, Icons.Outlined.History),
    CHAT("Hỏi AI", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    SETTINGS("Cài đặt", Icons.Filled.Settings, Icons.Outlined.Settings),
}

class MainActivity : ComponentActivity() {

    private val viewModel: FactCheckViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntentExtras(intent)

        setContent {
            MyApplicationTheme {
                MainAppContent(
                    viewModel = viewModel,
                    onLaunchOverlayPermission = { requestOverlayPermission() },
                    onLaunchMediaProjection = { requestMediaProjection() },
                    onRequestNotificationPermission = { requestNotificationPermission() },
                    onRequestAudioPermission = { requestAudioPermission() },
                    onRequestMicrophonePermission = { requestAudioPermission(true) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentExtras(intent)
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val hasOverlay = Settings.canDrawOverlays(this)
            if (!hasOverlay && FactCheckServiceManager.assistantState.value.isOverlayActive) {
                FactCheckServiceManager.setOverlayActive(false)
            }
        }
    }

    private fun handleIntentExtras(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            if (intent.type == "text/plain")
                viewModel.draft.value =
                    intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty().take(12000)
            else if (intent.type?.startsWith("image/") == true)
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { viewModel.loadImage(it) }
        }
        val detailId = intent?.getStringExtra("NAVIGATE_TO_DETAIL")
        if (detailId != null) {
            viewModel.selectRecordForDetail(detailId)
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(
                        this,
                        "Vui lòng cấp quyền \"Hiển thị trên ứng dụng khác\" cho LTM FactCheck AI",
                        Toast.LENGTH_LONG,
                    )
                    .show()
                val intent =
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName"),
                    )
                startActivity(intent)
            } else {
                FactCheckServiceManager.startService(this)
                if (!FactCheckServiceManager.assistantState.value.isMediaProjectionActive) {
                    requestMediaProjection()
                }
            }
        } else {
            FactCheckServiceManager.startService(this)
            if (!FactCheckServiceManager.assistantState.value.isMediaProjectionActive) {
                requestMediaProjection()
            }
        }
    }

    private fun requestMediaProjection() {
        val mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    private val mediaProjectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                // Khởi chạy Foreground Service với token MediaProjection
                val serviceIntent =
                    Intent(this, FloatingAssistantService::class.java).apply {
                        action = FloatingAssistantService.ACTION_START_PROJECTION
                        putExtra(FloatingAssistantService.EXTRA_RESULT_CODE, result.resultCode)
                        putExtra(FloatingAssistantService.EXTRA_RESULT_DATA, result.data)
                    }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                Toast.makeText(
                        this,
                        "Đã kích hoạt giám sát màn hình. Nút AI nổi đang hoạt động!",
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            } else {
                Toast.makeText(
                        this,
                        "Bạn đã từ chối chia sẻ màn hình. Phiên giám sát chưa thể bắt đầu.",
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            }
        }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(
                        this,
                        "Thông báo bị tắt. Bạn có thể bỏ lỡ các cảnh báo sai lệch tức thì.",
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            }
        }

    private var audioUsesMicrophone = false

    private fun requestAudioPermission(microphone: Boolean = false) {
        audioUsesMicrophone = microphone
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            FactCheckServiceManager.startService(
                this,
                if (audioUsesMicrophone) FloatingAssistantService.ACTION_TOGGLE_MICROPHONE
                else FloatingAssistantService.ACTION_TOGGLE_AUDIO,
            )
        }
    }

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                FactCheckServiceManager.startService(
                    this,
                    if (audioUsesMicrophone) FloatingAssistantService.ACTION_TOGGLE_MICROPHONE
                    else FloatingAssistantService.ACTION_TOGGLE_AUDIO,
                )
                Toast.makeText(
                        this,
                        "Đã cấp quyền. Bắt đầu thu âm thanh nội bộ.",
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            } else {
                Toast.makeText(
                        this,
                        "Cần quyền ghi âm để chuyển đổi âm thanh video thành văn bản.",
                        Toast.LENGTH_LONG,
                    )
                    .show()
            }
        }
}

@Composable
fun MainAppContent(
    viewModel: FactCheckViewModel,
    onLaunchOverlayPermission: () -> Unit,
    onLaunchMediaProjection: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    onRequestMicrophonePermission: () -> Unit,
) {
    var currentScreen by remember { mutableStateOf(AppNavScreen.DASHBOARD) }
    val selected by viewModel.selectedDetailRecord.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val backdrop = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val context = androidx.compose.ui.platform.LocalContext.current
    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                viewModel.loadImage(uri)
            }
        }
    val audioPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) viewModel.importAudio(uri)
        }
    if (selected != null) {
        BackHandler { viewModel.selectRecordForDetail("") }
        FactCheckDetailScreen(
            selected,
            { viewModel.selectRecordForDetail("") },
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding(),
            onAsk = {
                viewModel.useChatContext(selected!!)
                viewModel.selectRecordForDetail("")
                currentScreen = AppNavScreen.CHAT
            },
            onRecheck = {
                val text = selected!!.originalClaim
                viewModel.selectRecordForDetail("")
                viewModel.draft.value = text
                currentScreen = AppNavScreen.DASHBOARD
            },
            onDelete = { viewModel.deleteRecord(selected!!.id) },
        )
    } else {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
            Box(
                Modifier.fillMaxSize().drawWithContent {
                    backdrop.record { this@drawWithContent.drawContent() }
                    drawLayer(backdrop)
                }
            ) {
                Box(
                    Modifier.fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.radialGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    Color.Transparent,
                                ),
                                radius = 1200f,
                            )
                        )
                        .statusBarsPadding()
                ) {
                    val screenContent: @Composable (AppNavScreen) -> Unit = { target ->
                        when (target) {
                            AppNavScreen.DASHBOARD ->
                                DashboardScreen(
                                    viewModel,
                                    onLaunchOverlayPermission,
                                    onLaunchMediaProjection,
                                    onRequestAudioPermission,
                                    { viewModel.selectRecordForDetail(it) },
                                    { currentScreen = AppNavScreen.CHAT },
                                    { imagePicker.launch("image/*") },
                                    { audioPicker.launch("audio/*") },
                                    onRequestMicrophonePermission,
                                )
                            AppNavScreen.HISTORY ->
                                HistoryScreen(viewModel) { viewModel.selectRecordForDetail(it) }
                            AppNavScreen.CHAT -> AiChatScreen(viewModel)
                            AppNavScreen.SETTINGS ->
                                SettingsScreen(viewModel, onLaunchOverlayPermission)
                        }
                    }
                    if (settings.reduceMotion) screenContent(currentScreen)
                    else
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "navigation",
                        ) {
                            screenContent(it)
                        }
                }
            }
            com.example.ui.components.GlassSurface(
                backdrop,
                settings.reduceTransparency,
                Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 12.dp)
                    .fillMaxWidth(),
            ) {
                AppNavScreen.entries.forEach { screen ->
                    val active = currentScreen == screen
                    Surface(
                        onClick = { currentScreen = screen },
                        color =
                            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else Color.Transparent,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f).testTag("nav_${screen.name.lowercase()}"),
                    ) {
                        androidx.compose.foundation.layout.Column(
                            Modifier.padding(vertical = 12.dp),
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                if (active) screen.selectedIcon else screen.unselectedIcon,
                                null,
                                tint =
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                            Text(
                                screen.titleVi,
                                fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color =
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
