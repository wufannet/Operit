package com.ai.assistance.operit.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 在主线程显示 Toast（可从任意线程 / 协程上下文调用）。
 */
object ToastUtils {

    /**
     * 通用：自动判断当前是否在主线程。
     * - 主线程：直接 [Toast.show]
     * - 非主线程：post 到主线程再 show（无需协程）
     */
    @JvmStatic
    fun show(
        context: Context,
        message: CharSequence,
        duration: Int = Toast.LENGTH_SHORT
    ) {
        val app = context.applicationContext
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(app, message, duration).show()
        } else {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(app, message, duration).show()
            }
        }
    }

    @JvmStatic
    fun showShort(
        context: Context,
        message: CharSequence
    ) {
        show(context, message, Toast.LENGTH_SHORT)
    }

    @JvmStatic
    fun showLong(
        context: Context,
        message: CharSequence
    ) {
        show(context, message, Toast.LENGTH_LONG)
    }



    /**
     * 在 suspend 函数中调用：切到 Main 再 show，避免后台线程直接 Toast。
     */
    suspend fun showOnMainThread(
        context: Context,
        message: CharSequence,
        duration: Int = Toast.LENGTH_LONG
    ) {
        val app = context.applicationContext
        withContext(Dispatchers.Main) {
            Toast.makeText(app, message, duration).show()
        }
    }

    /**
     * 与 [show] 相同，保留旧名便于兼容。
     */
    @JvmStatic
    fun postShow(
        context: Context,
        message: CharSequence,
        duration: Int = Toast.LENGTH_LONG
    ) {
        show(context, message, duration)
    }
}
