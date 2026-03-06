package com.example.floatingmosaic

import android.content.Context
import android.graphics.RectF

/**
 * 配置持久化管理
 * 使用 SharedPreferences 本地存储，无网络、无上传
 * 记忆：马赛克区域位置、大小、粒度、显示状态
 */
object PrefsManager {
    private const val PREFS_NAME = "mosaic_prefs"
    private const val KEY_LEFT = "mosaic_left"
    private const val KEY_TOP = "mosaic_top"
    private const val KEY_RIGHT = "mosaic_right"
    private const val KEY_BOTTOM = "mosaic_bottom"
    private const val KEY_GRANULARITY = "granularity"
    private const val KEY_VISIBLE = "mosaic_visible"
    private const val KEY_FLOAT_BALL_X = "float_ball_x"
    private const val KEY_FLOAT_BALL_Y = "float_ball_y"

    private const val DEFAULT_GRANULARITY = 1  // 0=细 1=中 2=粗
    private const val DEFAULT_VISIBLE = true

    /**
     * 保存马赛克区域矩形（屏幕坐标）
     */
    fun saveMosaicRect(context: Context, rect: RectF) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putFloat(KEY_LEFT, rect.left)
            putFloat(KEY_TOP, rect.top)
            putFloat(KEY_RIGHT, rect.right)
            putFloat(KEY_BOTTOM, rect.bottom)
            apply()
        }
    }

    /**
     * 读取马赛克区域，若从未保存则返回 null（使用默认居中 200x200）
     */
    fun loadMosaicRect(context: Context): RectF? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val left = prefs.getFloat(KEY_LEFT, -1f)
        val top = prefs.getFloat(KEY_TOP, -1f)
        val right = prefs.getFloat(KEY_RIGHT, -1f)
        val bottom = prefs.getFloat(KEY_BOTTOM, -1f)
        if (left < 0 || top < 0 || right <= left || bottom <= top) return null
        return RectF(left, top, right, bottom)
    }

    /**
     * 保存粒度：0=细 1=中 2=粗
     */
    fun saveGranularity(context: Context, level: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_GRANULARITY, level.coerceIn(0, 2))
            .apply()
    }

    fun loadGranularity(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_GRANULARITY, DEFAULT_GRANULARITY)
    }

    /**
     * 保存马赛克显示/隐藏状态
     */
    fun saveMosaicVisible(context: Context, visible: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_VISIBLE, visible)
            .apply()
    }

    fun loadMosaicVisible(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_VISIBLE, DEFAULT_VISIBLE)
    }

    /**
     * 保存悬浮球位置
     */
    fun saveFloatBallPosition(context: Context, x: Int, y: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putInt(KEY_FLOAT_BALL_X, x)
            putInt(KEY_FLOAT_BALL_Y, y)
            apply()
        }
    }

    /**
     * 读取悬浮球位置，若未保存返回 null
     */
    fun loadFloatBallPosition(context: Context): Pair<Int, Int>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val x = prefs.getInt(KEY_FLOAT_BALL_X, Int.MIN_VALUE)
        val y = prefs.getInt(KEY_FLOAT_BALL_Y, Int.MIN_VALUE)
        if (x == Int.MIN_VALUE || y == Int.MIN_VALUE) return null
        return Pair(x, y)
    }
}
