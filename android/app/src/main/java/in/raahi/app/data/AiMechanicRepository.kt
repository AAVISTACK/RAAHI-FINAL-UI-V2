package `in`.raahi.app.data

import `in`.raahi.app.network.AiMessageDto
import `in`.raahi.app.network.AiSessionDto
import `in`.raahi.app.network.RaahiApi
import `in`.raahi.app.network.SendAiMessageRequest
import `in`.raahi.app.network.apiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiMechanicRepository @Inject constructor(private val api: RaahiApi) {

    suspend fun createSession(): AiSessionDto = apiCall { api.createAiSession() }

    suspend fun mySessions(): List<AiSessionDto> = apiCall { api.myAiSessions() }

    suspend fun messages(sessionId: String): List<AiMessageDto> = apiCall { api.aiSessionMessages(sessionId) }

    suspend fun sendMessage(sessionId: String, content: String): AiMessageDto =
        apiCall { api.sendAiMessage(sessionId, SendAiMessageRequest(content)) }
}
