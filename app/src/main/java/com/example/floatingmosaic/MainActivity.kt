package com.example.floatingmosaic

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * 主界面：权限检测与引导
 * 首次启动检测悬浮窗权限，未开启则引导用户跳转设置
 * 权限开启后可启动悬浮窗服务
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnOpenSettings = findViewById<Button>(R.id.btn_open_settings)
        val btnStartFloat = findViewById<Button>(R.id.btn_start_float)
        val tvStatus = findViewById<TextView>(R.id.tv_permission_status)

        fun refreshUI() {
            val hasPermission = hasOverlayPermission()
            if (hasPermission) {
                tvStatus.text = "悬浮窗权限已开启，点击下方按钮启动悬浮窗。"
                btnOpenSettings.visibility = android.view.View.GONE
                btnStartFloat.visibility = android.view.View.VISIBLE
            } else {
                tvStatus.text = getString(R.string.permission_desc)
                btnOpenSettings.visibility = android.view.View.VISIBLE
                btnStartFloat.visibility = android.view.View.GONE
            }
        }

        btnOpenSettings.setOnClickListener {
            openOverlaySettings()
        }

        btnStartFloat.setOnClickListener {
            if (hasOverlayPermission()) {
                startFloatService()
                finish()
            } else {
                Toast.makeText(this, "请先开启悬浮窗权限", Toast.LENGTH_SHORT).show()
                refreshUI()
            }
        }

        refreshUI()
    }

    override fun onResume() {
        super.onResume()
        // 从设置页返回时刷新权限状态
        findViewById<Button>(R.id.btn_start_float)?.let { btn ->
            if (hasOverlayPermission()) {
                btn.visibility = android.view.View.VISIBLE
                findViewById<Button>(R.id.btn_open_settings)?.visibility = android.view.View.GONE
                findViewById<TextView>(R.id.tv_permission_status)?.text =
                    "悬浮窗权限已开启，点击下方按钮启动悬浮窗。"
            }
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "无法打开设置，请手动在应用管理中开启悬浮窗权限", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startFloatService() {
        startService(Intent(this, FloatService::class.java))
    }
}
