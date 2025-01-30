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

    suspend fun broadcast(roomName: String, message: String, sender: ClientHandler? = null) {
        rooms[roomName]?.forEach { client ->
            if (client != sender) {
                client.sendResponse(ServerResponse.ChatMessage(
                    id = generateId(),
                    sender = sender?.username ?: "server",
                    room = roomName,
                    message = message
                ))
            }
        }
        //print in server if sender is a real user
        if(sender != null){
            val msg = formatChatMessage(sender.username!!, message, roomName)
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