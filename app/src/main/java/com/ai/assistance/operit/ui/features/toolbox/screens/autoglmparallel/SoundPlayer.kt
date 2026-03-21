package com.ai.assistance.operit.ui.features.toolbox.screens.autoglmparallel

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.ai.assistance.operit.R

/**
 * 声音播放工具类
 * 用于播放任务完成和失败时的提示音
 */
object SoundPlayer {
    private const val TAG = "SoundPlayer"
    
    private var successPlayer: MediaPlayer? = null
    private var failurePlayer: MediaPlayer? = null
    
    /**
     * 播放任务成功声音
     * @param context 上下文
     */
    fun playSuccessSound(context: Context) {
        try {
            // 释放之前的播放器
            successPlayer?.release()
            
            // 创建新的播放器
            successPlayer = MediaPlayer.create(context, R.raw.task_success)
            
            successPlayer?.apply {
                setOnCompletionListener {
                    release()
                    successPlayer = null
                }
                
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "播放成功声音失败: what=$what, extra=$extra")
                    release()
                    successPlayer = null
                    true
                }
                
                start()
                Log.d(TAG, "开始播放任务成功声音")
            } ?: Log.w(TAG, "无法创建成功声音播放器，请检查 res/raw/task_success 文件是否存在")
        } catch (e: Exception) {
            Log.e(TAG, "播放任务成功声音异常", e)
        }
    }
    
    /**
     * 播放任务失败声音
     * @param context 上下文
     */
    fun playFailureSound(context: Context) {
        try {
            // 释放之前的播放器
            failurePlayer?.release()
            
            // 创建新的播放器
            failurePlayer = MediaPlayer.create(context, R.raw.task_failure)
            
            failurePlayer?.apply {
                setOnCompletionListener {
                    release()
                    failurePlayer = null
                }
                
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "播放失败声音失败: what=$what, extra=$extra")
                    release()
                    failurePlayer = null
                    true
                }
                
                start()
                Log.d(TAG, "开始播放任务失败声音")
            } ?: Log.w(TAG, "无法创建失败声音播放器，请检查 res/raw/task_failure 文件是否存在")
        } catch (e: Exception) {
            Log.e(TAG, "播放任务失败声音异常", e)
        }
    }

    /**
     * 并行任务批次**自然全部结束**时调用（ViewModel 里 `isRunning == true` 的分支）。
     *
     * - 全部 [TaskStatus.SUCCESS] -> 播放成功音
     * - 全部 [TaskStatus.FAILED] -> 播放失败音
     * - 存在 [TaskStatus.CANCELED] -> **不播放**（含用户取消）
     * - 混合结果 / 仍有 [TaskStatus.RUNNING]、[TaskStatus.IDLE] -> 不播放
     */
    fun playParallelBatchOutcomeIfNeeded(context: Context, tasks: List<ParallelTaskUiState>) {
        if (tasks.isEmpty()) return
        if (tasks.any { it.status == TaskStatus.CANCELED }) return
        if (tasks.any { it.status == TaskStatus.RUNNING || it.status == TaskStatus.IDLE }) return

        val appContext = context.applicationContext
        when {
            tasks.all { it.status == TaskStatus.SUCCESS } -> playSuccessSound(appContext)
            tasks.all { it.status == TaskStatus.FAILED } -> playFailureSound(appContext)
        }
    }

    /**
     * 释放所有播放器资源
     * 在应用退出或不再需要时调用
     */
    fun release() {
        try {
            successPlayer?.release()
            successPlayer = null
            failurePlayer?.release()
            failurePlayer = null
            Log.d(TAG, "已释放所有声音播放器资源")
        } catch (e: Exception) {
            Log.e(TAG, "释放声音播放器资源异常", e)
        }
    }
}

