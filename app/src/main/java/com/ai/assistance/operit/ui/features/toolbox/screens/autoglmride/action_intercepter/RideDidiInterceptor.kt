package com.ai.assistance.operit.ui.features.toolbox.screens.autoglmride.action_intercepter




import com.ai.assistance.operit.core.tools.agent.ModelResponse
import com.ai.assistance.operit.core.tools.agent.ParsedAgentAction
import com.ai.assistance.operit.core.tools.agent.interceptor.ActionInterceptor
import com.ai.assistance.operit.core.tools.agent.interceptor.InterceptorResult
import com.ai.assistance.operit.util.AppLogger

/***
 * 滴滴打车拦截器
 */
class RideDidiInterceptor(
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
        val TAG = "滴滴拦截器"
        val thinkingText = response.thinking ?: ""
         AppLogger.d(TAG,"$TAG 进入,stepCount:$stepCount, thinking_text:'$thinkingText',\n action:$action")

        if (action.actionName == "Tap") {
            val elementStr = action.fields["element"]
            val coords = parseElement(elementStr)
            if (coords != null) {
                val (absX, absY) = coords

                // 1. 点击终点坐标修改, 终点拦截器
                val destNewX = 272
                val destNewY = 524
                val yRange = 25

                if (absX in 201..549 && absY in (destNewY - yRange)..(destNewY + yRange) &&
                    (thinkingText.contains("搜索框") || thinkingText.contains("输入目的地")) &&
                    (start.isEmpty() || stepCount > 2) //有起点情况下,配置终点至少要是第3步,第1步是点击起点+输入地址,第2步是点击匹配的起点地址,
                ) {
                    val modifiedFields = action.fields.toMutableMap()
//                    modifiedFields["element"] = "[$destNewX, $destNewY]"
                    modifiedFields["element"] = "[$destNewX, $absY]" //只看不x坐标,解决点到搜索框右边推荐的地址问题
                    val modifiedAction = action.copy(fields = modifiedFields)

//                    if (destNewX != absX || destNewY != absY) {
////                        AppLogger.d(TAG,"$TAG 点击终点坐标修改: ($absX,$absY) -> ($destNewX,$destNewY)"
//                    }
                    AppLogger.d(TAG,"$TAG 点击终点坐标修改x坐标:终点真实坐标($absX,$absY) -> 配置坐标($destNewX,$destNewY), 差异(${absX-destNewX},${absY-destNewY})")
//                    AppLogger.d(TAG,"$TAG 点击终点坐标修改x坐标:真实坐标($absX,$absY) -> 配置坐标($destNewX,$destNewY)")

                    // 构造输入动作
                    val typeAction = ParsedAgentAction(
                        metadata = "do",
                        actionName = "Type",
                        fields = mapOf(
                            "action" to "Type",
                            "text" to destination
                        )
                    )

                    AppLogger.d(TAG,"$TAG 终点拦截器生效, ⚡触发宏指令: [点击终点] + [输入 $destination]")
                    val msg = """
                        让我先点击搜索框输入地址。
                        do(action="Type", text="$destination").
                        好的，我已经输入了"$destination"
                    """.trimIndent()

                    return InterceptorResult(false, listOf(modifiedAction, typeAction), msg, null)
                }

                // 2. 点击起点坐标修改, 起点拦截器
                //点击起点坐标修改: (499,452) -> (499,467)
                val startNewX = 499
//                val startNewY = 467
                val startNewY = 452

                var isTapStart = false

                //情况1  这块写的太复杂了.

                if (start.isNotEmpty() && (
                            (absX in 201..559 && absY in 401..(startNewY + yRange) && (thinkingText.contains("起点") || thinkingText.contains("上车"))) ||
                                    (absX in 201..559 && absY in 401..(startNewY + yRange) && (thinkingText.contains("修改起点地址") || thinkingText.contains("正在获取上车地点"))) ||
                                    (absX in 201..559 && absY < 550 && thinkingText.contains("起点") && stepCount == 1)
                            )
                ) {
                    //起点坐标不用修改
//                    val modifiedFields = action.fields.toMutableMap()
//                    modifiedFields["element"] = "[$startNewX, $startNewY]"
//                    val modifiedAction = action.copy(fields = modifiedFields)

//                    if (startNewX != absX || startNewY != absY) {
////                        AppLogger.d(TAG,"$TAG 点击起点坐标修改: ($absX,$absY) -> ($startNewX,$startNewY)")
//
//                    }
                    AppLogger.d(TAG,"$TAG 点击起点坐标不修改: 起点真实坐标($absX,$absY) -> 配置坐标($startNewX,$startNewY), 差异(${absX-startNewX},${absY-startNewY})")
//                    AppLogger.d(TAG,"$TAG 点击起点坐标不修改: 真实坐标($absX,$absY) -> 配置坐标($startNewX,$startNewY)")

                    val typeAction = ParsedAgentAction(
                        metadata = "do",
                        actionName = "Type",
                        fields = mapOf(
                            "action" to "Type",
                            "text" to start
                        )
                    )

                    AppLogger.d(TAG,"$TAG 起点拦截器生效, ⚡触发宏指令: [点击起点] + [输入 $start]")
                    val msg = """
                        让我先点击搜索框输入地址。
                        do(action="Type", text="$start").
                        好的，我已经输入了"$start"
                    """.trimIndent()

                    return InterceptorResult(false, listOf(action, typeAction), msg, null)
                }
            }
        }
        return InterceptorResult(true)
    }
}