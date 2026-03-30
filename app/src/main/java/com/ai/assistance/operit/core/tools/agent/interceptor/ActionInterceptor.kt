// --- START OF FILE ActionInterceptor.kt ---

package com.ai.assistance.operit.core.tools.agent.interceptor

import com.ai.assistance.operit.core.tools.agent.ActionHandler
import com.ai.assistance.operit.core.tools.agent.ModelResponse
import com.ai.assistance.operit.core.tools.agent.ParsedAgentAction

/**
 * 拦截器结果包装类，替代 Python 的 Tuple4 返回值
 */
data class InterceptorResult(
    val passed: Boolean,
    val modifiedActions: List<ParsedAgentAction>? = null,
    val message: String? = null,
    val actionResult: ActionHandler.ActionExecResult? = null
)

/**
 * 动作拦截器抽象基类
 */
abstract class ActionInterceptor {

    /**
     * 处理拦截逻辑
     *
     * @param action 当前准备执行的动作
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @param stepCount 当前执行的步数
     * @param response 模型响应对象
     * @return InterceptorResult 拦截处理结果
     */
    abstract fun beforeExecute(
        action: ParsedAgentAction,
        screenWidth: Int,
        screenHeight: Int,
        stepCount: Int,
        response: ModelResponse
    ): InterceptorResult

    /**
     * 辅助方法：从字符串 "[499, 524]" 解析出坐标对
     */
    protected fun parseElement(elementStr: String?): Pair<Int, Int>? {
        if (elementStr.isNullOrBlank()) return null
        val parts = elementStr.trim()
            .removeSurrounding("[", "]")
            .split(",")
            .map { it.trim() }

        if (parts.size < 2) return null
        val absX = parts[0].toIntOrNull() ?: return null
        val absY = parts[1].toIntOrNull() ?: return null
        return absX to absY
    }
}