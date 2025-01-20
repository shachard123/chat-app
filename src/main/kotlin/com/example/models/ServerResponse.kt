package com.example.models

import kotlinx.serialization.Serializable

@Serializable
sealed class ServerResponse {
    abstract val responseId: String

    @Serializable
    data class Success(
        override val responseId: String,
        val message: String
    ) : ServerResponse()

    @Serializable
    data class Error(
        override val responseId: String,
        val errorMessage: String
    ) : ServerResponse()

    @Serializable
    data class ChatMessage(
        override val responseId: String,
        val sender: String,
        val content: String
    ) : ServerResponse()

}
