package com.ai.assistance.operit.ui.features.toolbox.screens.autoglmparallel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.core.config.FunctionalPrompts
import com.ai.assistance.operit.core.tools.agent.ActionHandler
import com.ai.assistance.operit.core.tools.agent.AgentConfig
import com.ai.assistance.operit.core.tools.agent.PhoneAgent
import com.ai.assistance.operit.core.tools.agent.StepResult
import com.ai.assistance.operit.core.tools.defaultTool.ToolGetter
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.LocaleUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class AutoGlmParallelViewModel(
    private val context: Context
) : ViewModel() {

    private val taskJobs = mutableMapOf<String, Job>()

    private val _uiState = MutableStateFlow(AutoGlmParallelUiState())
    val uiState: StateFlow<AutoGlmParallelUiState> = _uiState.asStateFlow()

    /**
     * 执行并行任务
     */
    fun executeParallel(appList: String, template: String) {
            val apps = appList.split(Regex("[,，]")) // 英文逗号或中文逗号
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (apps.isEmpty() || template.isBlank()) return

        cancelAll()

        val tasks = apps.map { app ->
            ParallelTaskUiState(
                appName = app,
                prompt = "打开$app,$template",
                status = TaskStatus.RUNNING,
                log = ""
            )
        }

        _uiState.value = AutoGlmParallelUiState(
            isRunning = true,
            tasks = tasks
        )

        apps.forEach { app ->
            startSingleTask(app, template)
        }
    }

    /**
     * 启动单个子任务
     */
    private fun startSingleTask(appName: String, template: String) {
        // 我想打车到白马广场,帮我打开应用分别比一比价格,将 template中的应用替换为 appName赋值到prompt
        val prompt = template.replace("应用", appName)
        val agentId = UUID.randomUUID().toString().take(8)

        val job = viewModelScope.launch {
            val logBuilder = StringBuilder()

            fun update(status: TaskStatus? = null) {
                _uiState.value = _uiState.value.copy(
                    tasks = _uiState.value.tasks.map {
                        if (it.appName == appName) {
                            it.copy(
                                status = status ?: it.status,
                                log = logBuilder.toString().trimEnd()
                            )
                        } else it
                    }
                )
            }

            try {
                appendWithTimestamp(logBuilder, "==================================================")
                appendWithTimestamp(logBuilder, "Task: $prompt")
                appendWithTimestamp(logBuilder, "AgentId: $agentId")
                appendWithTimestamp(logBuilder, "==================================================")
                appendWithTimestamp(logBuilder, "")

                update(TaskStatus.RUNNING)

                val uiService = EnhancedAIService.getAIServiceForFunction(
                    context,
                    com.ai.assistance.operit.data.model.FunctionType.UI_CONTROLLER
                )

                val agentConfig = AgentConfig(maxSteps = 25)
                val uiTools = ToolGetter.getUITools(context)

                val actionHandler = ActionHandler(
                    context = context,
                    screenWidth = context.resources.displayMetrics.widthPixels,
                    screenHeight = context.resources.displayMetrics.heightPixels,
                    toolImplementations = uiTools
                )

                val agent = PhoneAgent(
                    context = context,
                    config = agentConfig,
                    uiService = uiService,
                    actionHandler = actionHandler,
                    agentId = agentId,
                    cleanupOnFinish = false
                )

                val systemPrompt = buildUiAutomationSystemPrompt()
                var stepIndex = 1
                val pausedState = MutableStateFlow(false)

                withContext(Dispatchers.IO) {
                    val finalMessage = agent.run(
                        task = prompt,
                        systemPrompt = systemPrompt,
                        onStep = { step ->
                            appendStepLog(logBuilder, stepIndex++, step)
                            update()
                        },
                        isPausedFlow = pausedState,
                        targetApp = appName
                    )

                    appendFinalLog(logBuilder, finalMessage)
                    update(TaskStatus.SUCCESS)
                }

            } catch (e: CancellationException) {
                appendWithTimestamp(logBuilder, "🚫 Task cancelled")
                update(TaskStatus.CANCELED)
            } catch (e: Exception) {
                AppLogger.e("AutoGlmParallelVM", "Task error", e)
                appendWithTimestamp(logBuilder, "❌ Error: ${e.message}")
                update(TaskStatus.FAILED)
            } finally {
                taskJobs.remove(appName)
                if (taskJobs.isEmpty()) {
                    _uiState.value = _uiState.value.copy(isRunning = false)
                }
            }
        }

        taskJobs[appName] = job
    }

    /**
     * 取消单个任务
     */
    fun cancelTask(appName: String) {
        taskJobs[appName]?.cancel()
    }

    /**
     * 取消所有任务
     */
    fun cancelAll() {
        taskJobs.values.forEach { it.cancel() }
        taskJobs.clear()
        _uiState.value = _uiState.value.copy(isRunning = false)
    }

    // ======== 以下工具方法，基本与你现有 ViewModel 完全一致 ========

    private fun buildUiAutomationSystemPrompt(): String {
        val useEnglish = LocaleUtils.getCurrentLanguage(context).lowercase().startsWith("en")
        val formattedDate =
            if (useEnglish) {
                SimpleDateFormat("yyyy-MM-dd EEEE", Locale.ENGLISH).format(Date())
            } else {
                val calendar = Calendar.getInstance()
                val sdf = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
                val datePart = sdf.format(Date())
                val weekdayNames =
                    arrayOf("星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六")
                "$datePart ${weekdayNames[calendar.get(Calendar.DAY_OF_WEEK) - 1]}"
            }
        return FunctionalPrompts.buildUiAutomationAgentPrompt(formattedDate, useEnglish)
    }

    private fun appendFinalLog(builder: StringBuilder, finalMessage: String) {
        val time = currentTimeString()
        fun append(line: String) {
            builder.append("[$time] ").appendLine(line)
        }

        append("🎉 ==================================================")
        finalMessage.lines().forEach { line ->
            if (line.isNotBlank()) append(line.trim())
        }
    }

    private fun appendStepLog(builder: StringBuilder, stepIndex: Int, step: StepResult) {
        val time = currentTimeString()
        fun append(line: String) {
            builder.append("[$time] ").appendLine(line)
        }

        append("==================================================")
        step.thinking?.takeIf { it.isNotBlank() }?.let {
            append("💭 思考过程:")
            it.lines().forEach { l -> append(l.trim()) }
        }

        step.action?.let { action ->
            append("🎯 执行动作:")
            append("{ action: ${action.actionName}, meta: ${action.metadata} }")
        }

        step.message?.takeIf { it.isNotBlank() }?.let {
            append(it.trim())
        }

        append("==================================================")
    }

    private fun appendWithTimestamp(builder: StringBuilder, line: String) {
        builder.append("[${currentTimeString()}] ").appendLine(line)
    }

    private fun currentTimeString(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }
}