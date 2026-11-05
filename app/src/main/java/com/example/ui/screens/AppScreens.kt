package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.FactCheckServiceManager
import com.example.model.FactCheckRecord
import com.example.model.FactCheckVerdict
import com.example.ui.FactCheckViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun Page(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 20.dp, bottom = 125.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            "LTM FACTCHECK",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun ErrorBanner(message: String?, onDismiss: () -> Unit = {}) {
    if (message != null)
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(18.dp),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    message,
                    Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Đóng thông báo") }
            }
        }
}

@Composable
fun DashboardScreen(
    viewModel: FactCheckViewModel,
    onRequestOverlayPermission: () -> Unit,
    onRequestMediaProjection: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToChat: () -> Unit,
    onPickImage: () -> Unit = {},
    onImportAudio: () -> Unit = {},
    onRequestMicrophonePermission: () -> Unit = {},
) {
    val state by viewModel.assistantState.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val history by viewModel.allHistory.collectAsStateWithLifecycle()
    Page("Kiểm chứng.\nHiểu rõ hơn.", "Đọc bằng chứng trước khi tin và chia sẻ.") {
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (connection.configured) "Đã lưu kết nối dịch vụ"
                    else "Cần thiết lập kết nối",
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Caption(
                if (connection.configured)
                    "Mỗi kết luận được đối chiếu với nguồn truy xuất. Việc lưu kết nối chưa xác nhận dịch vụ hoạt động."
                else "Mở Cài đặt để dùng khóa Gemini cá nhân hoặc kết nối máy chủ riêng."
            )
        }
        Panel {
            Text(
                "Bạn muốn kiểm chứng gì?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = draft,
                onValueChange = { viewModel.draft.value = it.take(12000) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 8,
                label = { Text("Dán nội dung hoặc sửa chữ trích xuất") },
                shape = RoundedCornerShape(18.dp),
                supportingText = { Text("${draft.length}/12.000 • Giữ đủ ngữ cảnh và thời điểm") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPickImage, enabled = !busy) {
                    Icon(Icons.Default.Image, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Chọn ảnh")
                }
                OutlinedButton(onClick = onImportAudio, enabled = !busy) { Text("Tệp âm thanh") }
            }
            Caption(
                "Nội dung cần kiểm chứng sẽ được gửi đến dịch vụ AI qua phương thức kết nối đã chọn. Ảnh được đọc chữ trên thiết bị; chỉ văn bản trích xuất được gửi đi."
            )
            Button(
                onClick = { viewModel.verifyManualClaim(draft) },
                enabled = draft.isNotBlank() && !busy && connection.configured,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(if (busy) "Đang xử lý…" else "Kiểm chứng với nguồn")
            }
            if (busy) TextButton(onClick = { viewModel.cancelCheck() }) { Text("Hủy yêu cầu") }
        }
        ErrorBanner(error) { viewModel.error.value = null }
        Panel {
            Text(
                "Trợ lý trên màn hình",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(state.status.displayNameVi, color = MaterialTheme.colorScheme.primary)
            Caption(
                "Bật đọc liên tục rồi cấp quyền chia sẻ. App trích chữ trên màn hình, gửi nội dung mới đến Gemini và trả kết luận, giải thích cùng đường dẫn nguồn."
            )
            Button(
                onClick = {
                    viewModel.updateSettings(
                        viewModel.settings.value.copy(continuousMonitoring = true)
                    )
                    onRequestOverlayPermission()
                },
                enabled = connection.configured,
            ) {
                Text(
                    if (state.isMediaProjectionActive) "Mở trợ lý nổi"
                    else "Bắt đầu kiểm chứng liên tục"
                )
            }
            if (state.isMediaProjectionActive) {
                Row {
                    TextButton(
                        onClick = {
                            if (state.status == com.example.model.AssistantStatus.PAUSED)
                                viewModel.resumeMonitoring()
                            else viewModel.pauseMonitoring()
                        }
                    ) {
                        Text(
                            if (state.status == com.example.model.AssistantStatus.PAUSED) "Tiếp tục"
                            else "Tạm dừng"
                        )
                    }
                    TextButton(onClick = { viewModel.stopMonitoring() }) { Text("Kết thúc") }
                }
            }
            TextButton(
                onClick = onRequestAudioPermission,
                enabled = state.isMediaProjectionActive,
            ) {
                Text(
                    if (state.isAudioTranscriptionActive) "Tắt âm thanh nội bộ"
                    else "Bật âm thanh nội bộ"
                )
            }
            TextButton(onClick = onRequestMicrophonePermission, enabled = connection.configured) {
                Text("Micro: bật / tắt (âm thanh bên ngoài)")
            }
            ErrorBanner(state.errorMessage) { FactCheckServiceManager.reportError(null) }
            if (state.currentScreenText.isNotBlank()) {
                Caption("Nội dung phiên hiện tại")
                Text(state.currentScreenText.take(240))
            }
            state.latestRecord?.let { record ->
                TextButton(onClick = { onNavigateToDetail(record.id) }) {
                    Text("Xem kết quả phiên")
                }
            }
        }
        if (history.isNotEmpty()) {
            Text(
                "Kiểm chứng gần đây",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            history.take(3).forEach { RecordRow(it) { onNavigateToDetail(it.id) } }
        }
    }
}

@Composable
private fun RecordRow(record: FactCheckRecord, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(record.verdict.titleVi, color = record.verdict.color, fontWeight = FontWeight.Bold)
            Text(record.originalClaim, maxLines = 3, fontWeight = FontWeight.Medium)
            Caption(date(record.checkedAt) + " • " + record.sourceApp)
        }
    }
}

private fun date(timestamp: Long) =
    SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale("vi", "VN")).format(Date(timestamp))

@Composable
fun HistoryScreen(viewModel: FactCheckViewModel, onNavigateToDetail: (String) -> Unit) {
    val records by viewModel.filteredHistory.collectAsStateWithLifecycle()
    val q by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filter by viewModel.selectedVerdictFilter.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 125.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                "Lịch sử",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            OutlinedTextField(
                q,
                viewModel::setSearchQuery,
                label = { Text("Tìm nội dung kiểm chứng") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
            )
        }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    filter == null,
                    { viewModel.setVerdictFilter(null) },
                    label = { Text("Tất cả") },
                )
                FactCheckVerdict.entries.forEach { v ->
                    FilterChip(
                        filter == v,
                        { viewModel.setVerdictFilter(v) },
                        label = { Text(v.titleVi) },
                    )
                }
            }
        }
        if (records.isEmpty())
            item {
                Panel {
                    Text("Chưa có kết quả phù hợp")
                    Caption("Kết quả kiểm chứng thực tế sẽ xuất hiện ở đây. Không có lịch sử mẫu.")
                }
            }
        items(records, key = { it.id }) { RecordRow(it) { onNavigateToDetail(it.id) } }
    }
}

@Composable
fun FactCheckDetailScreen(
    record: FactCheckRecord?,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onAsk: () -> Unit = {},
    onRecheck: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    if (record == null) return
    val context = LocalContext.current
    var delete by remember { mutableStateOf(false) }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại")
            }
            Text("Hồ sơ kiểm chứng", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { delete = true }) { Icon(Icons.Default.Delete, "Xóa kết quả") }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                record.verdict.titleVi,
                style = MaterialTheme.typography.headlineLarge,
                color = record.verdict.color,
                fontWeight = FontWeight.Bold,
            )
            Caption("Kiểm tra lúc " + date(record.checkedAt) + " • " + record.sourceApp)
            Text(
                record.originalClaim,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Panel {
                Text(record.shortExplanation, style = MaterialTheme.typography.titleMedium)
                Text(record.detailedExplanation)
            }
            if (record.evidence.isNotBlank())
                Panel {
                    Text("Bằng chứng liên quan", fontWeight = FontWeight.Bold)
                    Text(record.evidence)
                }
            if (record.counterEvidence.isNotBlank())
                Panel {
                    Text("Đối chiếu và phản chứng", fontWeight = FontWeight.Bold)
                    Text(record.counterEvidence)
                }
            Text(
                "Nguồn truy xuất · ${record.references.size}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            record.references.forEachIndexed { i, ref ->
                Panel {
                    Text("[${i+1}] ${ref.title}", fontWeight = FontWeight.Bold)
                    Caption(
                        ref.publisher + " • " + ref.publishDate.ifBlank { "Không rõ ngày đăng" }
                    )
                    Text(ref.supportingSnippet)
                    Caption("Phần liên quan do hệ thống tổng hợp; không phải trích nguyên văn.")
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(ref.url))
                                )
                            }
                        }
                    ) {
                        Text("Mở nguồn gốc ↗")
                    }
                }
            }
            if (record.searchEntryPoint.isNotBlank()) SearchAttribution(record.searchEntryPoint)
            Panel {
                Text("Giới hạn", fontWeight = FontWeight.Bold)
                Text(record.limitations)
                Caption(
                    "Bản lưu trước phiên bản 3.0 có thể là kết quả từ cơ chế từ khóa cũ. Hãy kiểm chứng lại trước khi sử dụng."
                )
            }
            Button(onClick = onAsk, modifier = Modifier.fillMaxWidth()) {
                Text("Hỏi tiếp về hồ sơ này")
            }
            OutlinedButton(onClick = onRecheck, modifier = Modifier.fillMaxWidth()) {
                Text("Kiểm chứng lại với nguồn mới")
            }
            Spacer(Modifier.height(20.dp))
        }
    }
    if (delete)
        AlertDialog(
            onDismissRequest = { delete = false },
            title = { Text("Xóa hồ sơ?") },
            text = { Text("Kết quả và nguồn đã lưu của hồ sơ này sẽ bị xóa khỏi thiết bị.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        delete = false
                        onDelete()
                    }
                ) {
                    Text("Xóa")
                }
            },
            dismissButton = { TextButton(onClick = { delete = false }) { Text("Giữ lại") } },
        )
}

@Composable
private fun SearchAttribution(html: String) {
    // Provider-required search suggestions, isolated from app privileges. No JS, file or content
    // access.
    AndroidView(
        factory = { c ->
            WebView(c).apply {
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkImage = true
                webViewClient =
                    object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: android.webkit.WebResourceRequest,
                        ): Boolean {
                            if (request.url.scheme == "https")
                                runCatching {
                                    c.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                                }
                            return true
                        }
                    }
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = {
            if (it.tag != html) {
                it.tag = html
                it.loadDataWithBaseURL("https://www.google.com", html, "text/html", "UTF-8", null)
            }
        },
        modifier = Modifier.fillMaxWidth().height(120.dp),
        onRelease = { it.destroy() },
    )
}

@Composable
fun AiChatScreen(viewModel: FactCheckViewModel) {
    val record by viewModel.chatContext.collectAsStateWithLifecycle()
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val busy by viewModel.chatBusy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var question by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 22.dp).padding(top = 22.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Hỏi đáp",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        if (record == null) {
            Panel {
                Text("Chọn một hồ sơ để hỏi tiếp")
                Caption(
                    "Mở kết quả trong Lịch sử, sau đó chọn Hỏi tiếp. Trợ lý chỉ sử dụng bằng chứng của hồ sơ bạn chọn."
                )
            }
        } else {
            Panel {
                Caption("Đang thảo luận hồ sơ")
                Text(record!!.originalClaim, maxLines = 3, fontWeight = FontWeight.SemiBold)
            }
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
            }
            LazyColumn(
                Modifier.weight(1f),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(messages) { msg ->
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color =
                            if (msg.isUser) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Caption(if (msg.isUser) "Bạn" else "LTM FactCheck")
                            Text(msg.text)
                        }
                    }
                }
            }
            ErrorBanner(error) { viewModel.error.value = null }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    question,
                    { question = it.take(2000) },
                    Modifier.weight(1f),
                    label = { Text("Câu hỏi về hồ sơ") },
                    maxLines = 4,
                    shape = RoundedCornerShape(22.dp),
                )
                IconButton(
                    onClick = {
                        viewModel.askAiAboutScreen(question)
                        question = ""
                    },
                    enabled = question.isNotBlank() && !busy,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Gửi câu hỏi")
                }
            }
        }
    }
}

@Composable
private fun Toggle(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Caption(description)
        }
        Switch(checked, onChange)
    }
}

@Composable
fun SettingsScreen(viewModel: FactCheckViewModel, onRequestOverlayPermission: () -> Unit) {
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val result by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var url by remember(connection.url) { mutableStateOf(connection.url) }
    var token by remember { mutableStateOf("") }
    var clear by remember { mutableStateOf(false) }
    var personalKey by remember { mutableStateOf("") }
    var model by remember(connection.model) { mutableStateOf(connection.model) }
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null) viewModel.exportHistory(uri)
        }
    Page("Cài đặt", "Kết nối, quyền riêng tư và cách bạn sử dụng trợ lý.") {
        Panel {
            Text(
                "Kết nối dịch vụ",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                url,
                { url = it },
                label = { Text("Máy chủ HTTPS") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )
            OutlinedTextField(
                token,
                { token = it },
                label = {
                    Text(if (connection.configured) "Mã mới (để trống để giữ mã)" else "Mã kết nối")
                },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )
            Button(
                onClick = {
                    viewModel.saveConnection(url, token.ifBlank { connection.token })
                    token = ""
                }
            ) {
                Text("Lưu kết nối")
            }
            OutlinedButton(
                onClick = { viewModel.testConnection() },
                enabled = connection.configured,
            ) {
                Text("Kiểm tra máy chủ")
            }
            result?.let { Caption(it) }
            ErrorBanner(error) { viewModel.error.value = null }
            Caption(
                "Mã được mã hóa trên thiết bị. Khóa AI được giữ ở máy chủ. Không nhập khóa AI vào ô mã kết nối."
            )
        }
        Panel {
            Text(
                "Dùng khóa AI cá nhân",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Caption(
                "Phù hợp dùng riêng: gửi trực tiếp đến Google AI, không cần máy chủ trung gian. Tài khoản API của bạn chịu hạn mức và chi phí. Giới hạn trong app: 100 tác vụ/ngày."
            )
            OutlinedTextField(
                personalKey,
                { personalKey = it },
                label = {
                    Text(
                        if (connection.personalKey.isNotBlank()) "Khóa mới (đã có khóa lưu)"
                        else "Gemini API key cá nhân"
                    )
                },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                model,
                { model = it },
                label = { Text("Mô hình Gemini") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    viewModel.savePersonalKey(personalKey.ifBlank { connection.personalKey }, model)
                    personalKey = ""
                }
            ) {
                Text("Dùng kết nối cá nhân")
            }
            Caption(
                "Khóa được mã hóa bằng Android Keystore. Lưu kết nối cá nhân sẽ chuyển khỏi chế độ máy chủ."
            )
        }
        Panel {
            Text(
                "Phiên đọc màn hình",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Toggle(
                "Theo dõi liên tục",
                "Đọc màn hình định kỳ, gửi nội dung mới đến Gemini. Nội dung không đổi sẽ không gửi lại.",
                s.continuousMonitoring,
            ) {
                viewModel.updateSettings(s.copy(continuousMonitoring = it))
            }
            Text("Đọc màn hình mỗi: ${s.sampleRateSeconds} giây")
            Slider(
                s.sampleRateSeconds.toFloat(),
                { viewModel.updateSettings(s.copy(sampleRateSeconds = it.toInt())) },
                valueRange = 3f..30f,
                steps = 26,
            )
            TextButton(onClick = onRequestOverlayPermission) { Text("Quản lý trợ lý nổi") }
        }
        Panel {
            Text(
                "Cảnh báo",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Toggle(
                "Chỉ cảnh báo kết luận Sai",
                "Không cảnh báo các mức còn lại.",
                s.alertOnlyFalse,
            ) {
                viewModel.updateSettings(s.copy(alertOnlyFalse = it))
            }
            Toggle(
                "Gây hiểu lầm",
                "Cảnh báo khi có bằng chứng về ngữ cảnh sai lệch.",
                s.alertMisleading,
            ) {
                viewModel.updateSettings(s.copy(alertMisleading = it))
            }
            Toggle(
                "Chưa đủ bằng chứng",
                "Nhắc kiểm tra thêm, không gọi nội dung này là tin giả.",
                s.alertInsufficientEvidence,
            ) {
                viewModel.updateSettings(s.copy(alertInsufficientEvidence = it))
            }
            Toggle(
                "Âm báo và rung",
                "Theo quyền và cài đặt thông báo của thiết bị.",
                s.hapticFeedback,
            ) {
                viewModel.updateSettings(s.copy(hapticFeedback = it))
            }
        }
        Panel {
            Text(
                "Hiển thị",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Toggle("Giảm trong suốt", "Dùng vật liệu đặc để đọc rõ hơn.", s.reduceTransparency) {
                viewModel.updateSettings(s.copy(reduceTransparency = it))
            }
            Toggle("Giảm chuyển động", "Giảm hiệu ứng chuyển màn hình.", s.reduceMotion) {
                viewModel.updateSettings(s.copy(reduceMotion = it))
            }
            Caption(
                "Sáng / tối theo hệ thống. Kính được dùng cho điều hướng; nội dung bằng chứng dùng nền dễ đọc."
            )
        }
        Panel {
            Text(
                "Dữ liệu của bạn",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Caption(
                "Ảnh màn hình và âm thanh thu trực tiếp chỉ được xử lý tạm thời. Văn bản cần kiểm chứng và đoạn âm thanh cần chép lời được gửi tới máy chủ và nhà cung cấp AI. Lịch sử kết quả nằm trên thiết bị. Dừng phiên để kết thúc thu thập."
            )
            OutlinedButton(onClick = { export.launch("LTMFactCheck-history.json") }) {
                Text("Xuất lịch sử JSON")
            }
            TextButton(onClick = { clear = true }) {
                Text("Xóa toàn bộ lịch sử", color = MaterialTheme.colorScheme.error)
            }
        }
        Caption("LTM FactCheck 3.1 • Kiểm chứng có nguồn • Designed by LocTM")
    }
    if (clear)
        AlertDialog(
            onDismissRequest = { clear = false },
            title = { Text("Xóa toàn bộ lịch sử?") },
            text = { Text("Thao tác này không thể hoàn tác.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllHistory()
                        clear = false
                    }
                ) {
                    Text("Xóa lịch sử")
                }
            },
            dismissButton = { TextButton(onClick = { clear = false }) { Text("Hủy") } },
        )
}
