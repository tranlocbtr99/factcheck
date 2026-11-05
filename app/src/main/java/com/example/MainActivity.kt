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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.GlassBackground
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

enum class AppNavScreen(
    val titleVi: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DASHBOARD("Tổng quan", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    HISTORY("Lịch sử", Icons.Filled.History, Icons.Outlined.History),
    CHAT("Hỏi AI", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    SETTINGS("Cài đặt", Icons.Filled.Settings, Icons.Outlined.Settings)
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
                    onRequestAudioPermission = { requestAudioPermission() }
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
        val detailId = intent?.getStringExtra("NAVIGATE_TO_DETAIL")
        if (detailId != null) {
            viewModel.selectRecordForDetail(detailId)
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Vui lòng cấp quyền \"Hiển thị trên ứng dụng khác\" cho LTM FactCheck AI", Toast.LENGTH_LONG).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
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
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            // Khởi chạy Foreground Service với token MediaProjection
            val serviceIntent = Intent(this, FloatingAssistantService::class.java).apply {
                action = FloatingAssistantService.ACTION_START_PROJECTION
                putExtra(FloatingAssistantService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(FloatingAssistantService.EXTRA_RESULT_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Toast.makeText(this, "Đã kích hoạt giám sát màn hình. Nút AI nổi đang hoạt động!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Bạn đã từ chối chia sẻ màn hình. Phiên giám sát chưa thể bắt đầu.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Thông báo bị tắt. Bạn có thể bỏ lỡ các cảnh báo sai lệch tức thì.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            FactCheckServiceManager.sendUserEvent(FactCheckServiceManager.UserActionEvent.RequestToggleAudio)
        }
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            FactCheckServiceManager.sendUserEvent(FactCheckServiceManager.UserActionEvent.RequestToggleAudio)
            Toast.makeText(this, "Đã cấp quyền. Bắt đầu nhận diện âm thanh video!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Cần quyền ghi âm để chuyển đổi âm thanh video thành văn bản.", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun MainAppContent(
    viewModel: FactCheckViewModel,
    onLaunchOverlayPermission: () -> Unit,
    onLaunchMediaProjection: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestAudioPermission: () -> Unit
) {
    var currentScreen by remember { mutableStateOf(AppNavScreen.DASHBOARD) }
    val selectedDetailRecord by viewModel.selectedDetailRecord.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        onRequestNotificationPermission()
    }

    if (selectedDetailRecord != null) {
        BackHandler {
            viewModel.selectRecordForDetail("")
        }
        FactCheckDetailScreen(
            record = selectedDetailRecord,
            onNavigateBack = { viewModel.selectRecordForDetail("") },
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xF2090D1A),
                    modifier = Modifier
                        .navigationBarsPadding()
                        .border(1.dp, GlassBorder)
                ) {
                    AppNavScreen.entries.forEach { screen ->
                        val selected = currentScreen == screen
                        NavigationBarItem(
                            selected = selected,
                            onClick = { currentScreen = screen },
                            icon = {
                                Icon(
                                    imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.titleVi,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = screen.titleVi,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CyanNeon,
                                selectedTextColor = CyanNeon,
                                indicatorColor = CyanNeon.copy(alpha = 0.2f),
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted
                            ),
                            modifier = Modifier.testTag("nav_${screen.name.lowercase()}")
                        )
                    }
                }
            },
            containerColor = GlassBackground,
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .statusBarsPadding()
            ) {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "screen_transition"
                ) { targetScreen ->
                    when (targetScreen) {
                        AppNavScreen.DASHBOARD -> DashboardScreen(
                            viewModel = viewModel,
                            onRequestOverlayPermission = onLaunchOverlayPermission,
                            onRequestMediaProjection = onLaunchMediaProjection,
                            onRequestAudioPermission = onRequestAudioPermission,
                            onNavigateToDetail = { id -> viewModel.selectRecordForDetail(id) },
                            onNavigateToChat = { currentScreen = AppNavScreen.CHAT }
                        )
                        AppNavScreen.HISTORY -> HistoryScreen(
                            viewModel = viewModel,
                            onNavigateToDetail = { id -> viewModel.selectRecordForDetail(id) }
                        )
                        AppNavScreen.CHAT -> AiChatScreen(
                            viewModel = viewModel
                        )
                        AppNavScreen.SETTINGS -> SettingsScreen(
                            viewModel = viewModel,
                            onRequestOverlayPermission = onLaunchOverlayPermission
                        )
                    }
                }
            }
        }
    }
}
