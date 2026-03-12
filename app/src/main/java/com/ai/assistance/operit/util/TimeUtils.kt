package com.ai.assistance.operit.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 兼容低版本 Android 的时间格式化工具类
 * 解决 SimpleDateFormat 线程不安全问题，提供常用的时间字符串生成方法
 */
object TimeUtils {

    // 定义常用的时间格式常量，方便复用
    const val FORMAT_YMD_HMS = "yyyyMMdd_HHmmss" // 年月日_时分秒
    const val FORMAT_YMD_HMS_LONE = "yyyy-MM-dd_HH-mm-ss" // 年月日_时分秒
    const val FORMAT_YMD = "yyyyMMdd"           // 纯年月日
    const val FORMAT_YMD_HMS_SEPARATOR = "yyyy-MM-dd HH:mm:ss" // 带分隔符的格式

    /**
     * 获取兼容低版本的时间字符串（默认格式：yyyyMMdd_HHmmss）
     * @return 格式化后的时间字符串，例如：20260304_153020
     */
    @JvmStatic
    fun getDateTimeStringDirShort(): String {
        return getDateTimeStringCompat(FORMAT_YMD_HMS)
    }

    /**
     * 获取兼容低版本的时间字符串（默认格式：yyyyMMdd_HHmmss）
     * @return 格式化后的时间字符串，例如：20260304_153020
     */
    @JvmStatic
    fun getDateTimeStringDirLong(): String {
        return getDateTimeStringCompat(FORMAT_YMD_HMS_LONE)
    }

    /**
     * 重载方法：自定义格式获取时间字符串
     * @param format 时间格式，例如 FORMAT_YMD / FORMAT_YMD_HMS
     * @return 自定义格式的时间字符串
     */
    @JvmStatic
    fun getDateTimeStringCompat(format: String): String {
        // 核心：每次调用都创建新的 SimpleDateFormat 实例，避免线程安全问题
        val sdf = SimpleDateFormat(format, Locale.getDefault())
        return try {
            sdf.format(Date())
        } catch (e: Exception) {
            // 异常兜底：返回空字符串或默认值，避免崩溃
            e.printStackTrace()
            ""
        }
    }

    /**
     * 可选：根据指定时间戳生成时间字符串
     * @param timestamp 时间戳（毫秒）
     * @param format 时间格式
     * @return 格式化后的时间字符串
     */
    @JvmStatic
    fun getDateTimeByTimestampCompat(timestamp: Long, format: String = FORMAT_YMD_HMS): String {
        val sdf = SimpleDateFormat(format, Locale.getDefault())
        return try {
            sdf.format(Date(timestamp))
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}