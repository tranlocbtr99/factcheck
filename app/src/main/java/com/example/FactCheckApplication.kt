package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.data.repository.FactCheckRepository

class FactCheckApplication : Application() {

    lateinit var repository: FactCheckRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = FactCheckRepository.getInstance(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "LTM FactCheck Giám sát",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Thông báo hiển thị trạng thái của Trợ lý Kiểm chứng Màn hình"
                setShowBadge(false)
            }

            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "LTM FactCheck Cảnh báo",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Cảnh báo khi phát hiện thông tin có dấu hiệu sai lệch trên màn hình"
                enableVibration(true)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
            manager?.createNotificationChannel(alertChannel)
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "ltm_factcheck_monitoring_channel"
        const val ALERT_CHANNEL_ID = "ltm_factcheck_alert_channel"
        const val FOREGROUND_NOTIFICATION_ID = 1001
        const val ALERT_NOTIFICATION_ID = 1002
    }
}
