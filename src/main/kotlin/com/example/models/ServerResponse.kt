package com.example.models

import kotlinx.serialization.Serializable

@Serializable
sealed class ServerResponse {
    abstract val id: String

    @Serializable
    data class Success(
        override val id: String,
        val message: String
    ) : ServerResponse()

    @Serializable
    data class Error(
        override val id: String,
        val message: String
    ) : ServerResponse()

    @Serializable
    data class ChatMessage(
        override val id: String,
        val sender: String,
        val room: String,
        val message: String
    ) : ServerResponse()

}
