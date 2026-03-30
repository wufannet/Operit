package com.ai.assistance.operit.ui.features.toolbox.screens.autoglmride.action_intercepter

import com.ai.assistance.operit.core.tools.agent.ActionHandler
import com.ai.assistance.operit.core.tools.agent.ModelResponse
import com.ai.assistance.operit.core.tools.agent.ParsedAgentAction
import com.ai.assistance.operit.core.tools.agent.interceptor.ActionInterceptor
import com.ai.assistance.operit.core.tools.agent.interceptor.InterceptorResult
import com.ai.assistance.operit.util.AppLogger

class RideHailingSafetyInterceptor : ActionInterceptor() {

    override fun beforeExecute(
        action: ParsedAgentAction,
        screenWidth: Int,
        screenHeight: Int,
        stepCount: Int,
        response: ModelResponse
    ): InterceptorResult {
        val TAG = "打车安全拦截器"
        if (action.actionName == "Tap" && stepCount > 2) {
            val elementStr = action.fields["element"]
            val coords = parseElement(elementStr)

            if (coords != null) {
                val (absX, absY) = coords

                if (absX > 500 && absY > 850) {
                    val errorMsg = "触发安全风控：打车呼叫错误,禁止执行打车呼叫区域的点击"
                    AppLogger.d(TAG,"$TAG error_msg: $errorMsg")

                    val actionExecResult = ActionHandler.ActionExecResult(
                        success = false,
                        shouldFinish = true,
                        message = errorMsg
                    )

                    return InterceptorResult(
                        passed = false,
                        modifiedActions = null,
                        message = errorMsg,
                        actionResult = actionExecResult
                    )
                }
            }
        }
        return InterceptorResult(true)
    }
}