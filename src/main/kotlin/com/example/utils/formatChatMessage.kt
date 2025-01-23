package com.example.utils

fun formatChatMessage(username: String, message: String, roomName:String? = null): String {
    if(roomName != null){
        return "[$roomName] $username: $message"
    }
    return "$username: $message"
}