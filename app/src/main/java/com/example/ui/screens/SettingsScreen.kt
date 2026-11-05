package com.example.ui.screens

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.AlertSensitivity
import com.example.ui.FactCheckViewModel
import com.example.ui.components.LiquidGlassButton
import com.example.ui.components.LiquidGlassCard
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.GlassBackground
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VerdictFalseColor
import com.example.ui.theme.VerdictTrueColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: FactCheckViewModel,
    onRequestOverlayPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val assistantState by viewModel.assistantState.collectAsStateWithLifecycle()

    val canDrawOverlays = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(context)
    } else {
        true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cấu hình & Quyền riêng tư", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GlassBackground)
            )
        },
        containerColor = GlassBackground,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Quyền hệ thống (System Permissions)
            item {
                LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("QUYỀN HỆ THỐNG", color = CyanNeon, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // Quyền hiển thị trên ứng dụng khác (SYSTEM_ALERT_WINDOW)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Nút nổi trên ứng dụng khác", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Hiển thị nút trợ lý khi dùng TikTok, Facebook, YouTube", color = TextSecondary, fontSize = 12.sp)
                            }
                            if (canDrawOverlays) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Đã cấp quyền", tint = VerdictTrueColor)
                            } else {
                                LiquidGlassButton(onClick = onRequestOverlayPermission, modifier = Modifier.height(36.dp)) {
                                    Text("Cấp quyền", fontSize = 12.sp)
                                }
                            }
                        }

                        // Quyền MediaProjection
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Chia sẻ màn hình (MediaProjection)", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Cho phép AI đọc bài viết đang xem khi bạn kích hoạt", color = TextSecondary, fontSize = 12.sp)
                            }
                            Text(
                                text = if (assistantState.isMediaProjectionActive) "Đang bật" else "Chưa bật",
                                color = if (assistantState.isMediaProjectionActive) VerdictTrueColor else TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Nhắc nhở không dùng quyền Trợ năng
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x2210B981))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "✓ LTM FactCheck AI không sử dụng AccessibilityService (Quyền Trợ năng), bảo đảm 100% tuân thủ chính sách bảo mật Google Play.",
                                color = VerdictTrueColor,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // 2. Tần suất chu kỳ đọc màn hình (Sampling Rate)
            item {
                LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("TẦN SUẤT QUÉT MÀN HÌNH", color = CyanNeon, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        val sampleRateLabel = when (settings.sampleRateSeconds) {
                            1 -> "Nhanh (1 giây) — Phản hồi tức thì, tiêu tốn tài nguyên hơn"
                            3 -> "Cân bằng (3 giây) — Khuyên dùng cho mạng xã hội"
                            else -> "Tiết kiệm (8 giây) — Tối ưu pin và CPU"
                        }
                        Text(text = sampleRateLabel, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)

                        Slider(
                            value = when (settings.sampleRateSeconds) {
                                1 -> 0f
                                3 -> 1f
                                else -> 2f
                            },
                            onValueChange = { value ->
                                val sec = when (value.toInt()) {
                                    0 -> 1
                                    1 -> 3
                                    else -> 8
                                }
                                viewModel.updateSettings(settings.copy(sampleRateSeconds = sec))
                            },
                            valueRange = 0f..2f,
                            steps = 1,
                            colors = SliderDefaults.colors(
                                thumbColor = CyanNeon,
                                activeTrackColor = CyanNeon,
                                inactiveTrackColor = GlassSurfaceElevated
                            ),
                            modifier = Modifier.testTag("sampling_rate_slider")
                        )
                    }
                }
            }

            // 3. Mức độ cảnh báo & Bộ lọc (Alert Sensitivity)
            item {
                LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("TIÊU CHUẨN CẢNH BÁO", color = CyanNeon, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // Độ nhạy
                        Column {
                            Text("Mức độ cảnh báo:", color = TextSecondary, fontSize = 12.sp)
                            Text(
                                text = "${settings.alertSensitivity.labelVi}: ${settings.alertSensitivity.thresholdDesc}",
                                color = TextPrimary,
                                fontSize = 13.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Chỉ cảnh báo khi thông tin hoàn toàn Sai", color = TextPrimary, fontSize = 13.sp)
                            Switch(
                                checked = settings.alertOnlyFalse,
                                onCheckedChange = { viewModel.updateSettings(settings.copy(alertOnlyFalse = it)) },
                                colors = SwitchDefaults.colors(checkedThumbColor = CyanNeon, checkedTrackColor = CyanNeon.copy(alpha = 0.3f))
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Cảnh báo nội dung Gây hiểu lầm / Cắt ghép", color = TextPrimary, fontSize = 13.sp)
                            Switch(
                                checked = settings.alertMisleading,
                                onCheckedChange = { viewModel.updateSettings(settings.copy(alertMisleading = it)) },
                                colors = SwitchDefaults.colors(checkedThumbColor = CyanNeon, checkedTrackColor = CyanNeon.copy(alpha = 0.3f))
                            )
                        }
                    }
                }
            }

            // 4. Quyền riêng tư (Privacy by Design)
            item {
                LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PrivacyTip, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("CHÍNH SÁCH QUYỀN RIÊNG TƯ", color = CyanNeon, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Text(
                            text = "• Không ghi hình liên tục: Hình ảnh màn hình chỉ được chụp tạm thời vào RAM để nhận diện văn bản (OCR) và được hủy ngay lập tức.\n" +
                                    "• Không ghi hình nội dung FLAG_SECURE: Hệ thống tôn trọng tuyệt đối bảo vệ mật khẩu, ứng dụng ngân hàng và tin nhắn nhạy cảm.\n" +
                                    "• Kiểm soát tuyệt đối: Bạn có thể nhấn nút Dừng khẩn cấp trên thanh thông báo hoặc nút nổi bất kỳ lúc nào.\n" +
                                    "• Xóa dữ liệu tức thì: Dữ liệu lịch sử kiểm chứng được lưu cục bộ trên máy bạn qua Room Database và có thể xóa hoàn toàn.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        LiquidGlassButton(
                            onClick = { viewModel.clearAllHistory() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Xóa sạch bộ nhớ đệm & Lịch sử kiểm chứng", color = VerdictFalseColor)
                        }
                    }
                }
            }
        }
    }
}
