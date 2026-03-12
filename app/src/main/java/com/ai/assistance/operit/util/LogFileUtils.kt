package com.ai.assistance.operit.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter

/**
 * 日志文件操作工具类
 * 支持同步/异步写入大字符串到文件，适配 JVM/Android 环境
 */
object LogFileUtils {

    /**
     * 同步保存日志字符串到文件（阻塞当前线程）
     * @param logContent 要保存的日志内容（支持 StringBuilder/String）
     * @param filePath 完整文件路径（如：D:/logs/app.log 或 /sdcard/app/logs/app.log）
     * @param append 是否追加写入（true=追加，false=覆盖）
     * @return Pair<Boolean, String> 第一个值=是否成功，第二个值=结果信息（成功/失败原因）
     */
    @JvmStatic
    fun saveLogSync(
        logContent: Any, // 兼容 StringBuilder 和 String
        filePath: String,
        append: Boolean = false
    ): Pair<Boolean, String> {
        // 统一转换为字符串，处理 StringBuilder 类型
        val content = when (logContent) {
            is StringBuilder -> logContent.toString().trimEnd()
            is String -> logContent.trimEnd()
            else -> return Pair(false, "日志内容仅支持 String 或 StringBuilder 类型")
        }

        // 校验路径合法性
        if (filePath.isBlank()) {
            return Pair(false, "文件路径不能为空")
        }

        val logFile = File(filePath)
        return try {
            // 递归创建父目录（不存在则创建）
            if (!logFile.parentFile?.exists()!!) {
                val dirCreated = logFile.parentFile?.mkdirs()
                if (dirCreated != true) {
                    return Pair(false, "父目录创建失败：${logFile.parent}")
                }
            }


            FileWriter(logFile, append).use { writer ->
                writer.write(content)
            }

//            // 写入文件（指定 UTF-8 避免中文乱码）
//            logFile.writeText(
//                text = content,
//                charset = StandardCharsets.UTF_8,
//                append = append
//            )

            Pair(true, "日志保存成功，路径：${logFile.absolutePath}")
        } catch (e: SecurityException) {
            Pair(false, "权限不足：${e.message}")
        } catch (e: Exception) {
            Pair(false, "保存失败：${e.message}（${e.javaClass.simpleName}）")
        }
    }

    /**
     * 异步保存日志到文件（非阻塞，适合 Android 主线程/高并发场景）
     * @param logContent 要保存的日志内容
     * @param filePath 完整文件路径
     * @param append 是否追加写入
     * @param callback 回调函数（可选，返回保存结果）
     */
    @JvmStatic
    fun saveLogAsync(
        logContent: Any,
        filePath: String,
        append: Boolean = false,
        callback: ((Boolean, String) -> Unit)? = null
    ) {
        // 切换到 IO 线程执行，避免阻塞主线程
        GlobalScope.launch(Dispatchers.IO) {
            val result = saveLogSync(logContent, filePath, append)
            // 回调切回主线程（可选，Android 场景常用）
            GlobalScope.launch(Dispatchers.Main) {
                callback?.invoke(result.first, result.second)
            }
        }
    }

    /**
     * 校验文件路径是否可写
     * @param filePath 完整文件路径
     * @return Pair<Boolean, String> 第一个值=是否可写，第二个值=原因
     */
    @JvmStatic
    fun checkFilePathWritable(filePath: String): Pair<Boolean, String> {
        if (filePath.isBlank()) {
            return Pair(false, "路径不能为空")
        }
        val file = File(filePath)
        return try {
            // 检查父目录是否可写
            if (!file.parentFile?.canWrite()!!) {
                return Pair(false, "父目录不可写：${file.parent}")
            }
            // 检查文件是否可写（已存在时）
            if (file.exists() && !file.canWrite()) {
                return Pair(false, "文件不可写：${file.absolutePath}")
            }
            Pair(true, "路径可写")
        } catch (e: Exception) {
            Pair(false, "路径校验失败：${e.message}")
        }
    }

    /**
     * 获取文件大小（单位：KB）
     * @param filePath 完整文件路径
     * @return Double 文件大小（KB），返回 -1 表示文件不存在/异常
     */
    @JvmStatic
    fun getFileSizeKB(filePath: String): Double {
        val file = File(filePath)
        return if (file.exists()) {
            (file.length() / 1024.0).run { String.format("%.2f", this).toDouble() }
        } else {
            -1.0
        }
    }
}