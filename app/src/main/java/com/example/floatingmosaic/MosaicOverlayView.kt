package com.example.floatingmosaic

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 马赛克遮罩视图
 * 实现思路：在指定矩形区域内绘制马赛克图案（像素块），不捕获屏幕内容
 * 纯本地渲染，无 MediaProjection，满足隐私要求
 * 粒度 0/1/2 对应块大小 8/16/32 像素
 */
class MosaicOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val mosaicPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x80000000.toInt()
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x8000AAFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private var mosaicRect = RectF(0f, 0f, 200f, 200f)
    private var granularity = 1  // 0=细 1=中 2=粗
    private var mosaicVisible = true
    private var showBorder = false
    private var borderHideTime = 0L

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var lastCenterX = 0f
    private var lastCenterY = 0f
    private var lastWidth = 0f
    private var lastHeight = 0f
    private var lastDist = 0f
    private var lastPointerCount = 0
    private var isDragging = false
    private var isScaling = false
    private var longPressTriggered = false
    private var touchDownOnMosaic = false
    private val longPressThreshold = 300L
    private var downTime = 0L
    private val minSize = 50f * resources.displayMetrics.density
    private var maxSize = max(
        resources.displayMetrics.widthPixels.toFloat(),
        resources.displayMetrics.heightPixels.toFloat()
    )
    private var lastViewWidth = 0
    private var lastViewHeight = 0

    var onConfigChangedListener: (() -> Unit)? = null

    fun setOnConfigChangedListener(listener: (() -> Unit)?) {
        onConfigChangedListener = listener
    }

    init {
        setWillNotDraw(false)
        isClickable = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        if (oldW > 0 && oldH > 0 && (w != oldW || h != oldH)) {
            // 屏幕旋转/尺寸变化：按相对比例调整，保持位置和大小比例不变
            val scaleX = w.toFloat() / oldW
            val scaleY = h.toFloat() / oldH
            mosaicRect.set(
                mosaicRect.left * scaleX,
                mosaicRect.top * scaleY,
                mosaicRect.right * scaleX,
                mosaicRect.bottom * scaleY
            )
            val maxRight = w - minSize
            val maxBottom = h - minSize
            mosaicRect.set(
                mosaicRect.left.coerceIn(0f, max(0f, maxRight)),
                mosaicRect.top.coerceIn(0f, max(0f, maxBottom)),
                mosaicRect.right.coerceIn(minSize, w.toFloat()),
                mosaicRect.bottom.coerceIn(minSize, h.toFloat())
            )
            maxSize = max(w.toFloat(), h.toFloat())
            invalidate()
            onConfigChangedListener?.invoke()
        }
        lastViewWidth = w
        lastViewHeight = h
    }

    /**
     * 马赛克块大小（像素）：细=8 中=16 粗=32
     */
    private fun getBlockSize(): Int {
        return when (granularity) {
            0 -> 8
            1 -> 16
            else -> 32
        }
    }

    fun setGranularity(level: Int) {
        granularity = level.coerceIn(0, 2)
        invalidate()
    }

    fun getGranularity(): Int = granularity

    fun setMosaicVisible(visible: Boolean) {
        mosaicVisible = visible
        invalidate()
    }

    fun isMosaicVisible(): Boolean = mosaicVisible

    fun toggleMosaicVisible() {
        mosaicVisible = !mosaicVisible
        invalidate()
    }

    fun setMosaicRect(rect: RectF) {
        mosaicRect.set(rect)
        invalidate()
    }

    fun getMosaicRect(): RectF = RectF(mosaicRect)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!mosaicVisible) return

        val blockSize = getBlockSize().toFloat()
        val left = mosaicRect.left.toInt()
        val top = mosaicRect.top.toInt()
        val right = mosaicRect.right.toInt()
        val bottom = mosaicRect.bottom.toInt()

        // 马赛克渲染：在矩形内绘制交替深浅的方块，模拟马赛克效果
        var y = top
        var row = 0
        while (y < bottom) {
            var x = left
            var col = 0
            while (x < right) {
                val shade = ((row + col) % 2) * 0.15f + 0.7f
                mosaicPaint.color = (0xFF * shade).toInt() shl 24 or 0x808080
                canvas.drawRect(
                    x.toFloat(),
                    y.toFloat(),
                    min((x + blockSize).toFloat(), right.toFloat()),
                    min((y + blockSize).toFloat(), bottom.toFloat()),
                    mosaicPaint
                )
                x += blockSize.toInt()
                col++
            }
            y += blockSize.toInt()
            row++
        }

        if (showBorder && System.currentTimeMillis() < borderHideTime) {
            canvas.drawRect(mosaicRect, borderPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!mosaicVisible) return false  // 马赛克隐藏时，所有触摸穿透到底层应用
        val px = event.rawX
        val py = event.rawY
        val pointerCount = event.pointerCount

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (pointerCount == 1 && mosaicRect.contains(px, py)) {
                    lastTouchX = px
                    lastTouchY = py
                    lastCenterX = mosaicRect.centerX()
                    lastCenterY = mosaicRect.centerY()
                    lastWidth = mosaicRect.width()
                    lastHeight = mosaicRect.height()
                    downTime = System.currentTimeMillis()
                    longPressTriggered = false
                    touchDownOnMosaic = true
                    isDragging = false
                    isScaling = false
                    showBorder = true
                    borderHideTime = System.currentTimeMillis() + 3000
                    return true
                }
                return false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (pointerCount == 2 && mosaicRect.contains(px, py)) {
                    lastDist = spacing(event)
                    lastCenterX = mosaicRect.centerX()
                    lastCenterY = mosaicRect.centerY()
                    lastWidth = mosaicRect.width()
                    lastHeight = mosaicRect.height()
                    isDragging = false
                    isScaling = true
                    showBorder = true
                    borderHideTime = System.currentTimeMillis() + 3000
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!longPressTriggered && pointerCount == 1 && System.currentTimeMillis() - downTime >= longPressThreshold) {
                    longPressTriggered = true
                    isDragging = true
                }
                if (isDragging && pointerCount == 1) {
                    val dx = px - lastTouchX
                    val dy = py - lastTouchY
                    mosaicRect.offset(dx, dy)
                    lastTouchX = px
                    lastTouchY = py
                    invalidate()
                } else if (isScaling && pointerCount == 2) {
                    val newDist = spacing(event)
                    if (newDist > 0 && lastDist > 0) {
                        val scale = newDist / lastDist
                        var w = lastWidth * scale
                        var h = lastHeight * scale
                        w = w.coerceIn(minSize, maxSize)
                        h = h.coerceIn(minSize, maxSize)
                        val cx = lastCenterX
                        val cy = lastCenterY
                        mosaicRect.set(
                            cx - w / 2,
                            cy - h / 2,
                            cx + w / 2,
                            cy + h / 2
                        )
                        lastDist = newDist
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount <= 1) {
                    isDragging = false
                    isScaling = false
                    touchDownOnMosaic = false
                    postDelayed({ showBorder = false; invalidate() }, 500)
                    onConfigChangedListener?.invoke()
                }
            }
        }
        return touchDownOnMosaic || isDragging || isScaling
    }

    private fun spacing(e: MotionEvent): Float {
        val x = e.getX(0) - e.getX(1)
        val y = e.getY(0) - e.getY(1)
        return sqrt(x * x + y * y)
    }
}
