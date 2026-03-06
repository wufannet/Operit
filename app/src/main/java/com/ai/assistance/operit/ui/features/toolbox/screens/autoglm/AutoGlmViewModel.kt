package com.ai.assistance.operit.ui.features.toolbox.screens.autoglm

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.core.config.FunctionalPrompts
import com.ai.assistance.operit.core.tools.agent.ActionHandler
import com.ai.assistance.operit.core.tools.agent.AgentConfig
import com.ai.assistance.operit.core.tools.agent.ParsedAgentAction
import com.ai.assistance.operit.core.tools.agent.PhoneAgent
import com.ai.assistance.operit.core.tools.agent.ShowerController
import com.ai.assistance.operit.core.tools.agent.StepResult
import com.ai.assistance.operit.core.tools.defaultTool.ToolGetter
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardUITools
import com.ai.assistance.operit.services.FloatingChatService
import com.ai.assistance.operit.ui.common.displays.VirtualDisplayOverlay
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.LocaleUtils
import com.ai.assistance.operit.util.LogFileUtils
import com.ai.assistance.operit.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AutoGlmViewModel(private val context: Context) : ViewModel() {

    private var executionJob: Job? = null

    private val sessionAgentId: String = java.util.UUID.randomUUID().toString().take(8)
    val agentId = "default"

    private val _uiState = MutableStateFlow(AutoGlmUiState())
    val uiState: StateFlow<AutoGlmUiState> = _uiState.asStateFlow()
    private var actionHandler: ActionHandler
    // 定义格式化器，复用以提高性能
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    //TODO 串行执行executeTask多次,需要保证每次executeTask中的executionJob执行完成再执行下一个
    fun executeTaskBatch(task: String, batchSize: Int=50) {
        Toast.makeText(context, "executeTaskBatch batchSize $batchSize", Toast.LENGTH_SHORT).show()
    }
    //TODO 取消executeTaskBatch任务
    fun executeTaskBatchCancel() {
        Toast.makeText(context, "executeTaskBatchCancel", Toast.LENGTH_SHORT).show()
    }

    fun executeTask(task: String) {
        if (task.isBlank()) return

        executionJob?.cancel()

        executionJob = viewModelScope.launch {
            // 记录任务开始时间
            val programStartTime = LocalDateTime.now()
            _uiState.value = AutoGlmUiState(isLoading = true, log = "Initializing agent...")

            try {
                val uiService = EnhancedAIService.getAIServiceForFunction(context, com.ai.assistance.operit.data.model.FunctionType.UI_CONTROLLER)
                val systemPrompt = buildUiAutomationSystemPrompt()

                val agentConfig = AgentConfig(maxSteps = 15)
                // Get the real UI tools implementation based on the user's preferred permission level.
                val uiTools = ToolGetter.getUITools(context)
                val image_save_path =  "/sdcard/Download/Operit/logs/" +  TimeUtils.getDateTimeStringDirShort()
//                val image_save_path =  "/01logs/" +  TimeUtils.getDateTimeStringDirShort()
                val actionHandler = ActionHandler(
                    context = context,
                    screenWidth = context.resources.displayMetrics.widthPixels,
                    screenHeight = context.resources.displayMetrics.heightPixels,
                    // Use the real UI tools implementation to ensure Tap/Swipe/PressKey/Screenshot actions are executed.
                    toolImplementations = uiTools,
                    image_save_path = image_save_path,
                )


//                AppLogger.d("AutoGlmViewModel", "image_save_path: $image_save_path")
                val agent = PhoneAgent(
                    context = context,
                    config = agentConfig,
                    uiService = uiService, // Directly pass the specialized AIService
                    actionHandler = actionHandler,
//                    agentId = sessionAgentId,
                    agentId =  "default",
                    cleanupOnFinish = false,
                    image_save_path = image_save_path,
                )

                val logBuilder = StringBuilder()

                // Header section，尽量贴近官方 CLI
                appendWithTimestamp(logBuilder, "============================")
                appendWithTimestamp(logBuilder, "Task: $task")
                appendWithTimestamp(logBuilder, "Max Steps: ${agentConfig.maxSteps}")
                appendWithTimestamp(logBuilder, "============================")
                appendWithTimestamp(logBuilder, "")

                // 先把头部显示出来
                _uiState.value = AutoGlmUiState(isLoading = true, log = logBuilder.toString())

                var stepIndex = 1
                val pausedState = kotlinx.coroutines.flow.MutableStateFlow(false)

                withContext(Dispatchers.IO) {
                    val finalMessage = agent.run(
                        task = task,
                        systemPrompt = systemPrompt,
                        onStep = { stepResult: StepResult ->
                            val stepBuilder = StringBuilder()
                            appendStepLog(stepBuilder, stepIndex, stepResult)
                            logBuilder.append(stepBuilder)
                            val save_log: StringBuilder
                            if (stepIndex == 1){
                                save_log = logBuilder
                            }else{
                                save_log = stepBuilder
                            }
                            val log = logBuilder.toString().trimEnd()
                            stepIndex++
                            _uiState.value = AutoGlmUiState(
                                isLoading = true,
                                log = log
                            )

                            LogFileUtils.saveLogAsync(
                                logContent = save_log,
                                filePath = "${image_save_path}/app.log", // Android 私有目录（无需权限）
                                append = true
                            ) { success, msg ->
                                // 回调处理结果（已切回主线程，可更新UI）
                                if (success) {
                                    // Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                                } else {
                                    // Log.e("LogFile", msg)
                                }
                            }

                            LogFileUtils.saveLogAsync(
                                logContent = save_log,
                                filePath = "${image_save_path}/app.txt", // Android 私有目录（无需权限）
                                append = true
                            ) { success, msg ->
                                // 回调处理结果（已切回主线程，可更新UI）
                                if (success) {
                                    // Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                                } else {
                                    // Log.e("LogFile", msg)
                                }
                            }
                        },
                        isPausedFlow = pausedState
                    )

                    // 追加最终结果，使用 🎉 / ✅ 样式
                    val finalTime = currentTimeString()
                    fun appendFinal(line: String) {
                        logBuilder.append("[")
                        logBuilder.append(finalTime)
                        logBuilder.append("] ")
                        logBuilder.appendLine(line)
                    }

                    appendFinal("🎉 ==================================================")

                    val finalLines = finalMessage.lines()
                    if (finalLines.isNotEmpty()) {
                        appendFinal("✅ 任务完成: ${finalLines.first().trim()}")
                        finalLines.drop(1).forEach { line ->
                            if (line.isNotBlank()) {
                                appendFinal(line.trim())
                            }
                        }
                    }

                    //添加任务耗时统计
                    val programEndTime = LocalDateTime.now()
                    // 计算耗时
                    val durationSeconds = ChronoUnit.SECONDS.between(programStartTime, programEndTime)
                    val durationMillis = ChronoUnit.MILLIS.between(programStartTime, programEndTime)
                    val durationTotalSeconds = durationMillis / 1000.0

                    // 格式化耗时（时分秒格式）
                    val hours = durationSeconds / 3600
                    val minutes = (durationSeconds % 3600) / 60
                    val seconds = durationSeconds % 60
                    val formattedDuration = String.format("%02d:%02d", minutes, seconds)

                    val timeBuild = StringBuilder()
                    timeBuild.appendLine("\n任务开始时间: ${programStartTime.format(formatter)}")
                    timeBuild.appendLine("任务结束时间: ${programEndTime.format(formatter)}")
                    timeBuild.appendLine("任务运行耗时: $formattedDuration")
                    timeBuild.appendLine("任务运行耗时秒: $durationSeconds")
                    logBuilder.append(timeBuild)
                    LogFileUtils.saveLogAsync(
                        logContent = timeBuild,
                        filePath = "${image_save_path}/app.log", // Android 私有目录（无需权限）
                        append = true
                    ) { success, msg ->
                        // 回调处理结果（已切回主线程，可更新UI）
                        if (success) {
                            // Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        } else {
                            // Log.e("LogFile", msg)
                        }
                    }


                    _uiState.value = AutoGlmUiState(
                        isLoading = false,
                        log = logBuilder.toString().trimEnd()
                    )
                }

            } catch (e: Exception) {
                AppLogger.e("AutoGlmViewModel", "Error executing task", e)
                _uiState.value = AutoGlmUiState(isLoading = false, log = "Error: ${e.message}")
            }
        }
    }

    fun cancelTask() {
        executionJob?.cancel()
        _uiState.value = AutoGlmUiState(isLoading = false, log = _uiState.value.log + "[Execution Cancelled by User]")
    }

    private fun buildUiAutomationSystemPrompt(): String {
        val useEnglish = LocaleUtils.getCurrentLanguage(context).lowercase().startsWith("en")
        val formattedDate =
            if (useEnglish) {
                SimpleDateFormat("yyyy-MM-dd EEEE", Locale.ENGLISH).format(Date())
            } else {
                val calendar = Calendar.getInstance()
                val sdf = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
                val datePart = sdf.format(Date())
                val weekdayNames = arrayOf("星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六")
                val weekday = weekdayNames[calendar.get(Calendar.DAY_OF_WEEK) - 1]
                "$datePart $weekday"
            }
        return FunctionalPrompts.buildUiAutomationAgentPrompt(formattedDate, useEnglish)
    }
    
    private fun extractTagContent(text: String, tag: String): String? {
        val pattern = Regex("""<$tag>(.*?)</$tag>""", RegexOption.DOT_MATCHES_ALL)
        return pattern.find(text)?.groupValues?.getOrNull(1)?.trim()
    }

    private fun appendStepLog(builder: StringBuilder, stepIndex: Int, stepResult: StepResult) {
        val time = currentTimeString()

        fun append(line: String) {
            builder.append("[")
            builder.append(time)
            builder.append("] ")
            builder.appendLine(line)
        }

        // 步骤分隔线
        append("\n======step start ${stepIndex}======")

        // 💭 思考过程
        stepResult.thinking?.takeIf { it.isNotBlank() }?.let { thinking ->
            append("💭 思考过程 step ${stepIndex}:")
            append("--------------------------------------------------")
            thinking.trim().lines().forEach { line ->
                if (line.isNotBlank()) {
                    append(line.trim())
                }
            }
        }

        // 🎯 执行动作
        stepResult.action?.let { action ->
            append("--------------------------------------------------")
            append("🎯 执行动作 step ${stepIndex}:")

            val jsonLines = mutableListOf<String>()
            action.actionName?.let { name ->
                jsonLines += "\"action\": \"$name\""
            }
            jsonLines += "\"_metadata\": \"${action.metadata}\""
            action.fields.forEach { (key, value) ->
                if (key != "action") {
                    jsonLines += "\"$key\": \"$value\""
                }
            }

            append("{")
            jsonLines.forEachIndexed { index, line ->
                val suffix = if (index == jsonLines.lastIndex) "" else ","
                append("  $line$suffix")
            }
            append("}")
        }

        // 对于非 finish 步骤，如果有额外消息则补充一段说明
        stepResult.message
            ?.takeIf { it.isNotBlank() && stepResult.action?.metadata != "finish" }
            ?.let { msg ->
                append("--------------------------------------------------")
                msg.trim().lines().forEach { line ->
                    if (line.isNotBlank()) {
                        append(line.trim())
                    }
                }
            }

        append("======step end ${stepIndex}======\n")
    }

    private fun currentTimeString(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun appendWithTimestamp(builder: StringBuilder, line: String) {
        val time = currentTimeString()
        builder.append("[")
        builder.append(time)
        builder.append("] ")
        builder.appendLine(line)
    }

    init {
        val uiTools = ToolGetter.getUITools(context)

        actionHandler = ActionHandler(
            context = context,
            screenWidth = context.resources.displayMetrics.widthPixels,
            screenHeight = context.resources.displayMetrics.heightPixels,
            toolImplementations = uiTools
        )
//        actionHandler.setAgentId(agentId)
    }

//    private fun ensureActionHandler() {
//        if (actionHandler != null) return
//
//        val uiTools = ToolGetter.getUITools(context)
//
//        actionHandler = ActionHandler(
//            context = context,
//            screenWidth = context.resources.displayMetrics.widthPixels,
//            screenHeight = context.resources.displayMetrics.heightPixels,
//            toolImplementations = uiTools
//        )
//        actionHandler.setAgentId(agentId)
//    }

    fun onStartApp(appName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            actionHandler.executeAgentAction(
                ParsedAgentAction(
                    metadata = "do",
                    actionName = "Launch",
                    fields = mapOf(
                        "action" to "Launch",
                        "app" to appName
                    )
                )
            )
        }
    }

    fun onSwitchDisplay(app: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val desktopPackage = resolveAppPackageName(app)
//            val desktopPackage = "com.sdu.didi.psnger"
//            val desktopPackage = packageName
//            delay(8000)
            val desktopLaunched = ShowerController.launchApp(agentId, desktopPackage)
            if (desktopLaunched) {
                try {
                    VirtualDisplayOverlay.getInstance(context, agentId).updateCurrentAppPackageName(desktopPackage)
                } catch (_: Exception) {}
                useShowerIndicatorForAgent(context, agentId)
            } else {
                AppLogger.e("onSwitchDisplay","Failed to launch on Shower virtual display desktopLaunched")
            }
        }
    }

    private suspend fun useShowerIndicatorForAgent(context: Context, agentId: String) {
        try {
            val overlay = VirtualDisplayOverlay.getInstance(context, agentId)
            overlay.setShowerBorderVisible(true)
        } catch (e: Exception) {
            AppLogger.e("PhoneAgent", "[$agentId] Error enabling Shower border indicator", e)
        }
        val floatingService = FloatingChatService.getInstance()
        floatingService?.setStatusIndicatorVisible(false)
    }

    private suspend fun resolveAppPackageName(app: String): String {
        val trimmed = app.trim()
        val lowered = trimmed.lowercase(Locale.getDefault())
        fun lookup(): String? = StandardUITools.APP_PACKAGES[app] ?: StandardUITools.APP_PACKAGES[trimmed] ?: StandardUITools.APP_PACKAGES[lowered]
        val directHit = lookup()
        if (directHit != null) return directHit
        withContext(Dispatchers.IO) { StandardUITools.scanAndAddInstalledApps(context) }
        return lookup() ?: trimmed
    }
}

data class AutoGlmUiState(
    val isLoading: Boolean = false,
    val log: String = "Ready to execute task."
)
