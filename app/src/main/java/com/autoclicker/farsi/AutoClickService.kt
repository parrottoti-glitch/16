package com.autoclicker.farsi

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.util.Calendar

/**
 * سرویس اصلی کلیک خودکار با استفاده از Accessibility API اندروید ۱۰
 * قابلیت اجرای کلیک روی نقطه مشخص با فاصله زمانی میلی‌ثانیه‌ای و شروع دقیق
 */
class AutoClickService : AccessibilityService() {

    companion object {
        var instance: AutoClickService? = null
        const val ACTION_START_CLICKING = "com.autoclicker.START"
        const val ACTION_STOP_CLICKING = "com.autoclicker.STOP"
        const val EXTRA_X = "extra_x"
        const val EXTRA_Y = "extra_y"
        const val EXTRA_INTERVAL = "extra_interval"
        const val EXTRA_COUNT = "extra_count"
        const val EXTRA_SCHEDULE_MS = "extra_schedule_ms"
        
        var isClicking = false
            private set
    }

    private var targetX: Float = 655f
    private var targetY: Float = 1879f
    private var clickIntervalMs: Long = 25L
    private var maxClicks: Int = 40
    private var clicksDone: Int = 0

    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        workerThread = HandlerThread("AutoClickWorker").apply { start() }
        workerHandler = Handler(workerThread!!.looper)
        Log.d("AutoClickService", "Accessibility Service متصل و آماده است")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CLICKING -> {
                targetX = intent.getIntExtra(EXTRA_X, targetX.toInt()).toFloat()
                targetY = intent.getIntExtra(EXTRA_Y, targetY.toInt()).toFloat()
                clickIntervalMs = intent.getLongExtra(EXTRA_INTERVAL, clickIntervalMs)
                maxClicks = intent.getIntExtra(EXTRA_COUNT, maxClicks)
                val scheduledTimestamp = intent.getLongExtra(EXTRA_SCHEDULE_MS, 0L)

                if (scheduledTimestamp > System.currentTimeMillis()) {
                    scheduleStart(scheduledTimestamp)
                } else {
                    startClickingNow()
                }
            }
            ACTION_STOP_CLICKING -> {
                stopClicking()
            }
        }
        return START_NOT_STICKY
    }

    /**
     * برنامه‌ریزی برای شروع دقیق با ساعت ۲۴ ساعته شامل میلی‌ثانیه
     */
    private fun scheduleStart(targetEpochMs: Long) {
        val delay = targetEpochMs - System.currentTimeMillis()
        if (delay > 0) {
            workerHandler?.postDelayed({
                // حلقه تنظیم میلی‌ثانیه‌ای برای بالاترین دقت در لحظه صفر
                while (System.currentTimeMillis() < targetEpochMs) {
                    Thread.yield()
                }
                startClickingNow()
            }, (delay - 50).coerceAtLeast(0))
        } else {
            startClickingNow()
        }
    }

    private fun startClickingNow() {
        if (isClicking) return
        isClicking = true
        clicksDone = 0

        broadcastStatus("درحال کلیک...", clicksDone, true)
        clickLoop()
    }

    private val clickRunnable = object : Runnable {
        override fun run() {
            if (!isClicking) return

            if (maxClicks > 0 && clicksDone >= maxClicks) {
                stopClicking()
                broadcastStatus("اتمام تعداد کلیک‌ها ($clicksDone)", clicksDone, false)
                return
            }

            // ارسال ژست کلیک روی مختصات هدف
            dispatchClick(targetX, targetY) { success ->
                clicksDone++
                broadcastStatus("کلیک $clicksDone از $maxClicks", clicksDone, true)
                
                if (isClicking) {
                    workerHandler?.postDelayed(this, clickIntervalMs)
                }
            }
        }
    }

    private fun clickLoop() {
        workerHandler?.post(clickRunnable)
    }

    /**
     * شبیه‌سازی لمس سریع (StrokeDescription) روی مختصات X, Y
     */
    private fun dispatchClick(x: Float, y: Float, onComplete: (Boolean) -> Unit) {
        val path = Path().apply {
            moveTo(x, y)
        }

        // مدت زمان فشرده ماندن صفحه: ۵ میلی‌ثانیه برای کلیک‌های پرسرعت
        val stroke = GestureDescription.StrokeDescription(path, 0L, 5L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onComplete(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                onComplete(false)
            }
        }, null)
    }

    fun stopClicking() {
        isClicking = false
        workerHandler?.removeCallbacksAndMessages(null)
        broadcastStatus("متوقف شد", clicksDone, false)
    }

    private fun broadcastStatus(message: String, count: Int, running: Boolean) {
        val intent = Intent("com.autoclicker.STATUS_UPDATE").apply {
            putExtra("message", message)
            putExtra("count", count)
            putExtra("running", running)
        }
        sendBroadcast(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        stopClicking()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopClicking()
        workerThread?.quitSafely()
        instance = null
    }
}