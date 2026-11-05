package com.example.ui

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.example.service.FloatingAssistantService

/**
 * Hoạt động trong suốt (Translucent) để yêu cầu quyền MediaProjection từ hệ thống Android
 * mà KHÔNG làm gián đoạn hay đẩy người dùng ra khỏi ứng dụng họ đang xem (TikTok, YouTube, Facebook, Chrome...).
 */
class TransparentProjectionActivity : ComponentActivity() {

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, FloatingAssistantService::class.java).apply {
                action = FloatingAssistantService.ACTION_START_PROJECTION
                putExtra(FloatingAssistantService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(FloatingAssistantService.EXTRA_RESULT_DATA, result.data)
                putExtra(FloatingAssistantService.EXTRA_TRIGGER_INSTANT_CHECK, true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Toast.makeText(this, "Đã kích hoạt đọc màn hình trực tiếp!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Chưa cấp quyền chia sẻ màn hình. Chạm vào nút nổi để thử lại.", Toast.LENGTH_SHORT).show()
        }
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try {
            projectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
        } catch (e: Exception) {
            e.printStackTrace()
            finish()
        }
    }
}
