package com.ai.assistance.operit.ui.features.toolbox.screens.autoglmparallel

import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.core.config.FunctionalPrompts
import com.ai.assistance.operit.core.tools.agent.ActionHandler
import com.ai.assistance.operit.core.tools.agent.AgentConfig
import com.ai.assistance.operit.core.tools.agent.PhoneAgent
import com.ai.assistance.operit.core.tools.agent.StepResult
import com.ai.assistance.operit.core.tools.defaultTool.ToolGetter
import com.ai.assistance.operit.ui.common.displays.VirtualDisplayOverlay
import com.ai.assistance.operit.ui.features.toolbox.screens.autoglmride.RideDidiPrompt
import com.ai.assistance.operit.ui.features.toolbox.screens.autoglmride.action_intercepter.RideDidiInterceptor
import com.ai.assistance.operit.ui.features.toolbox.screens.autoglmride.action_intercepter.RideGdInterceptor
import com.ai.assistance.operit.ui.features.toolbox.screens.autoglmride.action_intercepter.RideHailingSafetyInterceptor
import com.ai.assistance.operit.ui.features.toolbox.screens.autoglmride.action_intercepter.RideHxzInterceptor
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.LocaleUtils
import com.ai.assistance.operit.util.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils
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
     * 执行并行任务 有提示词模版template执行提示词,没有就执行打车提示词
     */
    fun executeParallel(appList: String, template: String,start: String="", destination: String="白马广场") {
            val apps = appList.split(Regex("[,，]")) // 英文逗号或中文逗号
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (apps.isEmpty()) {
            Toast.makeText(context, "请输入要操作的应用列表", Toast.LENGTH_SHORT).show()
            return
        }



        cancelAll()
        VirtualDisplayOverlay.resetWindowCounter() //重置窗口位置,从左上开始第一个

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
            tasks = tasks,
            totalSuccessDurationMillis = null,
            slowestSuccessAppName = null
        )

        apps.forEach { app ->
            startSingleTask(app, template,start,destination)
        }
    }


    /**
     * 执行并行任务 有提示词模版template执行提示词,没有就执行打车提示词
     */
    fun executeParallelRide(appList: String, template: String,start: String="", destination: String="白马广场") {
        val apps = appList.split(Regex("[,，]")) // 英文逗号或中文逗号
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (apps.isEmpty()) {
            Toast.makeText(context, "请输入要操作的应用列表", Toast.LENGTH_SHORT).show()
            return
        }

        if (destination.isBlank()) {
            Toast.makeText(context, "请输入终点", Toast.LENGTH_SHORT).show()
            return
        }

        cancelAll()
        VirtualDisplayOverlay.resetWindowCounter() //重置窗口位置,从左上开始第一个

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
            tasks = tasks,
            totalSuccessDurationMillis = null,
            slowestSuccessAppName = null
        )

        apps.forEach { app ->
            startSingleTask(app, template,start,destination)
        }
    }

    /**
     * 启动单个子任务
     */
    private fun startSingleTask(appName: String, template: String,start: String="", destination: String) {

        // 我想打车到白马广场,帮我打开应用分别比一比价格,将 template中的应用替换为 appName赋值到prompt
        var prompt: String
        var isOnlyOpenApp = false
        if(StringUtils.isNotEmpty(template)){ //template不为空
            if(template.equals("打开应用")){ //1.打开应用指令,只用代码打开应用,不使用ai.
                isOnlyOpenApp = true
            }
            prompt = template.replace("应用", appName) //2.其他指令,替换应用为实际参数
        }else{
            //使用内置的打车提示词. //template为空
            val hasStart = start.isNotEmpty() // 判断是否传入了有效的起点
            when (appName) {
                "滴滴", "滴滴出行" -> {
                    prompt = if (hasStart) RideDidiPrompt.ride_didi_start_use else RideDidiPrompt.ride_didi_use
                }
                "花小猪", "花小猪打车" -> {
                    prompt = if (hasStart) RideDidiPrompt.ride_hxz_start_use else RideDidiPrompt.ride_hxz_use
                }
                "高德", "高德地图" -> {
                    prompt = if (hasStart) RideDidiPrompt.ride_gd_start_use else RideDidiPrompt.ride_gd_use
                }
                else -> {
                    return
                }
            }

        }

        // 统一替换终点
        prompt = prompt.replace("{destination}", destination)
        // 如果有起点，统一替换起点
        if (start.isNotEmpty()) {
            prompt = prompt.replace("{start}", start)
        }

        val agentId = appName +"_" + UUID.randomUUID().toString().take(4)

        val job = viewModelScope.launch {
            val taskStartAtMs = SystemClock.elapsedRealtime()
            val logBuilder = StringBuilder()

            fun update(status: TaskStatus? = null, durationMillis: Long? = null) {
                _uiState.value = _uiState.value.copy(
                    tasks = _uiState.value.tasks.map {
                        if (it.appName == appName) {
                            it.copy(
                                status = status ?: it.status,
                                durationMillis = durationMillis ?: it.durationMillis,
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

//                val agentConfig = AgentConfig(maxSteps = 1) //最大步数设置为 1,就测应用是否正常打开 预热没打开就再次打开一次
                val agentConfig = AgentConfig(maxSteps = 10) //配置2-最大步数设置,打车一般 5-6步,最大 10步就行
                val uiTools = ToolGetter.getUITools(context)

                val image_save_path =  "/sdcard/.0logs/" +  TimeUtils.getDateTimeStringDirShort()+"_"+appName

                val actionHandler = ActionHandler(
                    context = context,
                    screenWidth = context.resources.displayMetrics.widthPixels,
                    screenHeight = context.resources.displayMetrics.heightPixels,
                    toolImplementations = uiTools,
                    image_save_path = image_save_path,
                )

                //按需也就是应用加载对应拦截器
                // 动态构建拦截器列表
                val businessInterceptors = buildList {
                    // 1. 添加通用的安全风控拦截器 (兜底)
                    add(RideHailingSafetyInterceptor())

                    // 2. 根据 appName 动态添加对应的坐标修正宏指令拦截器
                    when (appName) {
                        "滴滴", "滴滴出行" -> {
                            add(RideDidiInterceptor(start = start, destination = destination))
                        }
                        "花小猪", "花小猪打车" -> {
                            add(RideHxzInterceptor(start = start, destination = destination))
                        }
                        "高德", "高德地图" -> {
                            add(RideGdInterceptor(start = start, destination = destination))
                        }
                    }
                }

                val agent = PhoneAgent(
                    context = context,
                    config = agentConfig,
                    uiService = uiService,
                    actionHandler = actionHandler,
                    agentId = agentId,
                    cleanupOnFinish = false,
                    image_save_path = image_save_path,
                    interceptors = businessInterceptors,
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
                        targetApp = appName,
                        isOnlyOpenApp = isOnlyOpenApp,
                    )

                    appendFinalLog(logBuilder, finalMessage)
                    val durationMillis = SystemClock.elapsedRealtime() - taskStartAtMs
                    update(TaskStatus.SUCCESS, durationMillis = durationMillis)
                }

            } catch (e: CancellationException) {
                appendWithTimestamp(logBuilder, "🚫 Task cancelled")
                val durationMillis = SystemClock.elapsedRealtime() - taskStartAtMs
                update(TaskStatus.CANCELED, durationMillis = durationMillis)
            } catch (e: Exception) {
                AppLogger.e("AutoGlmParallelVM", "Task error", e)
                appendWithTimestamp(logBuilder, "❌ Error: ${e.message}")
                val durationMillis = SystemClock.elapsedRealtime() - taskStartAtMs
                update(TaskStatus.FAILED, durationMillis = durationMillis)
            } finally {
                taskJobs.remove(appName) //任务列表中清除完成的任务
                if (taskJobs.isEmpty()) {  //任务全部完成,任务列表为空,
                    if (_uiState.value.isRunning) {
                        val successTasks = _uiState.value.tasks
                            .filter { it.status == TaskStatus.SUCCESS && it.durationMillis != null }
                        val slowestSuccessTask = successTasks.maxByOrNull { it.durationMillis!! }

                        _uiState.value = _uiState.value.copy(
                            isRunning = false,
                            totalSuccessDurationMillis = slowestSuccessTask?.durationMillis,
                            slowestSuccessAppName = slowestSuccessTask?.appName
                        )
                        //播放任务结果声音
                        SoundPlayer.playParallelBatchOutcomeIfNeeded(
                            context.applicationContext,
                            _uiState.value.tasks
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(isRunning = false)
                    }
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
        _uiState.value = _uiState.value.copy(
            isRunning = false,
            totalSuccessDurationMillis = null,
            slowestSuccessAppName = null
        )
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