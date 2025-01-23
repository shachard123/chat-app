package com.example.server

import com.example.models.ServerResponse
import io.ktor.utils.io.*

object ChatRoomManager {
    // roomName -> set of session IDs
    private val rooms = mutableMapOf<String, MutableSet<String>>()

    fun addSessionToRoom(roomName: String, sessionId: String) {
        rooms.computeIfAbsent(roomName) { mutableSetOf() }.add(sessionId)
    }

    fun removeSessionFromRoom(roomName: String, sessionId: String) {
        rooms[roomName]?.remove(sessionId)
        if (rooms[roomName].isNullOrEmpty()) {
            rooms.remove(roomName)
        }
    }

    fun getSessionsInRoom(roomName: String): Set<String> {
        return rooms[roomName] ?: emptySet()
    }
}