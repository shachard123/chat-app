package com.example.models

import kotlinx.serialization.Serializable

@Serializable
sealed class ServerResponse {
    @Serializable
    data class Success(val message: String) : ServerResponse()

    @Serializable
    data class Error(val errorMessage: String) : ServerResponse()

    @Serializable
    data class ChatMessage(val sender: String, val content: String) : ServerResponse()

}
