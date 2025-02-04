package com.example.server

import com.example.models.ServerResponse
import com.example.utils.formatChatMessage
import com.example.utils.generateId
import io.ktor.utils.io.*

object ChatRoomManager {
    // roomName -> set of session IDs
    val rooms = mutableMapOf<String, MutableSet<ClientHandler>>()


    fun addClientToRoom(roomName: String, client: ClientHandler) {
        val room = rooms.getOrPut(roomName) { mutableSetOf() }
        room.add(client)
    }

    fun removeClientFromRoom(roomName: String, client: ClientHandler) {
        rooms[roomName]?.remove(client)
    }

    suspend fun broadcast(roomName: String, message: String, sendingClient: ClientHandler? = null) {
        rooms[roomName]?.forEach { client ->
            if (client != sendingClient) {
                client.sendResponse(ServerResponse.ChatMessage(
                    id = generateId(),
                    sender = sendingClient?.getUserName() ?: "server",
                    room = roomName,
                    message = message
                ))
            }
        }
        //print in server if sender is a real user
        sendingClient?.getUserName()?.let { username ->
            val msg = formatChatMessage(username, message, roomName)
            println(msg)
        }


    }

    suspend fun broadcastToAllRooms(message: String, sender: ClientHandler? = null) {
        rooms.keys.forEach { roomName ->
            broadcast(roomName, message, sender)
        }
    }

    fun clearRooms() {
        rooms.clear()
    }
}