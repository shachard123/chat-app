package com.example.server

import com.example.models.User
import io.ktor.utils.io.*

object ClientManager {
    private val connectedClients = mutableMapOf<String, ByteWriteChannel>()

    fun addClient(username: String, channel: ByteWriteChannel) {
        connectedClients[username] = channel
    }

    fun removeClient(username: String) {
        connectedClients.remove(username)
    }

    fun getClient(username: String): ByteWriteChannel? {
        return connectedClients[username]
    }

    fun getClients(): Map<String, ByteWriteChannel> {
        return connectedClients
    }

}