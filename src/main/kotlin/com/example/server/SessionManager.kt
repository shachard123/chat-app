package com.example.server

import io.ktor.utils.io.*

object SessionManager {
    private val sessions = mutableMapOf<String, SessionData>()

    data class SessionData(
        val sessionId: String,
        val username: String,
        var currentRoom: String?,
        val sendChannel: ByteWriteChannel
    )

    fun createSession(sessionId: String, username: String, sendChannel: ByteWriteChannel) {
        sessions[sessionId] = SessionData(sessionId, username, null, sendChannel)
    }

    fun removeSession(sessionId: String) {
        sessions.remove(sessionId)
    }

    fun updateRoom(sessionId: String, roomName: String) {
        sessions[sessionId]?.currentRoom = roomName
    }

    fun getSession(sessionId: String): SessionData? {
        return sessions[sessionId]
    }

    fun getSessionsInRoom(roomName: String): List<SessionData> {
        return sessions.values.filter { it.currentRoom == roomName }
    }

    fun getSessionByUsername(username: String): SessionData? {
        return sessions.values.firstOrNull { it.username == username }
    }
}