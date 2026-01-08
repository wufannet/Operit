package com.ai.assistance.operit.core.tools.agent // 定义代理工具所属的包名

import android.content.ClipData // 导入剪贴板数据类
import android.content.ClipboardManager // 导入剪贴板管理器
import android.content.Context // 导入 Android 上下文环境
import android.graphics.Bitmap // 导入位图处理类
import android.graphics.BitmapFactory // 导入位图解码工厂
import android.view.KeyEvent // 导入键盘事件类
import com.ai.assistance.operit.api.chat.llmprovider.AIService // 导入 AI 服务接口
import com.ai.assistance.operit.core.tools.defaultTool.ToolGetter // 导入工具获取器
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardUITools // 导入标准 UI 工具
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel // 导入权限等级枚举
import com.ai.assistance.operit.core.tools.system.ShizukuAuthorizer // 导入 Shizuku 授权器
import com.ai.assistance.operit.data.model.AITool // 导入 AI 工具数据模型
import com.ai.assistance.operit.data.model.ToolParameter // 导入工具参数模型
import com.ai.assistance.operit.data.model.ToolResult // 导入工具执行结果模型
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager // 导入显示首选项管理器
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences // 导入权限首选项
import com.ai.assistance.operit.services.FloatingChatService // 导入悬浮窗服务
import com.ai.assistance.operit.ui.common.displays.UIAutomationProgressOverlay // 导入自动化进度覆盖层
import com.ai.assistance.operit.ui.common.displays.VirtualDisplayOverlay // 导入虚拟显示覆盖层
import com.ai.assistance.operit.util.AppLogger // 导入应用日志工具
import com.ai.assistance.operit.util.ImagePoolManager // 导入图片池管理器
import kotlinx.coroutines.CancellationException // 导入协程取消异常
import kotlinx.coroutines.Dispatchers // 导入协程调度器
import kotlinx.coroutines.Job // 导入协程作业类
import kotlinx.coroutines.currentCoroutineContext // 获取当前协程上下文
import kotlinx.coroutines.delay // 导入协程延迟函数
import kotlinx.coroutines.flow.MutableStateFlow // 导入可变状态流
import kotlinx.coroutines.flow.StateFlow // 导入状态流
import kotlinx.coroutines.withContext // 导入上下文切换函数
import java.io.File // 导入文件类
import java.io.FileOutputStream // 导入文件输出流
import java.util.Locale // 导入语言区域类

/** Configuration for the PhoneAgent. */ // 手机代理配置类注释
data class AgentConfig( // 代理配置数据类
    val maxSteps: Int = 20 // 任务执行的最大步数限制，默认为20步
) // 结束数据类定义

/** Result of a single agent step. */ // 单步执行结果注释
data class StepResult( // 单步结果数据类
    val success: Boolean, // 执行是否成功
    val finished: Boolean, // 任务是否已完全结束
    val action: ParsedAgentAction?, // 解析出的代理动作
    val thinking: String?, // AI 的思考过程文本
    val message: String? = null // 结果描述消息
) // 结束数据类定义

/** Parsed action from the model's response. */ // 动作解析模型注释
data class ParsedAgentAction( // 解析后的动作数据类
    val metadata: String, // 动作元数据类型（如 do 或 finish）
    val actionName: String?, // 动作具体名称（如 Tap, Swipe）
    val fields: Map<String, String> // 动作携带的参数键值对
) // 结束数据类定义

/**
 * AI-powered agent for automating Android phone interactions.
 *
 * The agent uses a vision-language model to understand screen content
 * and decide on actions to complete user tasks.
 */ // PhoneAgent 类详细功能说明注释
class PhoneAgent( // 手机交互代理主类
    private val context: Context, // Android 环境上下文
    private val config: AgentConfig, // 代理配置对象
    private val uiService: AIService,  // AI 语言模型服务
    private val actionHandler: ActionHandler, // 动作执行处理器
    val agentId: String = "default", // 代理唯一标识 ID
    private val cleanupOnFinish: Boolean = (agentId != "default"), // 结束时是否清理环境
) { // 类体开始
    private var _stepCount = 0 // 内部步数计数器
    val stepCount: Int // 对外暴露的步数只读属性
        get() = _stepCount // 返回当前的执行步数

    private val _contextHistory = mutableListOf<Pair<String, String>>() // 存储聊天上下文历史
    val contextHistory: List<Pair<String, String>> // 对外暴露的历史记录列表
        get() = _contextHistory.toList() // 返回历史记录的副本

    private var pauseFlow: StateFlow<Boolean>? = null // 暂停状态流引用

    init { // 初始化块
        actionHandler.setAgentId(agentId) // 为处理器设置代理 ID
    } // 结束初始化

    private suspend fun awaitIfPaused() { // 处理暂停逻辑的挂起函数
        val flow = pauseFlow ?: return // 如果没有暂停流则直接返回
        if (!flow.value) { // 如果当前未暂停
            return // 直接返回继续执行
        } // 结束判断
        AppLogger.d("PhoneAgent", "[$agentId] awaitIfPaused: entering pause loop, delay starting") // 打印进入暂停日志
        try { // 开启重试循环
            while (flow.value) { // 当处于暂停状态时
                delay(200) // 每 200 毫秒检查一次
            } // 结束循环
        } finally { // 退出时执行
            AppLogger.d("PhoneAgent", "[$agentId] awaitIfPaused: exiting pause loop") // 打印结束暂停日志
        } // 结束 try-finally
    } // 结束函数

    private fun hasShowerDisplay(logMessageSuffix: String): Boolean { // 检查 Shower 虚拟显示状态
        return try { // 尝试查询状态
            ShowerController.getDisplayId(agentId) != null || ShowerController.getVideoSize(agentId) != null // 判断显示 ID 或视频尺寸是否存在
        } catch (e: Exception) { // 捕获查询异常
            AppLogger.e("PhoneAgent", "[$agentId] $logMessageSuffix", e) // 记录错误日志
            false // 发生错误视为不存在
        } // 结束 try-catch
    } // 结束函数

    private suspend fun prewarmShowerIfNeeded( // 预热虚拟显示环境
        hasShowerDisplayAtStart: Boolean, // 启动时是否已有显示
        targetApp: String? // 目标应用包名
    ): Pair<Boolean, String?> { // 返回预热状态及错误信息
        if (hasShowerDisplayAtStart) return Pair(true, null) // 如果已启动则直接成功
        val targetAppForPrewarm = targetApp?.takeIf { it.isNotBlank() } ?: return Pair(false, null) // 获取有效目标应用

        val preferredLevel = androidPermissionPreferences.getPreferredPermissionLevel() // 获取首选权限等级
            ?: AndroidPermissionLevel.STANDARD // 默认标准权限
        var isAdbOrHigher = when (preferredLevel) { // 判断是否为高级权限（ADB/Root等）
            AndroidPermissionLevel.DEBUGGER, // 调试模式
            AndroidPermissionLevel.ADMIN, // 管理员模式
            AndroidPermissionLevel.ROOT -> true // Root 模式返回 true
            else -> false // 其他返回 false
        } // 结束 when 判断

        if (isAdbOrHigher) { // 如果是高级权限
            val experimentalEnabled = try { // 检查实验性虚拟显示开关
                DisplayPreferencesManager.getInstance(context).isExperimentalVirtualDisplayEnabled() // 读取设置
            } catch (_: Exception) { // 异常处理
                true // 默认开启
            } // 结束 try
            if (!experimentalEnabled) { // 如果未启用实验性功能
                isAdbOrHigher = false // 降级为普通权限处理
            } // 结束判断
        } // 结束权限检查

        if (!isAdbOrHigher) return Pair(false, null) // 权限不足无法预热

        if (preferredLevel == AndroidPermissionLevel.DEBUGGER) { // 针对 Shizuku 的特殊检查
            val isShizukuRunning = ShizukuAuthorizer.isShizukuServiceRunning() // 检查服务运行状态
            val hasShizukuPermission = if (isShizukuRunning) ShizukuAuthorizer.hasShizukuPermission() else false // 检查授权
            if (!isShizukuRunning || !hasShizukuPermission) { // 缺失条件
                return Pair(false, "Shizuku 不可用，无法启动虚拟屏幕。") // 返回错误提示
            } // 结束 Shizuku 检查
        } // 结束权限逻辑

        AppLogger.d( // 打印预热开始日志
            "PhoneAgent", // 标签
            "[$agentId] run: prewarming Shower virtual display via Launch(app='$targetAppForPrewarm')" // 消息内容
        ) // 结束打印
        val prewarmResult = try { // 执行启动动作预热
            actionHandler.executeAgentAction( // 调用处理器执行动作
                ParsedAgentAction( // 构造启动动作
                    metadata = "do", // 类型为执行
                    actionName = "Launch", // 动作为启动
                    fields = mapOf( // 参数映射
                        "action" to "Launch", // 动作名
                        "app" to targetAppForPrewarm // 应用名
                    ) // 结束 Map
                ) // 结束 Action 构造
            ) // 结束调用
        } catch (e: Exception) { // 捕获执行异常
            if (e is CancellationException) throw e // 如果是主动取消则抛出
            return Pair(false, "虚拟屏幕预启动失败：${e.message}") // 返回失败信息
        } // 结束 try-catch

        val hasShowerAfterPrewarm = hasShowerDisplay("Error checking Shower state after prewarm") // 再次检查显示状态
        if (!hasShowerAfterPrewarm) { // 如果预热后仍未启动
            return Pair(false, prewarmResult.message ?: "虚拟屏幕未能启动，已中止以避免在主屏幕执行操作。") // 返回详细错误
        } // 结束判断

        return Pair(true, null) // 预热成功
    } // 结束函数

    /**
     * Run the agent to complete a task.
     *
     * @param task Natural language description of the task.
     * @param systemPrompt System prompt for the UI automation agent.
     * @param onStep Optional callback invoked after each step with the StepResult.
     * @return Final message from the agent.
     */ // 运行代理核心逻辑注释
    suspend fun run( // 执行任务的主入口函数
        task: String, // 用户输入的自然语言任务
        systemPrompt: String, // 系统提示词
        onStep: (suspend (StepResult) -> Unit)? = null, // 每步回调
        isPausedFlow: StateFlow<Boolean>? = null, // 外部传入的暂停流
        targetApp: String? = null // 目标应用
    ): String { // 返回任务执行结果字符串
        val floatingService = FloatingChatService.getInstance() // 获取悬浮窗服务实例
        val job = currentCoroutineContext()[Job] // 获取当前协程的作业对象

        if (job != null) { // 如果存在作业对象
            PhoneAgentJobRegistry.register(agentId, job) // 将作业注册到全局注册表以便管理
        } else { // 如果没有协程环境
            AppLogger.w("PhoneAgent", "[$agentId] run: no Job in coroutineContext, registry disabled") // 打印警告日志
        } // 结束判断

        var hasShowerDisplayAtStart = hasShowerDisplay("Error checking Shower virtual display state") // 获取启动时的虚拟显示状态
        val (prewarmedShowerDisplay, prewarmError) = prewarmShowerIfNeeded(hasShowerDisplayAtStart, targetApp) // 执行预热逻辑
        if (prewarmError != null) { // 如果预热报错
            return prewarmError  // 直接返回错误信息
        } // 结束错误拦截
        hasShowerDisplayAtStart = prewarmedShowerDisplay // 更新显示状态标记

        var useShowerUi = hasShowerDisplayAtStart // 决定是否使用虚拟屏幕 UI 交互
        val progressOverlay = UIAutomationProgressOverlay.getInstance(context) // 获取进度覆盖层实例
        var showerOverlay: VirtualDisplayOverlay? = if (useShowerUi) try { // 如果使用虚拟屏，尝试获取覆盖层
            VirtualDisplayOverlay.getInstance(context, agentId) // 获取实例
        } catch (e: Exception) { // 异常捕获
            AppLogger.e("PhoneAgent", "[$agentId] Error getting VirtualDisplayOverlay instance", e) // 记录错误
            null // 失败置空
        } else null // 否则不使用

        val pausedMutable = isPausedFlow as? MutableStateFlow<Boolean> // 尝试转换为可变流以便更新状态

        try { // 进入任务主循环
            // Setup UI for agent run: hide window, then choose indicator based on whether Shower virtual display is active
            floatingService?.setFloatingWindowVisible(false) // 隐藏悬浮对话窗口
            if (useShowerUi) { // 如果使用虚拟屏幕
                useShowerIndicatorForAgent(context, agentId) // 开启虚拟屏幕边框指示器
            } else { // 如果在真实屏幕执行
                useFullscreenStatusIndicatorForAgent() // 开启全屏状态指示器
            } // 结束分支
            if (useShowerUi) { // 初始化虚拟屏控制 UI
                showerOverlay?.showAutomationControls( // 显示控制面板
                    totalSteps = config.maxSteps, // 传递最大步数
                    initialStatus = "思考中...", // 初始状态显示
                    onTogglePauseResume = { isPaused -> pausedMutable?.value = isPaused }, // 绑定暂停切换
                    onExit = { // 退出回调
                        PhoneAgentJobRegistry.cancelAgent(agentId, "User cancelled UI automation") // 注销任务
                        job?.cancel(CancellationException("User cancelled UI automation")) // 取消协程
                    } // 结束回调定义
                ) // 结束显示调用
            } else { // 初始化普通进度 UI
                progressOverlay.show( // 显示普通进度条
                    config.maxSteps, // 最大步数
                    "Thinking...", // 提示语
                    onCancel = { // 取消回调
                        PhoneAgentJobRegistry.cancelAgent(agentId, "User cancelled UI automation") // 注销
                        job?.cancel(CancellationException("User cancelled UI automation")) // 取消
                    }, // 结束回调
                    onToggleTakeOver = { isPaused -> pausedMutable?.value = isPaused } // 接管/暂停
                ) // 结束显示调用
            } // 结束 UI 分支

            reset() // 重置步数和历史记录
            _contextHistory.add("system" to systemPrompt) // 注入系统提示词到历史
            pauseFlow = isPausedFlow // 绑定暂停流

            // First step with user prompt
            AppLogger.d("PhoneAgent", "[$agentId] run: starting first step for task='$task', hasShowerDisplayAtStart=$hasShowerDisplayAtStart") // 启动日志
            awaitIfPaused() // 检查暂停
            var result = _executeStep(task, isFirst = true) // 执行第一步（包含用户原始需求）
            val firstAction = result.action // 获取首步产生的动作
            val firstStatusText = when { // 计算首步显示文案
                result.finished -> result.message ?: "已完成" // 结束文案
                firstAction != null && firstAction.metadata == "do" -> { // 执行文案
                    val actionName = firstAction.actionName ?: "" // 获取动作名
                    if (actionName.isNotEmpty()) "执行 ${actionName} 中..." else "执行操作中..." // 组装文案
                } // 结束 do 分支
                else -> "思考中..." // 默认文案
            } // 结束 when 判断

            if (!useShowerUi) { // 如果初始未使用虚拟屏，首步后重新检查状态
                val hasShowerNow = try { // 重新检测
                    ShowerController.getDisplayId(agentId) != null || ShowerController.getVideoSize(agentId) != null // 检查 ID 或尺寸
                } catch (e: Exception) { // 异常捕获
                    AppLogger.e("PhoneAgent", "[$agentId] Error re-checking Shower virtual display state after first step", e) // 记录
                    false // 错误视为无
                } // 结束 try-catch

                if (hasShowerNow) { // 如果检测到虚拟屏已上线
                    useShowerUi = true // 切换标志位
                    try { // 隐藏旧指示器
                        progressOverlay.hide() // 隐藏
                    } catch (_: Exception) { // 忽略
                    } // 结束 try

                    try { // 尝试切换到虚拟屏覆盖层
                        showerOverlay = VirtualDisplayOverlay.getInstance(context, agentId) // 获取实例
                    } catch (e: Exception) { // 捕获
                        AppLogger.e("PhoneAgent", "[$agentId] Error getting VirtualDisplayOverlay instance when switching (first step)", e) // 记录
                        showerOverlay = null // 失败置空
                    } // 结束 try-catch

                    if (showerOverlay != null) { // 如果成功获得虚拟屏控制权
                        useShowerIndicatorForAgent(context, agentId) // 更新 UI 指示器
                        showerOverlay?.showAutomationControls( // 初始化虚拟屏面板
                            totalSteps = config.maxSteps, // 最大步数
                            initialStatus = firstStatusText, // 当前状态
                            onTogglePauseResume = { isPaused -> pausedMutable?.value = isPaused }, // 回调
                            onExit = { // 退出回调
                                PhoneAgentJobRegistry.cancelAgent(agentId, "User cancelled UI automation") // 取消注册
                                job?.cancel(CancellationException("User cancelled UI automation")) // 取消任务
                            } // 结束回调
                        ) // 结束调用
                        showerOverlay?.updateAutomationProgress(stepCount, config.maxSteps, firstStatusText) // 更新进度
                    } else { // 切换失败则回退到普通覆盖层
                        progressOverlay.show( // 重新显示
                            config.maxSteps, // 最大步数
                            "Thinking...", // 提示
                            onCancel = { // 取消
                                PhoneAgentJobRegistry.cancelAgent(agentId, "User cancelled UI automation") // 取消注册
                                job?.cancel(CancellationException("User cancelled UI automation")) // 取消
                            }, // 结束回调
                            onToggleTakeOver = { isPaused -> pausedMutable?.value = isPaused } // 暂停回调
                        ) // 结束显示
                        progressOverlay.updateProgress(stepCount, config.maxSteps, firstStatusText) // 更新进度
                        useShowerUi = false // 标志位置回
                    } // 结束 showerOverlay 判断
                } else { // 仍无虚拟屏
                    progressOverlay.updateProgress(stepCount, config.maxSteps, firstStatusText) // 仅更新当前进度
                } // 结束 hasShowerNow 判断
            } else { // 初始已在使用虚拟屏
                showerOverlay?.updateAutomationProgress(stepCount, config.maxSteps, firstStatusText) // 更新面板进度
            } // 结束 useShowerUi 逻辑

            onStep?.invoke(result) // 执行步进回调

            if (result.finished) { // 如果首步就结束了
                return result.message ?: "Task completed" // 直接返回结果
            } // 结束任务

            // Continue until finished or max steps reached
            while (_stepCount < config.maxSteps) { // 循环执行剩余步数
                awaitIfPaused() // 检查暂停
                result = _executeStep(null, isFirst = false) // 执行后续步骤（无原始 Prompt）
                val action = result.action // 获取当前动作
                val statusText = when { // 计算当前状态文案
                    result.finished -> result.message ?: "已完成" // 结束文案
                    action != null && action.metadata == "do" -> { // 执行中
                        val actionName = action.actionName ?: "" // 动作名
                        if (actionName.isNotEmpty()) "执行 ${actionName} 中..." else "执行操作中..." // 拼接
                    } // 结束分支
                    else -> "思考中..." // 思考文案
                } // 结束 when

                if (!useShowerUi) { // 循环内同样检查虚拟屏动态切换
                    val hasShowerNow = try { // 检测虚拟屏
                        ShowerController.getDisplayId(agentId) != null || ShowerController.getVideoSize(agentId) != null // 判断
                    } catch (e: Exception) { // 捕获
                        AppLogger.e("PhoneAgent", "[$agentId] Error re-checking Shower state in loop", e) // 日志
                        false // 错误
                    } // 结束 try-catch

                    if (hasShowerNow) { // 如果虚拟屏中途上线
                        useShowerUi = true // 切换
                        progressOverlay.hide() // 隐藏普通 UI
                        showerOverlay = VirtualDisplayOverlay.getInstance(context, agentId) // 获取新覆盖层
                        if (showerOverlay != null) { // 切换成功
                            useShowerIndicatorForAgent(context, agentId) // 指示器更新
                            showerOverlay?.showAutomationControls( // 显示面板
                                totalSteps = config.maxSteps, // 步数
                                initialStatus = statusText, // 状态
                                onTogglePauseResume = { isPaused -> pausedMutable?.value = isPaused }, // 回调
                                onExit = { // 退出回调
                                    PhoneAgentJobRegistry.cancelAgent(agentId, "User cancelled UI automation") // 取消注册
                                    job?.cancel(CancellationException("User cancelled UI automation")) // 取消任务
                                } // 结束定义
                            ) // 结束显示
                            showerOverlay?.updateAutomationProgress(stepCount, config.maxSteps, statusText) // 更新进度
                        } else { // 切换失败回滚
                            progressOverlay.show( // 恢复普通 UI
                                config.maxSteps, // 步数
                                "Thinking...", // 提示
                                onCancel = { // 取消回调
                                    PhoneAgentJobRegistry.cancelAgent(agentId, "User cancelled UI automation") // 取消注册
                                    job?.cancel(CancellationException("User cancelled UI automation")) // 取消任务
                                }, // 结束回调
                                onToggleTakeOver = { isPaused -> pausedMutable?.value = isPaused } // 暂停回调
                            ) // 结束显示
                            progressOverlay.updateProgress(stepCount, config.maxSteps, statusText) // 更新
                            useShowerUi = false // 重置标志
                        } // 结束 showerOverlay 检查
                    } else { // 持续使用普通屏
                        progressOverlay.updateProgress(stepCount, config.maxSteps, statusText) // 更新进度条
                    } // 结束 hasShowerNow 分支
                } else { // 持续使用虚拟屏
                    showerOverlay?.updateAutomationProgress(stepCount, config.maxSteps, statusText) // 更新面板
                } // 结束 useShowerUi 分支

                onStep?.invoke(result) // 执行外部步进回调

                if (result.finished) { // 任务判断：如果已结束
                    return result.message ?: "Task completed" // 返回结果消息
                } // 结束返回
            } // 结束 while 循环

            return "Max steps reached" // 步数耗尽，强制结束
        } finally { // 无论成功失败均执行清理
            AppLogger.d("PhoneAgent", "[$agentId] run: finishing, restoring UI") // 结束日志
            pauseFlow = null // 清除暂停引用
            floatingService?.setFloatingWindowVisible(true) // 恢复显示悬浮窗
            clearAgentIndicators(context, agentId) // 清理所有 UI 指示器
            if (useShowerUi) { // 隐藏对应 UI
                showerOverlay?.hideAutomationControls() // 隐藏虚拟屏面板
            } else { // 否则
                progressOverlay.hide() // 隐藏普通进度条
            } // 结束 UI 隐藏
            if (cleanupOnFinish) { // 如果配置了清理会话
                AppLogger.d("PhoneAgent", "[$agentId] run: cleaning up agent session") // 清理日志
                try { // 隐藏虚拟覆盖层
                    VirtualDisplayOverlay.hide(agentId) // 执行隐藏
                } catch (_: Exception) { // 忽略错误
                } // 结束 try
                try { // 关闭虚拟控制器
                    ShowerController.shutdown(agentId) // 执行关闭
                } catch (_: Exception) { // 忽略
                } // 结束 try
            } // 结束清理判断
        } // 结束 try-finally
    } // 结束 run 函数

    /** Reset the agent state for a new task. */ // 重置状态函数注释
    fun reset() { // 执行状态重置
        _contextHistory.clear() // 清空历史记录
        _stepCount = 0 // 重置计数器
    } // 结束 reset

    /** Execute a single step of the agent loop. */ // 执行单步逻辑注释
    private suspend fun _executeStep(userPrompt: String?, isFirst: Boolean): StepResult { // 单步核心逻辑
        _stepCount++ // 步数自增
        AppLogger.d("PhoneAgent", "[$agentId] _executeStep: begin, step=$_stepCount") // 单步启动日志

        val screenshotLink = actionHandler.captureScreenshotForAgent() // 捕获当前屏幕截图
        val screenInfo = buildString { // 构建传递给 AI 的屏幕描述
            if (screenshotLink != null) { // 如果有截图
                appendLine("[SCREENSHOT] Below is the latest screen image:") // 截图标识
                appendLine(screenshotLink) // 插入图片链接
            } else { // 如果截图失败
                appendLine("No screenshot available for this step.") // 提示缺失
            } // 结束判断
        }.trim() // 结束 String 构建

        val userMessage = if (isFirst) { // 构造用户消息内容
            "$userPrompt\n\n$screenInfo" // 首步：任务描述 + 截图
        } else { // 否则
            "** Screen Info **\n\n$screenInfo" // 续步：仅截图
        } // 结束消息构造

        _contextHistory.add("user" to userMessage) // 添加到上下文历史

        val responseStream = uiService.sendMessage( // 调用 AI 接口获取决策
            message = userMessage, // 当前消息
            chatHistory = _contextHistory.toList(), // 完整历史
            enableThinking = false, // 禁用独立思考节点
            stream = true, // 开启流式返回
            preserveThinkInHistory = true // 在历史中保留思考内容
        ) // 结束接口调用

        val contentBuilder = StringBuilder() // 拼接待处理的完整响应
        responseStream.collect { chunk -> contentBuilder.append(chunk) } // 收集流数据
        val fullResponse = contentBuilder.toString().trim() // 转换为字符串
        AppLogger.d("PhoneAgent", "[$agentId] _executeStep: AI response collected, length=${fullResponse.length}") // 响应日志

        val (thinking, answer) = parseThinkingAndAction(fullResponse) // 解析响应中的思考和指令部分
        val historyEntry = "<think>$thinking</think><answer>$answer</answer>" // 构造入库历史格式
        _contextHistory.add("assistant" to historyEntry) // 保存助手的回答

        val parsedAction = parseAgentAction(answer) // 解析具体的动作指令
        actionHandler.removeImagesFromLastUserMessage(_contextHistory) // 移除历史中过期的图片链接以减少 Token 消耗

        if (parsedAction.metadata == "finish") { // 如果 AI 决定结束
            val message = parsedAction.fields["message"] ?: "Task finished." // 获取结束消息
            return StepResult(success = true, finished = true, action = parsedAction, thinking = thinking, message = message) // 返回完成状态
        } // 结束 finish

        if (parsedAction.metadata == "do") { // 如果 AI 决定执行动作
            awaitIfPaused() // 检查暂停
            val execResult = actionHandler.executeAgentAction(parsedAction) // 执行动作并获取结果
            if (execResult.shouldFinish) { // 如果执行器提示任务应终止
                return StepResult(success = execResult.success, finished = true, action = parsedAction, thinking = thinking, message = execResult.message) // 返回结束
            } // 结束判断
            return StepResult(success = execResult.success, finished = false, action = parsedAction, thinking = thinking, message = execResult.message) // 返回继续
        } // 结束 do

        val errorMessage = "Unknown action format: ${parsedAction.metadata}" // 格式未知错误
        return StepResult(success = false, finished = true, action = parsedAction, thinking = thinking, message = errorMessage) // 返回异常终止
    } // 结束单步执行函数

    private fun extractTagContent(text: String, tag: String): String? { // 提取 XML 标签内容的助手函数
        val pattern = Regex("""<$tag>(.*?)</$tag>""", RegexOption.DOT_MATCHES_ALL) // 定义正则模式
        return pattern.find(text)?.groupValues?.getOrNull(1)?.trim() // 返回第一个捕获组内容
    } // 结束助手函数

    private fun parseThinkingAndAction(content: String): Pair<String?, String> { // 拆分思考过程与指令
        val full = content.trim() // 去除首尾空格
        val finishMarker = "finish(message=" // 结束标记关键字
        val finishIndex = full.indexOf(finishMarker) // 查找结束标记位置
        if (finishIndex >= 0) { // 如果包含结束标记
            val thinking = full.substring(0, finishIndex).trim().ifEmpty { null } // 标记前的内容视为思考
            val action = full.substring(finishIndex).trim() // 标记后的内容视为动作
            return thinking to action // 返回二元组
        } // 结束判断
        val doMarker = "do(action=" // 执行标记关键字
        val doIndex = full.indexOf(doMarker) // 查找位置
        if (doIndex >= 0) { // 如果包含执行标记
            val thinking = full.substring(0, doIndex).trim().ifEmpty { null } // 标记前视为思考
            val action = full.substring(doIndex).trim() // 标记后视为指令
            return thinking to action // 返回二元组
        } // 结束判断
        val thinkTag = extractTagContent(full, "think") // 尝试通过 <think> 标签解析
        val answerTag = extractTagContent(full, "answer") // 尝试通过 <answer> 标签解析
        if (thinkTag != null || answerTag != null) { // 如果存在标签格式
            return thinkTag to (answerTag ?: full) // 返回标签内容
        } // 结束判断
        return null to full // 否则认为全部是指令
    } // 结束拆分函数

    private fun parseAgentAction(raw: String): ParsedAgentAction { // 解析指令细节
        val original = raw.trim() // 去除多余空格
        val finishIndex = original.lastIndexOf("finish(") // 最后一次出现的 finish 指令
        val doIndex = original.lastIndexOf("do(") // 最后一次出现的 do 指令
        val startIndex = when { // 确定有效指令的起始位置
            finishIndex >= 0 && doIndex >= 0 -> maxOf(finishIndex, doIndex) // 取最后两者最大值
            finishIndex >= 0 -> finishIndex // 只有 finish
            doIndex >= 0 -> doIndex // 只有 do
            else -> -1 // 均不存在
        } // 结束位置判断

        val trimmed = if (startIndex >= 0) original.substring(startIndex).trim() else original // 截取核心指令字符串

        if (trimmed.startsWith("finish")) { // 处理结束动作
            val messageRegex = Regex("""finish\s*\(\s*message\s*=\s*\"(.*)\"\s*\)""", RegexOption.DOT_MATCHES_ALL) // 匹配 message 参数
            val message = messageRegex.find(trimmed)?.groupValues?.getOrNull(1) ?: "" // 提取文本
            return ParsedAgentAction(metadata = "finish", actionName = null, fields = mapOf("message" to message)) // 构造结果
        } // 结束 finish 解析

        if (!trimmed.startsWith("do")) { // 非 do 动作处理
            return ParsedAgentAction(metadata = "unknown", actionName = null, fields = emptyMap()) // 返回未知类型
        } // 结束

        val inner = trimmed.removePrefix("do").trim().removeSurrounding("(", ")") // 剥离 do() 外壳
        val fields = mutableMapOf<String, String>() // 准备参数容器
        val regex = Regex("""(\w+)\s*=\s*(?:\[(.*?)\]|\"(.*?)\"|'([^']*)'|([^,)]+))""") // 匹配 key=value 模式（支持多种括号引号）
        regex.findAll(inner).forEach { matchResult -> // 遍历所有参数对
            val key = matchResult.groupValues[1] // 参数名
            val value = matchResult.groupValues.drop(2).firstOrNull { it.isNotEmpty() } ?: "" // 参数值
            fields[key] = value // 存入容器
        } // 结束遍历

        return ParsedAgentAction(metadata = "do", actionName = fields["action"], fields = fields) // 返回完整解析对象
    } // 结束解析函数
} // 结束 PhoneAgent 类

private suspend fun useFullscreenStatusIndicatorForAgent() { // 启用全屏指示器助手函数
    val floatingService = FloatingChatService.getInstance() // 获取服务
    floatingService?.setStatusIndicatorVisible(true) // 设置指示器可见
} // 结束

private suspend fun useShowerIndicatorForAgent(context: Context, agentId: String) { // 启用虚拟屏指示器助手函数
    try { // 异常保护
        val overlay = VirtualDisplayOverlay.getInstance(context, agentId) // 获取覆盖层
        overlay.setShowerBorderVisible(true) // 开启高亮边框
    } catch (e: Exception) { // 捕获
        AppLogger.e("PhoneAgent", "[$agentId] Error enabling Shower border indicator", e) // 记录错误
    } // 结束 try-catch
    val floatingService = FloatingChatService.getInstance() // 获取悬浮服务
    floatingService?.setStatusIndicatorVisible(false) // 隐藏系统全屏指示器（避免冲突）
} // 结束

private suspend fun clearAgentIndicators(context: Context, agentId: String) { // 清理所有指示器助手函数
    try { // 异常保护
        val overlay = VirtualDisplayOverlay.getInstance(context, agentId) // 获取覆盖层
        overlay.setShowerBorderVisible(false) // 关闭边框
    } catch (e: Exception) { // 捕获
        AppLogger.e("PhoneAgent", "[$agentId] Error disabling Shower border indicator", e) // 记录错误
    } // 结束 try
    val floatingService = FloatingChatService.getInstance() // 服务实例
    floatingService?.setStatusIndicatorVisible(false) // 关闭状态条
} // 结束

/** Handles the execution of parsed actions. */ // 动作处理器类注释
class ActionHandler( // 具体执行设备操作的类
    private val context: Context, // 上下文环境
    private var screenWidth: Int, // 屏幕宽度缓存
    private var screenHeight: Int, // 屏幕高度缓存
    private val toolImplementations: ToolImplementations // 工具的具体实现（点击、滑动等）
) { // 类体开始
    private var agentId: String = "default" // 默认代理 ID

    fun setAgentId(id: String) { // ID 设置函数
        agentId = id // 更新实例的 ID
    } // 结束

    data class ActionExecResult( // 内部结果数据类
        val success: Boolean, // 执行是否成功
        val shouldFinish: Boolean, // 指示任务是否应立即结束
        val message: String? // 补充说明
    ) // 结束定义

    companion object { // 静态常量
        private const val POST_LAUNCH_DELAY_MS = 1000L // 启动应用后的等待时长
        private const val POST_NON_WAIT_ACTION_DELAY_MS = 500L // 非启动动作后的缓冲时间
    } // 结束

    private data class ShowerUsageContext( // 虚拟屏运行环境描述类
        val isAdbOrHigher: Boolean, // 权限标记
        val showerDisplayId: Int? // 虚拟屏幕 ID
    ) { // 类体
        val hasShowerDisplay: Boolean get() = showerDisplayId != null // 是否已显示
        val canUseShowerForInput: Boolean get() = isAdbOrHigher && showerDisplayId != null // 是否满足输入控制条件
    } // 结束

    private fun resolveShowerUsageContext(): ShowerUsageContext { // 解析当前环境
        val level = androidPermissionPreferences.getPreferredPermissionLevel() ?: AndroidPermissionLevel.STANDARD // 获取权限等级
        var isAdbOrHigher = when (level) { // 映射权限状态
            AndroidPermissionLevel.DEBUGGER, // 调试
            AndroidPermissionLevel.ADMIN, // 管理
            AndroidPermissionLevel.ROOT -> true // 满足
            else -> false // 不满足
        } // 结束 when

        if (isAdbOrHigher) { // 权限满足时检查开关
            val experimentalEnabled = try { // 尝试读取
                DisplayPreferencesManager.getInstance(context).isExperimentalVirtualDisplayEnabled() // 实验性开关
            } catch (e: Exception) { // 捕获
                AppLogger.e("ActionHandler", "[$agentId] Error reading experimental virtual display flag", e) // 日志
                true // 默认开启
            } // 结束 try
            if (!experimentalEnabled) { // 如果开关关闭
                isAdbOrHigher = false // 禁用高级权限流程
            } // 结束判断
        } // 结束逻辑
        val showerId = try { // 获取屏幕 ID
            ShowerController.getDisplayId(agentId) // 查询 ID
        } catch (e: Exception) { // 捕获
            AppLogger.e("ActionHandler", "[$agentId] Error getting Shower display id", e) // 日志
            null // 失败
        } // 结束 try
        return ShowerUsageContext(isAdbOrHigher = isAdbOrHigher, showerDisplayId = showerId) // 返回环境包
    } // 结束函数

    suspend fun captureScreenshotForAgent(): String? { // 截图核心函数
        val showerCtx = resolveShowerUsageContext() // 获取环境
        val floatingService = FloatingChatService.getInstance() // 悬浮窗服务
        val progressOverlay = UIAutomationProgressOverlay.getInstance(context) // 进度图层

        var screenshotLink: String? = null // 链接占位
        var dimensions: Pair<Int, Int>? = null // 尺寸占位

        if (showerCtx.canUseShowerForInput) { // 如果支持虚拟屏截图
            val (link, dims) = captureScreenshotViaShower() // 从虚拟屏获取
            screenshotLink = link // 赋值
            dimensions = dims // 赋值
        } // 结束虚拟屏判断

        if (screenshotLink == null) { // 如果虚拟屏截图失败或不支持
            try { // 切换到主屏幕截图流程
                floatingService?.setStatusIndicatorVisible(false) // 截图前隐藏 UI 指示器
                progressOverlay.setOverlayVisible(false) // 隐藏进度 UI 以防入镜
                delay(200) // 等待 UI 消失彻底

                val screenshotTool = buildScreenshotTool() // 构造截图工具对象
                val (filePath, fallbackDims) = toolImplementations.captureScreenshot(screenshotTool) // 调用实现层截图

                if (filePath != null) { // 截图文件已生成
                    val bitmap = BitmapFactory.decodeFile(filePath) // 解码为位图
                    if (bitmap != null) { // 解码成功
                        val (compressedLink, _) = saveCompressedScreenshotFromBitmap(bitmap) // 压缩并转为 ID 链接
                        screenshotLink = compressedLink // 保存链接
                        dimensions = fallbackDims // 记录尺寸
                        bitmap.recycle() // 回收内存
                    } // 结束位图处理
                } // 结束文件处理
            } finally { // 恢复 UI 状态
                val hasShowerDisplayNow = try { // 再次校验虚拟屏状态
                    ShowerController.getDisplayId(agentId) != null // 判断是否存活
                } catch (e: Exception) { // 异常
                    AppLogger.e("ActionHandler", "[$agentId] Error checking Shower display state in finally", e) // 日志
                    false // 默认无
                } // 结束校验
                if (!hasShowerDisplayNow) { // 仅在非虚拟屏模式下恢复全屏指示器
                    floatingService?.setStatusIndicatorVisible(true) // 恢复显示
                } // 结束指示器恢复
                progressOverlay.setOverlayVisible(true) // 恢复进度 UI
            } // 结束 finally
        } // 结束降级流程

        if (dimensions != null) { // 如果获取到了屏幕尺寸
            screenWidth = dimensions.first // 更新宽
            screenHeight = dimensions.second // 更新高
        } // 结束
        return screenshotLink // 返回图片链接
    } // 结束截图函数

    private fun buildScreenshotTool(): AITool { // 构造 AITool 对象的辅助函数
        return AITool( // 返回新对象
            name = "capture_screenshot", // 工具名
            parameters = emptyList() // 参数为空
        ) // 结束构造
    } // 结束

    private suspend fun captureScreenshotViaShower(): Pair<String?, Pair<Int, Int>?> { // 从虚拟屏幕渲染帧中截图
        return try { // 异常保护
            val pngBytes = VirtualDisplayOverlay.getInstance(context, agentId).captureCurrentFramePng() // 直接抓取渲染字节
            if (pngBytes == null || pngBytes.isEmpty()) { // 无数据
                AppLogger.w("ActionHandler", "[$agentId] Shower WS screenshot returned no data") // 警告
                Pair(null, null) // 返回空
            } else { // 有数据
                val bitmap = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size) // 字节转位图
                if (bitmap == null) { // 解码失败
                    AppLogger.e("ActionHandler", "[$agentId] Shower screenshot: failed to decode bytes") // 日志
                    Pair(null, null) // 返回空
                } else { // 成功
                    val result = saveCompressedScreenshotFromBitmap(bitmap) // 执行保存与压缩
                    bitmap.recycle() // 及时清理
                    result // 返回结果
                } // 结束位图校验
            } // 结束字节校验
        } catch (e: Exception) { // 捕获
            if (e is CancellationException) throw e // 处理取消
            AppLogger.e("ActionHandler", "[$agentId] Shower screenshot failed", e) // 日志
            Pair(null, null) // 失败
        } // 结束 try-catch
    } // 结束函数

    private fun saveCompressedScreenshotFromBitmap(bitmap: Bitmap): Pair<String?, Pair<Int, Int>?> { // 压缩位图并保存到文件
        return try { // 开启流程
            val originalWidth = bitmap.width // 记录原宽
            val originalHeight = bitmap.height // 记录原高

            val prefs = DisplayPreferencesManager.getInstance(context) // 获取配置
            val format = prefs.getScreenshotFormat().uppercase(Locale.getDefault()) // 目标格式
            val quality = prefs.getScreenshotQuality().coerceIn(50, 100) // 压缩质量
            val scalePercent = prefs.getScreenshotScalePercent().coerceIn(50, 100) // 缩放比例

            val screenshotDir = File("/sdcard/Download/Operit/cleanOnExit") // 临时目录
            if (!screenshotDir.exists()) screenshotDir.mkdirs() // 确保目录存在

            val shortName = System.currentTimeMillis().toString().takeLast(4) // 随机短名
            val (compressFormat, fileExt, effectiveQuality) = when (format) { // 选择压缩策略
                "JPG", "JPEG" -> Triple(Bitmap.CompressFormat.JPEG, "jpg", quality) // JPEG
                else -> Triple(Bitmap.CompressFormat.PNG, "png", 100) // PNG(无损质量忽略设置)
            } // 结束 when

            val scaleFactor = scalePercent / 100.0 // 计算浮点缩放比
            val bitmapForSave = if (scaleFactor in 0.0..0.999) { // 需要缩放
                val newWidth = (originalWidth * scaleFactor).toInt().coerceAtLeast(1) // 新宽
                val newHeight = (originalHeight * scaleFactor).toInt().coerceAtLeast(1) // 新高
                Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true) // 执行缩放
            } else { // 无需缩放
                bitmap // 使用原图
            } // 结束缩放逻辑

            val file = File(screenshotDir, "$shortName.$fileExt") // 最终文件对象
            try { // 执行写操作
                FileOutputStream(file).use { outputStream -> // 自动关闭流
                    bitmapForSave.compress(compressFormat, effectiveQuality, outputStream) // 执行压缩写入
                } // 结束写入
            } finally { // 清理临时位图
                if (bitmapForSave !== bitmap) bitmapForSave.recycle() // 如果生成了新位图则回收
            } // 结束 try-finally

            val imageId = ImagePoolManager.addImage(file.absolutePath) // 将文件存入图片池并换取 ID
            if (imageId == "error") { // 图片池存入失败
                Pair(null, null) // 返回空
            } else { // 成功
                Pair("<link type=\"image\" id=\"$imageId\"></link>", Pair(originalWidth, originalHeight)) // 返回 XML 链接及原尺寸
            } // 结束结果返回
        } catch (e: Exception) { // 捕获过程异常
            AppLogger.e("ActionHandler", "[$agentId] Error saving compressed screenshot", e) // 日志
            Pair(null, null) // 返回空
        } // 结束整体 try
    } // 结束压缩保存函数

    fun removeImagesFromLastUserMessage(history: MutableList<Pair<String, String>>) { // 清理历史消息中多余的图片链接
        val lastUserMessageIndex = history.indexOfLast { it.first == "user" } // 找到最后一条用户消息
        if (lastUserMessageIndex != -1) { // 消息存在
            val (role, content) = history[lastUserMessageIndex] // 获取角色和内容
            if (content.contains("<link type=\"image\"")) { // 确实含有图片链接
                val stripped = content.replace(Regex("""<link type=\"image\".*?</link>"""), "").trim() // 正则剔除
                history[lastUserMessageIndex] = role to stripped // 更新历史项
            } // 结束内容校验
        } // 结束索引校验
    } // 结束清理函数

    suspend fun executeAgentAction(parsed: ParsedAgentAction): ActionExecResult { // 分发并执行具体的 AI 动作
        val actionName = parsed.actionName ?: return fail(message = "Missing action name") // 校验动作名
        val fields = parsed.fields // 获取动作参数

        val showerCtx = resolveShowerUsageContext() // 获取当前环境描述
        return when (actionName) { // 根据指令分发逻辑
            "Launch" -> { // 处理启动应用指令
                val app = fields["app"]?.takeIf { it.isNotBlank() } ?: return fail(message = "No app name specified for Launch") // 获取包名/应用名
                val packageName = resolveAppPackageName(app) // 解析真实包名
                try { // 异常保护
                    val preferredLevel = androidPermissionPreferences.getPreferredPermissionLevel() // 权限检查
                        ?: AndroidPermissionLevel.STANDARD // 默认
                    val experimentalEnabled = try { // 实验性检查
                        DisplayPreferencesManager.getInstance(context).isExperimentalVirtualDisplayEnabled() // 开关
                    } catch (e: Exception) { true } // 失败默认开启

                    if (preferredLevel == AndroidPermissionLevel.DEBUGGER && experimentalEnabled) { // 调试模式下前置校验
                        val isShizukuRunning = ShizukuAuthorizer.isShizukuServiceRunning() // Shizuku
                        val hasShizukuPermission = if (isShizukuRunning) ShizukuAuthorizer.hasShizukuPermission() else false // 权限
                        if (!isShizukuRunning || !hasShizukuPermission) { // 不符合
                            return fail(shouldFinish = true, message = "Shizuku 不可用，无法启动虚拟屏幕。") // 报错并终止
                        } // 结束
                    } // 结束前置校验

                    if (showerCtx.isAdbOrHigher) { // 走高级启动流程
                        val pm = context.packageManager // 包管理器
                        val hasLaunchableTarget = pm.getLaunchIntentForPackage(packageName) != null // 检查是否有启动入口
                        ensureVirtualDisplayIfAdbOrHigher() // 确保虚拟屏已就绪

                        val metrics = context.resources.displayMetrics // 显示规格
                        val width = metrics.widthPixels // 宽
                        val height = metrics.heightPixels // 高
                        val dpi = metrics.densityDpi // 密度
                        val bitrateKbps = try { // 比特率
                            DisplayPreferencesManager.getInstance(context).getVirtualDisplayBitrateKbps() // 读取
                        } catch (e: Exception) { 3000 } // 默认 3Mbps

                        val created = ShowerController.ensureDisplay(agentId, context, width, height, dpi, bitrateKbps = bitrateKbps) // 启动虚拟显卡
                        val launched = if (created && hasLaunchableTarget) ShowerController.launchApp(agentId, packageName) else false // 在虚拟屏上启动应用

                        if (created && launched) { // 启动成功
                            try { // 通知覆盖层更新包名
                                VirtualDisplayOverlay.getInstance(context, agentId).updateCurrentAppPackageName(packageName) // 更新
                            } catch (_: Exception) {} // 忽略
                            useShowerIndicatorForAgent(context, agentId) // 指示器反馈
                            delay(POST_LAUNCH_DELAY_MS) // 启动缓冲
                            ok() // 成功
                        } else { // 启动异常
                            val desktopPackage = "com.ai.assistance.operit.desktop" // 备用桌面
                            val desktopLaunched = ShowerController.launchApp(agentId, desktopPackage) // 尝试启动桌面
                            if (desktopLaunched) { // 桌面启动成功
                                try { // 更新包名
                                    VirtualDisplayOverlay.getInstance(context, agentId).updateCurrentAppPackageName(desktopPackage) // 更新
                                } catch (_: Exception) {} // 忽略
                                useShowerIndicatorForAgent(context, agentId) // 反馈
                                delay(POST_LAUNCH_DELAY_MS) // 缓冲
                                ok() // 视作成功（至少环境对了）
                            } else { // 最终失败
                                fail(message = "Failed to launch on Shower virtual display") // 返回错误
                            } // 结束备用逻辑
                        } // 结束启动结果判断
                    } else { // 普通权限流程
                        val systemTools = ToolGetter.getSystemOperationTools(context) // 获取系统工具
                        val result = systemTools.startApp(AITool("start_app", listOf(ToolParameter("package_name", packageName)))) // 真实屏幕启动
                        if (result.success) { // 成功
                            delay(POST_LAUNCH_DELAY_MS) // 等待
                            ok() // 结果
                        } else { // 失败
                            fail(message = result.error ?: "Failed to launch app: $packageName") // 报错
                        } // 结束分支
                    } // 结束权限分流
                } catch (e: Exception) { // 捕获启动总异常
                    if (e is CancellationException) throw e // 处理协程取消
                    fail(message = "Exception while launching app: ${e.message}") // 详细报错
                } // 结束 launch try
            } // 结束 Launch 分支
            "Tap" -> { // 处理点击操作
                val element = fields["element"] ?: return fail(message = "No element for Tap") // 获取点击位置
                val (x, y) = parseRelativePoint(element) ?: return fail(message = "Invalid coordinates for Tap: $element") // 坐标解析
                val exec = withAgentUiHiddenForAction(showerCtx) { // 确保 UI 不遮挡的情况下执行
                    if (showerCtx.canUseShowerForInput) { // 虚拟屏输入
                        val okTap = ShowerController.tap(agentId, x, y) // 向虚拟屏发点击
                        if (okTap) ok() else fail(message = "Shower TAP failed at ($x,$y)") // 结果
                    } else { // 真实屏幕输入
                        val params = withDisplayParam(listOf(ToolParameter("x", x.toString()), ToolParameter("y", y.toString()))) // 构造参数
                        val result = toolImplementations.tap(AITool("tap", params)) // 执行实现类点击
                        if (result.success) ok() else fail(message = result.error ?: "Tap failed") // 结果
                    } // 结束分支
                } // 结束执行块
                if (exec.success && !exec.shouldFinish) delay(POST_NON_WAIT_ACTION_DELAY_MS) // 点击后延迟缓冲
                exec // 返回结果
            } // 结束 Tap 分支
            "Type" -> { // 处理输入文本指令
                val text = fields["text"] ?: "" // 获取输入内容
                val exec = withAgentUiHiddenForAction(showerCtx) { // 隐藏干扰 UI
                    if (showerCtx.canUseShowerForInput) { // 虚拟屏流程
                        try { // 特殊的清空并输入策略
                            var cleared = false // 清空标记
                            val selectedAll = ShowerController.keyWithMeta(agentId, KeyEvent.KEYCODE_A, KeyEvent.META_CTRL_ON) // 执行 Ctrl+A
                            if (selectedAll) { // 全选成功
                                delay(80) // 等待响应
                                cleared = ShowerController.key(agentId, KeyEvent.KEYCODE_DEL) // 执行删除键
                            } // 结束
                            if (!cleared) { // 如果全选删除失败，尝试 KEYCODE_CLEAR
                                cleared = ShowerController.key(agentId, KeyEvent.KEYCODE_CLEAR) // 执行清空
                            } // 结束
                            if (!cleared) { // 终极清空：光标移至末尾后循环退格
                                ShowerController.key(agentId, KeyEvent.KEYCODE_MOVE_END) // 移到最后
                                repeat(200) { // 假设最大字符数
                                    ShowerController.key(agentId, KeyEvent.KEYCODE_DEL) // 连续按退格
                                } // 结束
                            } // 结束备选清空逻辑
                            delay(300) // 等待编辑框清空生效
                            if (text.isEmpty()) return@withAgentUiHiddenForAction ok() // 如果只是想清空，到此结束
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager // 剪贴板获取
                                ?: return@withAgentUiHiddenForAction fail(message = "Clipboard unavailable") // 失败
                            clipboard.setPrimaryClip(ClipData.newPlainText("operit_input", text)) // 将目标文本存入系统剪贴板
                            delay(100) // 剪贴板广播同步
                            val pasted = ShowerController.key(agentId, KeyEvent.KEYCODE_PASTE) // 发送粘贴指令
                            if (pasted) ok() else fail(message = "Shower PASTE failed") // 返回粘贴状态
                        } catch (e: Exception) { // 捕获
                            if (e is CancellationException) throw e // 处理取消
                            fail(message = "Error typing via Shower: ${e.message}") // 详细报错
                        } // 结束 try
                    } else { // 真实屏幕传统输入流程
                        val params = withDisplayParam(listOf(ToolParameter("text", text))) // 参数
                        val result = toolImplementations.setInputText(AITool("set_input_text", params)) // 执行输入实现
                        if (result.success) ok() else fail(message = result.error ?: "Type failed") // 返回
                    } // 结束分支
                } // 结束执行块
                if (exec.success && !exec.shouldFinish) delay(POST_NON_WAIT_ACTION_DELAY_MS) // 延迟缓冲
                exec // 返回
            } // 结束 Type 分支
            "Swipe" -> { // 处理滑动指令
                val start = fields["start"] ?: return fail(message = "Missing swipe start") // 滑动起点
                val end = fields["end"] ?: return fail(message = "Missing swipe end") // 滑动终点
                val (sx, sy) = parseRelativePoint(start) ?: return fail(message = "Invalid swipe start") // 解析起点坐标
                val (ex, ey) = parseRelativePoint(end) ?: return fail(message = "Invalid swipe end") // 解析终点坐标
                val exec = withAgentUiHiddenForAction(showerCtx) { // 隐藏 UI
                    if (showerCtx.canUseShowerForInput) { // 虚拟屏滑动
                        val okSwipe = ShowerController.swipe(agentId, sx, sy, ex, ey) // 发送滑动指令
                        if (okSwipe) ok() else fail(message = "Shower SWIPE failed") // 结果
                    } else { // 真实屏滑动
                        val params = withDisplayParam(listOf( // 构造参数列表
                            ToolParameter("start_x", sx.toString()), ToolParameter("start_y", sy.toString()), // 起点
                            ToolParameter("end_x", ex.toString()), ToolParameter("end_y", ey.toString()) // 终点
                        )) // 结束参数
                        val result = toolImplementations.swipe(AITool("swipe", params)) // 调用滑动实现
                        if (result.success) ok() else fail(message = result.error ?: "Swipe failed") // 结果
                    } // 结束环境分支
                } // 结束逻辑块
                if (exec.success && !exec.shouldFinish) delay(POST_NON_WAIT_ACTION_DELAY_MS) // 缓冲
                exec // 返回结果
            } // 结束 Swipe 分支
            "Back" -> { // 处理返回键
                val exec = withAgentUiHiddenForAction(showerCtx) { // 隐藏 UI
                    if (showerCtx.canUseShowerForInput) { // 虚拟键位模拟
                        val okKey = ShowerController.key(agentId, KeyEvent.KEYCODE_BACK) // 发送返回码
                        if (okKey) ok() else fail(message = "Shower BACK failed") // 结果
                    } else { // 无障碍或 ADB 返回
                        val params = withDisplayParam(listOf(ToolParameter("key_code", "KEYCODE_BACK"))) // 参数
                        val result = toolImplementations.pressKey(AITool("press_key", params)) // 调用按键实现
                        if (result.success) ok() else fail(message = result.error ?: "Back failed") // 返回
                    } // 结束判断
                } // 结束逻辑
                if (exec.success && !exec.shouldFinish) delay(POST_NON_WAIT_ACTION_DELAY_MS) // 缓冲
                exec // 结果
            } // 结束 Back
            "Home" -> { // 处理桌面键
                val exec = withAgentUiHiddenForAction(showerCtx) { // 隐藏 UI
                    if (showerCtx.canUseShowerForInput) { // 虚拟屏 Home
                        val okKey = ShowerController.key(agentId, KeyEvent.KEYCODE_HOME) // 发送主屏键码
                        if (okKey) ok() else fail(message = "Shower HOME failed") // 结果
                    } else { // 传统路径
                        val params = withDisplayParam(listOf(ToolParameter("key_code", "KEYCODE_HOME"))) // 参数
                        val result = toolImplementations.pressKey(AITool("press_key", params)) // 执行
                        if (result.success) ok() else fail(message = result.error ?: "Home failed") // 结果
                    } // 结束
                } // 结束逻辑
                if (exec.success && !exec.shouldFinish) delay(POST_NON_WAIT_ACTION_DELAY_MS) // 缓冲
                exec // 结果
            } // 结束 Home
            "Wait" -> { // 处理显式等待指令
                val seconds = fields["duration"]?.replace("seconds", "")?.trim()?.toDoubleOrNull() ?: 1.0 // 解析等待时长（秒）
                delay((seconds * 1000).toLong().coerceAtLeast(0L)) // 执行协程挂起
                ok() // 结束等待并成功
            } // 结束 Wait
            "Take_over" -> ok(shouldFinish = true, message = fields["message"] ?: "User takeover required") // 人工接管逻辑：直接终止并提示原因
            else -> fail(message = "Unknown action: $actionName") // 其他未知指令返回错误
        } // 结束全局动作分发
    } // 结束指令执行函数

    private suspend fun withAgentUiHiddenForAction( // 在真实屏幕执行关键动作前临时隐藏 UI 的封装函数
        showerCtx: ShowerUsageContext, // 当前环境上下文
        block: suspend () -> ActionExecResult // 要执行的动作代码块
    ): ActionExecResult { // 返回动作执行结果
        if (showerCtx.canUseShowerForInput) return block() // 虚拟屏环境无需隐藏真实 UI，直接执行
        val progressOverlay = UIAutomationProgressOverlay.getInstance(context) // 获取进度层实例
        try { // 隐藏指示器
            progressOverlay.setOverlayVisible(false) // 关闭可见性
            delay(200) // 等待平滑动画消失
            return block() // 执行核心逻辑
        } finally { // 恢复指示器
            progressOverlay.setOverlayVisible(true) // 重新显示
        } // 结束 try-finally
    } // 结束封装函数

    private suspend fun ensureVirtualDisplayIfAdbOrHigher() { // 确保虚拟显示服务器已启动的辅助函数
        try { // 异常保护
            val level = androidPermissionPreferences.getPreferredPermissionLevel() ?: AndroidPermissionLevel.STANDARD // 检查权限
            val isAdbOrHigher = when (level) { // 校验状态
                AndroidPermissionLevel.DEBUGGER, AndroidPermissionLevel.ADMIN, AndroidPermissionLevel.ROOT -> true // 满足
                else -> false // 不满足
            } // 结束 when
            if (!isAdbOrHigher) return // 普通权限直接跳过

            val ok = ShowerServerManager.ensureServerStarted(context) // 尝试拉起 Shower 服务端进程
            if (ok) { // 服务就绪
                try { // 展示覆盖层
                    VirtualDisplayOverlay.getInstance(context, agentId).show(0) // 开启虚拟展示图层
                } catch (e: Exception) { // 捕获
                    AppLogger.e("ActionHandler", "[$agentId] Error showing Shower overlay", e) // 记录
                } // 结束内部 try
            } // 结束判断
        } catch (e: Exception) { // 捕获整体异常
            if (e is CancellationException) throw e // 保持取消状态
            AppLogger.e("ActionHandler", "[$agentId] Error ensuring Shower", e) // 日志记录
        } // 结束整体 try
    } // 结束函数

    private fun withDisplayParam(params: List<ToolParameter>): List<ToolParameter> { // 向工具参数中动态注入 DisplayId 的辅助函数
        return try { // 尝试注入
            val showerId = ShowerController.getDisplayId(agentId) // 获取当前虚拟屏 ID
            if (showerId != null) { // 虚拟屏存在
                params + ToolParameter("display", showerId.toString()) // 注入 ID 到参数列表
            } else { // 虚拟屏不存在
                val id = VirtualDisplayManager.getInstance(context).getDisplayId() // 尝试从管理器获取默认 ID
                if (id != null) params + ToolParameter("display", id.toString()) else params // 注入或保持原样
            } // 结束 ID 判断
        } catch (e: Exception) { // 捕获
            params // 发生异常时返回原参数，不做注入
        } // 结束整体流程
    } // 结束注入函数

    private fun ok(shouldFinish: Boolean = false, message: String? = null) = ActionExecResult(true, shouldFinish, message) // 生成成功的执行结果
    private fun fail(shouldFinish: Boolean = false, message: String) = ActionExecResult(false, shouldFinish, message) // 生成失败的执行结果

    private fun parseRelativePoint(value: String): Pair<Int, Int>? { // 将 AI 生成的 [0-1000] 相对坐标映射到实际像素坐标
        val parts = value.trim().removeSurrounding("[", "]").split(",").map { it.trim() } // 解析格式化字符串
        if (parts.size < 2) return null // 坐标轴不足则返回空
        val relX = parts[0].toIntOrNull() ?: return null // 解析 X（千分比值）
        val relY = parts[1].toIntOrNull() ?: return null // 解析 Y（千分比值）
        return (relX / 1000.0 * screenWidth).toInt() to (relY / 1000.0 * screenHeight).toInt() // 计算对应分辨率下的像素位置
    } // 结束解析函数

    private suspend fun resolveAppPackageName(app: String): String { // 将 AI 提到的应用名转换为 Android 系统包名
        val trimmed = app.trim() // 去除多余空格
        val lowered = trimmed.lowercase(Locale.getDefault()) // 转小写
        fun lookup(): String? = StandardUITools.APP_PACKAGES[app] ?: StandardUITools.APP_PACKAGES[trimmed] ?: StandardUITools.APP_PACKAGES[lowered] // 定义字典查找
        val directHit = lookup() // 首次查找
        if (directHit != null) return directHit // 命中直接返回
        withContext(Dispatchers.IO) { StandardUITools.scanAndAddInstalledApps(context) } // 未命中则触发全盘扫描并更新字典
        return lookup() ?: trimmed // 再次尝试查找，若依然无果则原样返回
    } // 结束解析函数
} // 结束 ActionHandler 类

/** Interface for providing tool implementations to the ActionHandler. */ // 动作实现抽象接口注释
interface ToolImplementations { // 定义需要外部实现的工具接口
    suspend fun tap(tool: AITool): ToolResult // 点击实现接口
    suspend fun longPress(tool: AITool): ToolResult // 长按实现接口
    suspend fun setInputText(tool: AITool): ToolResult // 文本输入实现接口
    suspend fun swipe(tool: AITool): ToolResult // 滑动实现接口
    suspend fun pressKey(tool: AITool): ToolResult // 按键实现接口
    suspend fun captureScreenshot(tool: AITool): Pair<String?, Pair<Int, Int>?> // 截图实现接口
} // 结束接口定义

// 本行代码注释：全文件逐行代码功能说明添加完毕