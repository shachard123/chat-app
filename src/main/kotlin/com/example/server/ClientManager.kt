package com.example.server

import com.example.models.User
import io.ktor.utils.io.*

class ClientManager {
    private val connectedClients = mutableMapOf<String, ByteWriteChannel>()

    private val users = mutableListOf(
        User("shachar5", "myStrongPass123"),
        User("shachar2", "password")
    )

    fun addUser(username: String, password: String) {
        users.add(User(username, password))
    }

    fun removeUser(username: String) {
        users.removeIf { it.username == username }
    }

    fun checkCredentials(username: String, attemptedPassword: String): Boolean {
        //check if password of username equals attemptedPassword
        val realPassword = users.find { it.username == username }?.password
        return realPassword == attemptedPassword
    }

    fun checkUserExists(username: String): Boolean {
        return users.any { it.username == username }
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