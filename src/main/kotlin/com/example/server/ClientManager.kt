package com.example.server

import io.ktor.utils.io.*

class ClientManager {
    private val connectedClients = mutableMapOf<String, ByteWriteChannel>()

    private val users = mutableMapOf(
        "shachar5" to "myStrongPass123",
        "shachar2" to "password"
    )

    fun addUser(username: String, password: String) {
        users[username] = password
    }

    fun removeUser(username: String) {
        users.remove(username)
    }

    fun checkCredentials(username: String, password: String): Boolean {
        return users[username] == password
    }

    fun checkUserExists(username: String): Boolean {
        return users.containsKey(username)
    }


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