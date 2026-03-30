package com.ai.assistance.operit.ui.features.toolbox.screens.autoglmride.action_intercepter

// --- START OF FILE RideHxzInterceptor.kt ---


import com.ai.assistance.operit.core.tools.agent.ModelResponse
import com.ai.assistance.operit.core.tools.agent.ParsedAgentAction
import com.ai.assistance.operit.core.tools.agent.interceptor.ActionInterceptor
import com.ai.assistance.operit.core.tools.agent.interceptor.InterceptorResult
import com.ai.assistance.operit.util.AppLogger

class RideHxzInterceptor(
    private val start: String = "",
    private val destination: String = ""
) : ActionInterceptor() {

    override fun beforeExecute(
        action: ParsedAgentAction,
        screenWidth: Int,
        screenHeight: Int,
        stepCount: Int,
        response: ModelResponse
    ): InterceptorResult {
        val TAG = "花小猪拦截器"
        val thinkingText = response.thinking ?: ""
        AppLogger.d(TAG,"$TAG 进入,stepCount:$stepCount, thinking_text:'$thinkingText',\n action:$action")

        if (action.actionName == "Tap") {
            val elementStr = action.fields["element"]
            val coords = parseElement(elementStr)

            if (coords != null) {
                val (absX, absY) = coords

                // 1. 点击终点坐标修改, 终点拦截器
                val destNewX = 272
                val destNewY = 624
                val yRange = 25

                if (absX in 201..549 && absY in (destNewY - yRange)..(destNewY + yRange) &&
                    (thinkingText.contains("搜索框") || thinkingText.contains("输入目的地") || thinkingText.contains("你要去哪儿") || thinkingText.contains("输入框")) &&
                    (start.isEmpty() || stepCount > 2)
                ) {
                    val modifiedFields = action.fields.toMutableMap()
                    modifiedFields["element"] = "[$destNewX, $destNewY]"
                    val modifiedAction = action.copy(fields = modifiedFields)

                    if (destNewX != absX || destNewY != absY) {
                        AppLogger.d(TAG,"$TAG 点击终点坐标修改: ($absX,$absY) -> ($destNewX,$destNewY)")
                    }

                    val typeAction = ParsedAgentAction(
                        metadata = "do",
                        actionName = "Type",
                        fields = mapOf("action" to "Type", "text" to destination)
                    )

                    AppLogger.d(TAG,"$TAG  终点拦截器生效, ⚡触发宏指令: [点击终点] +[输入 $destination]")
                    val msg = """
                        让我先点击搜索框输入地址。
                        do(action="Type", text="$destination").
                        好的，我已经输入了"$destination"
                    """.trimIndent()

                    return InterceptorResult(false, listOf(modifiedAction, typeAction), msg, null)
                }

                // 2. 点击起点坐标修改, 起点拦截器
                val startNewX = 499
                val startNewY = 544

                if (start.isNotEmpty() && (
                            (absX in 201..(startNewX + 49) && absY in 401..(startNewY + yRange - 1) && (thinkingText.contains("起点") || thinkingText.contains("上车"))) ||
                                    (absX in 201..(startNewX + 49) && absY in 401..(startNewY + yRange - 1) && (thinkingText.contains("修改起点地址") || thinkingText.contains("正在获取上车地点") || thinkingText.contains("正在定位"))) ||
                                    (absX in 201..(startNewX + 49) && absY < startNewY + yRange && thinkingText.contains("起点") && stepCount == 1)
                            )
                ) {
                    val modifiedFields = action.fields.toMutableMap()
                    modifiedFields["element"] = "[$startNewX, $startNewY]"
                    val modifiedAction = action.copy(fields = modifiedFields)

                    if (startNewX != absX || startNewY != absY) {
                        AppLogger.d(TAG,"$TAG 点击起点坐标修改: ($absX,$absY) -> ($startNewX,$startNewY)")
                    }

                    val typeAction = ParsedAgentAction(
                        metadata = "do",
                        actionName = "Type",
                        fields = mapOf("action" to "Type", "text" to start)
                    )

                    AppLogger.d(TAG,"$TAG 起点拦截器生效, ⚡触发宏指令: [点击起点] + [输入 $start]")
                    val msg = """
                        让我先点击搜索框输入地址。
                        do(action="Type", text="$start").
                        好的，我已经输入了"$start"
                    """.trimIndent()

                    return InterceptorResult(false, listOf(modifiedAction, typeAction), msg, null)
                }
            }
        }
        return InterceptorResult(true)
    }
}