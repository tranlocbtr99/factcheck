package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.AnalysisPhase
import com.example.model.AssistantStatus
import com.example.model.FactCheckRecord
import com.example.model.FactCheckVerdict
import com.example.ui.FactCheckViewModel
import com.example.ui.components.LiquidGlassButton
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.VerdictBadge
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.GlassBackground
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassBorderHighlight
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VerdictFalseColor
import com.example.ui.theme.VerdictTrueColor

@Composable
fun DashboardScreen(
    viewModel: FactCheckViewModel,
    onRequestOverlayPermission: () -> Unit,
    onRequestMediaProjection: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val assistantState by viewModel.assistantState.collectAsStateWithLifecycle()
    val allHistory by viewModel.allHistory.collectAsStateWithLifecycle()
    var manualInputText by remember { mutableStateOf("") }

    val trendingTestClaims = listOf(
        "Uống nước chanh nóng kiềm hóa chữa khỏi ung thư",
        "Mạng 5G gây biến đổi gen và phát tán virus",
        "Ngân hàng Nhà nước chuẩn bị đổi tiền polymer",
        "Trái Đất phẳng và ảnh vệ tinh NASA chỉ là dựng hình"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(GlassBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Liquid Glass Header Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.hero_factcheck_banner),
                    contentDescription = "LTM FactCheck Banner",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xCC090D1A),
                                    Color(0xF2090D1A)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "LTM FactCheck AI",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary,
                                fontSize = 22.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(CyanNeon.copy(alpha = 0.2f))
                                .border(1.dp, CyanNeon, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "NATIVE AI",
                                color = CyanNeon,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = "Kiểm chứng trực tiếp trên TikTok, Facebook, YouTube, Báo chí",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }
            }
        }

        // 2. Alert Notification Banner (nếu có cảnh báo sai lệch)
        item {
            AnimatedVisibility(
                visible = assistantState.alertMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LiquidGlassCard(
                    borderColor = VerdictFalseColor,
                    backgroundColor = VerdictFalseColor.copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Cảnh báo",
                            tint = VerdictFalseColor,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CẢNH BÁO THÔNG TIN SAI LỆCH",
                                color = VerdictFalseColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = assistantState.alertMessage ?: "",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { viewModel.dismissAlert() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Đóng cảnh báo",
                                tint = TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // 3. Trạng thái Trợ lý nổi & Giám sát màn hình
        item {
            LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val statusColor = when (assistantState.status) {
                                AssistantStatus.IDLE -> Color.Gray
                                AssistantStatus.MONITORING -> VerdictTrueColor
                                AssistantStatus.PAUSED -> Color(0xFFF59E0B)
                                AssistantStatus.ALERT -> VerdictFalseColor
                            }
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Trạng thái Trợ lý",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = assistantState.status.displayNameVi,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }

                        // Toggle bật/tắt nút nổi (Overlay)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (assistantState.isOverlayActive) "Nút nổi: BẬT" else "Nút nổi: TẮT",
                                color = if (assistantState.isOverlayActive) CyanNeon else TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = assistantState.isOverlayActive,
                                onCheckedChange = { enable ->
                                    if (enable) {
                                        onRequestOverlayPermission()
                                    } else {
                                        viewModel.toggleFloatingAssistant(context, false)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = CyanNeon,
                                    checkedTrackColor = CyanNeon.copy(alpha = 0.3f),
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = Color(0x331E293B)
                                ),
                                modifier = Modifier.testTag("floating_assistant_switch")
                            )
                        }
                    }

                    // Ticker giai đoạn phân tích hiện tại
                    if (assistantState.isMediaProjectionActive) {
                        val phaseText = when (assistantState.analysisPhase) {
                            AnalysisPhase.IDLE -> "Đang theo dõi màn hình..."
                            AnalysisPhase.CAPTURING_FRAME -> "Đang bắt khung hình..."
                            AnalysisPhase.RUNNING_OCR -> "Đang nhận diện chữ viết (OCR)..."
                            AnalysisPhase.EXTRACTING_CLAIMS -> "Đang trích xuất mệnh đề..."
                            AnalysisPhase.RETRIEVING_EVIDENCE -> "Đang đối chiếu bằng chứng độc lập..."
                            AnalysisPhase.FINISHED -> "Đã hoàn thành kiểm chứng"
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x2238BDF8))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = CyanNeon,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "$phaseText (${assistantState.currentAppDetected})",
                                    color = CyanNeon,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Các nút điều khiển giám sát MediaProjection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!assistantState.isMediaProjectionActive) {
                            LiquidGlassButton(
                                onClick = onRequestMediaProjection,
                                isPrimary = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("start_monitoring_button")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Bắt đầu giám sát", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            if (assistantState.status == AssistantStatus.PAUSED) {
                                LiquidGlassButton(
                                    onClick = { viewModel.resumeMonitoring() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("resume_monitoring_button")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Tiếp tục")
                                }
                            } else {
                                LiquidGlassButton(
                                    onClick = { viewModel.pauseMonitoring() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("pause_monitoring_button")
                                ) {
                                    Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Tạm dừng")
                                }
                            }

                            LiquidGlassButton(
                                onClick = { viewModel.stopMonitoring() },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("stop_monitoring_button")
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp), tint = VerdictFalseColor)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Dừng", color = VerdictFalseColor)
                            }
                        }
                    }
                }
            }
        }

        // 4. Quick Stats Ticker (Số lượng kiểm chứng, cảnh báo, độ tin cậy)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LiquidGlassCard(modifier = Modifier.weight(1f)) {
                    Column {
                        Text(text = "Đã kiểm chứng", color = TextSecondary, fontSize = 11.sp)
                        Text(
                            text = "${allHistory.size}",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                }
                LiquidGlassCard(modifier = Modifier.weight(1f)) {
                    Column {
                        Text(text = "Cảnh báo sai", color = TextSecondary, fontSize = 11.sp)
                        Text(
                            text = "${allHistory.count { it.isAlerted }}",
                            color = VerdictFalseColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                }
                LiquidGlassCard(modifier = Modifier.weight(1f)) {
                    Column {
                        Text(text = "Nguồn độc lập", color = TextSecondary, fontSize = 11.sp)
                        Text(
                            text = "100%",
                            color = VerdictTrueColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                }
            }
        }

        // 5. Kiểm chứng nhanh thủ công hoặc bằng chứng gần đây
        item {
            LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Kiểm chứng nhanh mệnh đề",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )

                    OutlinedTextField(
                        value = manualInputText,
                        onValueChange = { manualInputText = it },
                        placeholder = { Text("Nhập tuyên bố cần kiểm chứng (vd: uống nước chanh trị ung thư...)", color = TextMuted, fontSize = 13.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_claim_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanNeon,
                            unfocusedBorderColor = GlassBorder,
                            focusedContainerColor = GlassSurfaceElevated,
                            unfocusedContainerColor = GlassSurface
                        ),
                        trailingIcon = {
                            if (manualInputText.isNotBlank()) {
                                IconButton(onClick = {
                                    viewModel.verifyManualClaim(manualInputText)
                                    manualInputText = ""
                                }) {
                                    Icon(Icons.Default.ArrowForward, contentDescription = "Kiểm chứng", tint = CyanNeon)
                                }
                            }
                        }
                    )

                    // Gợi ý chủ đề tin đồn thịnh hành
                    Text(text = "Tin đồn thường gặp để thử nghiệm:", color = TextSecondary, fontSize = 12.sp)
                    trendingTestClaims.forEach { claim ->
                        TextButton(
                            onClick = {
                                viewModel.verifyManualClaim(claim)
                            },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "• $claim",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = CyanNeon,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. Kết quả mới nhất (nếu có)
        val latest = allHistory.firstOrNull()
        if (latest != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Kiểm chứng mới nhất",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )

                    LiquidGlassCard(
                        onClick = { onNavigateToDetail(latest.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("latest_fact_card")
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                VerdictBadge(verdict = latest.verdict)
                                Text(
                                    text = latest.sourceApp,
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                text = latest.normalizedClaim,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = latest.shortExplanation,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
