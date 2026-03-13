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
     * 执行并行任务
     */
    fun executeParallel(appList: String, template: String) {
            val apps = appList.split(Regex("[,，]")) // 英文逗号或中文逗号
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (apps.isEmpty()) return

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
        val prompt: String
        if(StringUtils.isNotEmpty(template)){
            prompt = template.replace("应用", appName)
        }else{
            //使用内置的打车提示词.
            when (appName) {
                "滴滴", "滴滴出行" -> {
                    prompt = """任务指令：
   1. 启动与进入： 打开滴滴出行进入主页,请执行具体操作do(action="Launch", app="滴滴出行").
   2. 触发搜索： 在主页点击“您想去哪儿”中的“您”字,请执行具体操作do(action="Tap", element=[272,509]).
     - 异常处理： 若进入“预约打车”或“立即选车”页面，请点击返回键回到主页重试，确保进入的是带键盘的“目的地搜索页面”。
   3. 输入目的地： 在键盘已经显示意味着输入框已经处于激活状态的目的地搜索页面输入“白马广场”,请执行具体操作do(action="Type", text="白马广场").
   4. 选择目标： 在搜索结果列表中，点击最匹配的选项（通常是第一项）。
   5. 确认已进入了打车选择页面：
     - 严禁操作: 不允许叫车,禁止点击"呼叫x种车型"按钮，不要点击"呼叫x种车型"按钮来完成订单,严禁代替用户下单呼叫。
     - 情况 A - 打车选择页面： 如果页面已经显示了多种车型的实时价格（如：惊喜特价 ¥XX、滴滴快车 ¥XX）和底部的“呼叫x种车型”按钮且终点地址正确，则任务已全部成功完成,不需要再找到和点击“确认下车点”，视为任务成功请执行具体操作finish。
     - 情况 B - 地址确认页： 如果页面底部出现“确认下车点”按钮，请点击它以进入打车选择页面,如果没有就代表不需要点击“确认下车点”。
   任务完成检查：
     - 成功标准： 只要屏幕上出现了实时报价列表且终点地址正确，即视为任务成功。
     - 终态动作： 停留在报价页面等待加载完成即可。
     - 严禁操作： 禁止点击底部的“呼叫x种车型”按钮，严禁代替用户下单呼叫。
   严禁操作: 不允许叫车,禁止点击"呼叫x种车型"按钮，不要点击"呼叫x种车型"按钮来完成订单,严禁代替用户下单呼叫。“确认下车点”按钮不是下单可以点击."""
                }
                "花小猪", "花小猪打车" -> {
                    prompt = """任务指令：
   1. 启动与进入： 打开花小猪打车进入主页,请执行具体操作do(action="Launch", app="花小猪打车").
   2. 触发搜索： 在主页点击“你要去哪儿”中的“你”字.
     - 异常处理： 若进入“预约打车”或“立即选车”页面，请点击返回键回到主页重试，确保进入的是带键盘的“目的地搜索页面”。
   3. 输入目的地： 在键盘已经显示意味着输入框已经处于激活状态的目的地搜索页面输入“白马广场”,请执行具体操作do(action="Type", text="白马广场").
   4. 选择目标： 在搜索结果列表中，点击最匹配的选项（通常是第一项）。
   5. 判断页面状态（关键步骤,不允许叫车）：
     - 严禁操作： 不允许叫车,禁止禁止禁止点击"立即叫车"按钮，不要不要不要点击"立即叫车"按钮来完成订单,严禁代替用户下单呼叫。
     - 情况 A - 地址确认页： 如果页面底部出现“确认下车点”按钮，请点击它以进入下一步,如果没有就代表不需要确认下车点,不需要再找到和点击“确认下车点”,如果没有看到就进行任务完成检查。
     - 情况 B - 报价预览页： 如果页面已经显示了多种车型的实时价格（如：小猪特价 ¥XX、小猪打车 ¥XX）和底部的“立即叫车”按钮且终点地址正确，则任务已全部成功完成,不需要再找到和点击“确认下车点”，即视为任务成功。
   任务完成检查：
     - 成功标准： 只要屏幕上出现了实时报价列表且终点地址正确，即视为任务成功。
     - 终态动作： 停留在报价页面等待加载完成即可。
     - 严禁操作： 不允许叫车,禁止禁止禁止点击"立即叫车"按钮，不要不要不要点击"立即叫车"按钮来完成订单,严禁代替用户下单呼叫。
   严禁操作： 不允许叫车,禁止禁止禁止点击"立即叫车"按钮，不要不要不要点击"立即叫车"按钮来完成订单,严禁代替用户下单呼叫。"""
                }
                "高德", "高德地图" -> {
                    prompt ="""任务指令：
    1. 启动： 打开高德地图进入高德地图主页,请执行具体操作do(action="Launch", app="高德地图").
    2. 在高德地图主页点击蓝色的打车按钮进入打车功能页面
    3. 触发终点搜索： 在打车功能页面点击“你要去哪儿”中的“你”字进入终点搜索页面.
      - 异常处理： 若进入“预约打车”或“立即选车”页面或非终点搜索页面，请点击返回键回到主页重试，确保进入的是带键盘的“终点搜索页面”。
    4. 输入终点： 在键盘已经显示意味着输入框已经处于激活状态的终点搜索页面输入“白马广场”,请执行具体操作do(action="Type", text="白马广场").
    5. 选择目标： 在搜索结果列表中，点击最匹配的选项（通常是第一项）。
    6. 判断页面状态（关键步骤,不允许叫车）：
      - 严禁操作： 不允许叫车,禁止禁止禁止点击"立即打车"按钮，不要不要不要点击"立即打车"按钮来完成订单,严禁代替用户下单呼叫。
      - 情况 A - 终点地址确认页： 如果页面底部出现“确认下车点”按钮，请点击它以进入下一步,如果没有就代表不需要确认下车点,不需要再找到和点击“确认下车点”,如果没有看到就进行任务完成检查。
      - 情况 B - 报价预览页： 如果页面已经显示了多种车型的实时价格（如：小猪特价 ¥XX、小猪打车 ¥XX）和底部的“立即打车”按钮且终点地址正确，则任务已全部成功完成,不需要再找到和点击“确认下车点”，即视为任务成功。
    任务完成检查：
      - 成功标准： 只要屏幕上出现了实时报价列表且终点地址正确，即视为任务成功。
      - 终态动作： 停留在报价页面等待加载完成即可。
      - 严禁操作： 不允许叫车,禁止禁止禁止点击"立即打车"按钮，不要不要不要点击"立即打车"按钮来完成订单,严禁代替用户下单呼叫。
    严禁操作： 不允许叫车,禁止禁止禁止点击"立即打车"按钮，不要不要不要点击"立即打车"按钮来完成订单,严禁代替用户下单呼叫。"""
                }
                else -> {
                    return
                }
            }
        }
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

                val image_save_path =  "/sdcard/.0logs/" +  TimeUtils.getDateTimeStringDirShort()+"_"+appName

                val actionHandler = ActionHandler(
                    context = context,
                    screenWidth = context.resources.displayMetrics.widthPixels,
                    screenHeight = context.resources.displayMetrics.heightPixels,
                    toolImplementations = uiTools,
                    image_save_path = image_save_path,
                )

                val agent = PhoneAgent(
                    context = context,
                    config = agentConfig,
                    uiService = uiService,
                    actionHandler = actionHandler,
                    agentId = agentId,
                    cleanupOnFinish = false,
                    image_save_path = image_save_path,
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