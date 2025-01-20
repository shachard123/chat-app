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
    data class ChatMessage(
        override val requestId: String,
        val username: String,
        val content: String
    ) : ClientRequest()
}
