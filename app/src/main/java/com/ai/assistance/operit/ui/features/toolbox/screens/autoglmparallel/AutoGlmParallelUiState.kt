package com.ai.assistance.operit.ui.features.toolbox.screens.autoglmparallel

enum class TaskStatus {
    IDLE,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELED
}

/** 单步 GUI agent 日志：截图 + 文本 */
data class ParallelTaskLogStep(
    val stepIndex: Int,
    val screenshotPath: String? = null,
    val text: String = ""
)

data class ParallelTaskUiState(
    val appName: String,
    val prompt: String,
    val status: TaskStatus = TaskStatus.IDLE,
    val durationMillis: Long? = null,
    val headerLog: String = "",
    val logSteps: List<ParallelTaskLogStep> = emptyList(),
    val footerLog: String = ""
)

data class AutoGlmParallelUiState(
    val isRunning: Boolean = false,
    val tasks: List<ParallelTaskUiState> = emptyList(),
    val totalSuccessDurationMillis: Long? = null,
    val slowestSuccessAppName: String? = null
)