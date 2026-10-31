package com.autoclicker.farsi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var etHour: EditText
    private lateinit var etMinute: EditText
    private lateinit var etSecond: EditText
    private lateinit var etMillis: EditText
    private lateinit var etInterval: EditText
    private lateinit var etCount: EditText
    private lateinit var etCoordX: EditText
    private lateinit var etCoordY: EditText
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnShowTarget: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvCurrentTime: TextView

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val message = intent?.getStringExtra("message") ?: ""
            val count = intent?.getIntExtra("count", 0) ?: 0
            val running = intent?.getBooleanExtra("running", false) ?: false
            tvStatus.text = "وضعیت: $message (تعداد کلیک: $count)"
            btnStart.isEnabled = !running
            btnStop.isEnabled = running
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        startClockUpdate()

        registerReceiver(statusReceiver, IntentFilter("com.autoclicker.STATUS_UPDATE"))
    }

    private fun initViews() {
        etHour = findViewById(R.id.etHour)
        etMinute = findViewById(R.id.etMinute)
        etSecond = findViewById(R.id.etSecond)
        etMillis = findViewById(R.id.etMillis)
        etInterval = findViewById(R.id.etInterval)
        etCount = findViewById(R.id.etCount)
        etCoordX = findViewById(R.id.etCoordX)
        etCoordY = findViewById(R.id.etCoordY)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnShowTarget = findViewById(R.id.btnShowTarget)
        tvStatus = findViewById(R.id.tvStatus)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)

        etInterval.setText("25")
        etCount.setText("40")
        etCoordX.setText("655")
        etCoordY.setText("1879")
    }

    private fun setupListeners() {
        btnShowTarget.setOnClickListener {
            if (!checkOverlayPermission()) {
                requestOverlayPermission()
                return@setOnClickListener
            }
            val intent = Intent(this, FloatingTargetService::class.java)
            startService(intent)
            Toast.makeText(this, "نشانه‌گیر روی صفحه فعال شد. آن را به نقطه دلخواه بکشید.", Toast.LENGTH_LONG).show()
        }

        btnStart.setOnClickListener {
            if (!checkAccessibilityPermission()) {
                requestAccessibilityPermission()
                return@setOnClickListener
            }

            val x = etCoordX.text.toString().toIntOrNull() ?: FloatingTargetService.currentX
            val y = etCoordY.text.toString().toIntOrNull() ?: FloatingTargetService.currentY
            val interval = etInterval.text.toString().toLongOrNull() ?: 25L
            val count = etCount.text.toString().toIntOrNull() ?: 40

            // محاسبه زمان‌بندی ۲۴ ساعته میلی‌ثانیه‌ای
            val h = etHour.text.toString().toIntOrNull()
            val m = etMinute.text.toString().toIntOrNull()
            val s = etSecond.text.toString().toIntOrNull()
            val ms = etMillis.text.toString().toIntOrNull() ?: 0

            var scheduledTimestamp = 0L
            if (h != null && m != null && s != null) {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, h)
                    set(Calendar.MINUTE, m)
                    set(Calendar.SECOND, s)
                    set(Calendar.MILLISECOND, ms)
                }
                scheduledTimestamp = cal.timeInMillis
                if (scheduledTimestamp < System.currentTimeMillis()) {
                    // اگر زمان گذشته بود، برای فردا تنظیم می‌شود
                    scheduledTimestamp += 24 * 3600 * 1000
                }
            }

            val startIntent = Intent(this, AutoClickService::class.java).apply {
                action = AutoClickService.ACTION_START_CLICKING
                putExtra(AutoClickService.EXTRA_X, x)
                putExtra(AutoClickService.EXTRA_Y, y)
                putExtra(AutoClickService.EXTRA_INTERVAL, interval)
                putExtra(AutoClickService.EXTRA_COUNT, count)
                putExtra(AutoClickService.EXTRA_SCHEDULE_MS, scheduledTimestamp)
            }
            startService(startIntent)
            Toast.makeText(this, "اتوکلیکر فعال شد", Toast.LENGTH_SHORT).show()
        }

        btnStop.setOnClickListener {
            val stopIntent = Intent(this, AutoClickService::class.java).apply {
                action = AutoClickService.ACTION_STOP_CLICKING
            }
            startService(stopIntent)
        }
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "لطفاً مجوز نمایش روی سایر برنامه‌ها را صادر کنید", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkAccessibilityPermission(): Boolean {
        return AutoClickService.instance != null
    }

    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "لطفاً سرویس AutoClicker را در دسترسی‌پذیری فعال کنید", Toast.LENGTH_LONG).show()
    }

    private fun startClockUpdate() {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.post(object : Runnable {
            override fun run() {
                tvCurrentTime.text = "ساعت دقیق گوشی: " + sdf.format(System.currentTimeMillis())
                handler.postDelayed(this, 50)
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(statusReceiver)
    }
}