package com.example.models

import kotlinx.serialization.Serializable

@Serializable
sealed class ClientRequest {
    abstract val requestId: String

    @Serializable
    data class Login(
        override val requestId: String,
        val username: String, val password: String
    ) : ClientRequest()

    @Serializable
    data class SignUp(
        override val requestId: String,
        val username: String,
        val password: String
    ) : ClientRequest()

    @Serializable
    data class JoinChatRoom(
        override val requestId: String,
        val sessionId: String,
        val roomName: String
    ) : ClientRequest()

    @Serializable
    data class OutgoingChatMessage(
        override val requestId: String,
        val sessionId: String,
        val content: String
    ) : ClientRequest()
}
