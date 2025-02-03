package com.example.models

import kotlinx.serialization.Serializable

@Serializable
sealed class ServerResponse {
    abstract val id: String

    @Serializable
    data class Response(
        override val id: String,
        val status: Status,
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

enum class Status {
    SUCCESS,
    ERROR
}