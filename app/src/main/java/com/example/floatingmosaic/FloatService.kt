package com.example.floatingmosaic

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.LayoutInflater
import android.widget.Button
import android.widget.FrameLayout
import android.widget.SeekBar
import kotlin.math.abs

/**
 * 悬浮窗服务
 * 负责创建和管理：悬浮控制球、操作面板、马赛克遮罩层
 * 使用 WindowManager 添加 TYPE_APPLICATION_OVERLAY 窗口，不抢占焦点
 */
class FloatService : Service() {

    private var windowManager: WindowManager? = null
    private var floatBallContainer: View? = null
    private var mosaicContainer: FrameLayout? = null
    private var mosaicView: MosaicOverlayView? = null
    private var panelContainer: View? = null

    private var ballParams: WindowManager.LayoutParams? = null
    private var mosaicParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private var screenWidth = 0
    private var screenHeight = 0
    private var ballSize = 0
    private var edgeHideThreshold = 0
    private var isPanelExpanded = false
    private var isBallCollapsed = false

    private var lastBallX = 0
    private var lastBallY = 0
    private var ballTouchStartX = 0f
    private var ballTouchStartY = 0f

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        screenWidth = resources.displayMetrics.widthPixels
        screenHeight = resources.displayMetrics.heightPixels
        ballSize = (48 * resources.displayMetrics.density).toInt()
        edgeHideThreshold = (20 * resources.displayMetrics.density).toInt()

        createMosaicOverlay()
        createFloatBall()
        createPanel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY  // 服务被杀死时尝试重启
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeAllViews()
        super.onDestroy()
    }

    private fun createFloatBall() {
        val container = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.float_ball_bg)
            alpha = 0.9f
        }
        val size = ballSize
        val params = createOverlayParams(size, size).apply {
            gravity = Gravity.TOP or Gravity.START
            val saved = PrefsManager.loadFloatBallPosition(this@FloatService)
            if (saved != null) {
                x = saved.first
                y = saved.second
            } else {
                x = screenWidth - size - 50
                y = screenHeight / 2 - size / 2
            }
            lastBallX = x
            lastBallY = y
        }

        container.setOnTouchListener(object : View.OnTouchListener {
            private var downTime = 0L
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        ballTouchStartX = e.rawX
                        ballTouchStartY = e.rawY
                        downTime = System.currentTimeMillis()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - ballTouchStartX).toInt()
                        val dy = (e.rawY - ballTouchStartY).toInt()
                        val w = params.width
                        val h = params.height
                        params.x = (params.x + dx).coerceIn(0, screenWidth - w)
                        params.y = (params.y + dy).coerceIn(0, screenHeight - h)
                        ballTouchStartX = e.rawX
                        ballTouchStartY = e.rawY
                        windowManager?.updateViewLayout(container, params)
                        updateBallCollapseState(container, params)
                    }
                    MotionEvent.ACTION_UP -> {
                        val isClick = System.currentTimeMillis() - downTime < 200 && abs(e.rawX - ballTouchStartX) < 20 && abs(e.rawY - ballTouchStartY) < 20
                        if (isClick) {
                            togglePanel()
                        } else {
                            lastBallX = params.x
                            lastBallY = params.y
                            PrefsManager.saveFloatBallPosition(this@FloatService, params.x, params.y)
                        }
                    }
                }
                return true
            }
        })

        ballParams = params
        floatBallContainer = container
        windowManager?.addView(container, params)
        updateBallCollapseState(container, params)
    }

    private fun updateBallCollapseState(ball: View, params: WindowManager.LayoutParams) {
        val w = params.width
        val h = params.height
        val collapseLeft = params.x < edgeHideThreshold
        val collapseRight = params.x > screenWidth - w - edgeHideThreshold
        val collapseTop = params.y < edgeHideThreshold
        val collapseBottom = params.y > screenHeight - h - edgeHideThreshold
        val shouldCollapse = collapseLeft || collapseRight || collapseTop || collapseBottom
        if (shouldCollapse != isBallCollapsed) {
            isBallCollapsed = shouldCollapse
            val newSize = if (shouldCollapse) ballSize / 3 else ballSize
            params.width = newSize
            params.height = newSize
            windowManager?.updateViewLayout(ball, params)
        }
    }

    private fun createMosaicOverlay() {
        val container = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        val params = createOverlayParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT).apply {
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }

        val mosaic = MosaicOverlayView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        mosaicView = mosaic
        container.addView(mosaic)

        val savedRect = PrefsManager.loadMosaicRect(this)
        val granularity = PrefsManager.loadGranularity(this)
        val visible = PrefsManager.loadMosaicVisible(this)
        mosaic.setGranularity(granularity)
        mosaic.setMosaicVisible(visible)
        if (savedRect != null) {
            mosaic.setMosaicRect(savedRect)
        } else {
            val defSize = (200 * resources.displayMetrics.density).toInt()
            val left = (screenWidth - defSize) / 2
            val top = (screenHeight - defSize) / 2
            mosaic.setMosaicRect(
                android.graphics.RectF(
                    left.toFloat(),
                    top.toFloat(),
                    (left + defSize).toFloat(),
                    (top + defSize).toFloat()
                )
            )
        }

        mosaic.onConfigChangedListener = {
            PrefsManager.saveMosaicRect(this@FloatService, mosaic.getMosaicRect())
            PrefsManager.saveGranularity(this@FloatService, mosaic.getGranularity())
            PrefsManager.saveMosaicVisible(this@FloatService, mosaic.isMosaicVisible())
        }

        mosaicContainer = container
        mosaicParams = params
        windowManager?.addView(container, params)
    }

    private fun createPanel() {
        val panel = LayoutInflater.from(this).inflate(R.layout.view_float_panel, null)
        panel.alpha = 0f
        panel.visibility = View.GONE

        val seekGranularity = panel.findViewById<SeekBar>(R.id.seek_granularity)
        val btnToggleMosaic = panel.findViewById<Button>(R.id.btn_toggle_mosaic)
        val btnExit = panel.findViewById<Button>(R.id.btn_exit)

        seekGranularity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) mosaicView?.setGranularity(progress)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {
                mosaicView?.getGranularity()?.let { PrefsManager.saveGranularity(this@FloatService, it) }
            }
        })

        btnToggleMosaic.setOnClickListener {
            mosaicView?.toggleMosaicVisible()
            val visible = mosaicView?.isMosaicVisible() == true
            PrefsManager.saveMosaicVisible(this@FloatService, visible)
            btnToggleMosaic.text = if (visible) getString(R.string.hide_mosaic) else getString(R.string.show_mosaic)
        }

        btnExit.setOnClickListener {
            stopSelf()
            android.os.Process.killProcess(android.os.Process.myPid())
        }

        val params = createOverlayParams((200 * resources.displayMetrics.density).toInt(), WindowManager.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = lastBallX - 100
            y = lastBallY - 200
        }
        panelParams = params
        panelContainer = panel
        windowManager?.addView(panel, params)
    }

    private fun togglePanel() {
        isPanelExpanded = !isPanelExpanded
        val panel = panelContainer ?: return
        val params = panelParams ?: return
        if (isPanelExpanded) {
            panel.visibility = View.VISIBLE
            panel.animate().alpha(1f).setDuration(150).start()
            mosaicView?.getGranularity()?.let { g ->
                panel.findViewById<SeekBar>(R.id.seek_granularity)?.progress = g
            }
            panel.findViewById<Button>(R.id.btn_toggle_mosaic)?.text =
                if (mosaicView?.isMosaicVisible() == true) getString(R.string.hide_mosaic) else getString(R.string.show_mosaic)
            params.x = (lastBallX - 80).coerceIn(0, screenWidth - 250)
            params.y = (lastBallY - 220).coerceIn(0, screenHeight - 300)
            windowManager?.updateViewLayout(panel, params)
        } else {
            panel.animate().alpha(0f).setDuration(150).withEndAction {
                panel.visibility = View.GONE
            }.start()
        }
    }

    private fun removeAllViews() {
        floatBallContainer?.let { windowManager?.removeView(it) }
        mosaicContainer?.let { windowManager?.removeView(it) }
        panelContainer?.let { windowManager?.removeView(it) }
    }

    private fun createOverlayParams(width: Int, height: Int): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            width,
            height,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            format = PixelFormat.TRANSLUCENT
        }
    }
}
