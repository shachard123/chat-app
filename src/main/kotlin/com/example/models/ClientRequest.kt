package com.example.models

import kotlinx.serialization.Serializable

@Serializable
sealed class ClientRequest {
    abstract val id: String

    @Serializable
    data class Login(
        override val id: String,
        val username: String,
        val password: String
    ) : ClientRequest()

    @Serializable
    data class SignUp(
        override val id: String,
        val username: String,
        val password: String
    ) : ClientRequest()

    @Serializable
    data class JoinRoom(
        override val id: String,
        val roomName: String
    ) : ClientRequest()

    @Serializable
    data class SendMessage(
        override val id: String,
        val message: String
    ) : ClientRequest()
}
