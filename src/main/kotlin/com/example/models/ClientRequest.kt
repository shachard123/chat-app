package com.example.models

import kotlinx.serialization.Serializable

@Serializable
sealed class ClientRequest {
    @Serializable
    data class Login(val username: String, val password: String) : ClientRequest()

    @Serializable
    data class SignUp(val username: String, val password: String) : ClientRequest()

    @Serializable
    data class ChatMessage(val username: String, val content: String) : ClientRequest()
}
