package com.example.server

import com.example.models.User

object UserManager {

    private val users = mutableListOf(
        User("shachar", "1234"),
        User("shachar2", "1234"),
        User("shachar3", "1234"),
        User("shachar4", "1234")
    )

    // Set to track connected users
    private val connectedUsers = mutableSetOf<String>()

    fun addUser(username: String, password: String) {
        users.add(User(username, password))
    }

    fun removeUser(username: String) {
        users.removeIf { it.username == username }
        connectedUsers.remove(username) // Clean up if the user was connected
    }

    fun areCredentialsValid(username: String, attemptedPassword: String): Boolean {
        return users.find { it.username == username }?.password == attemptedPassword
    }

    fun doesUserExist(username: String): Boolean {
        return users.any { it.username == username }
    }

    fun clearUsers() {
        users.clear()
        connectedUsers.clear() // Also clear connected users
    }

    fun addUsers(users: List<User>) {
        this.users.addAll(users)
    }

    fun isUserConnected(username: String): Boolean {
        return connectedUsers.contains(username)
    }

    fun markUserAsConnected(username: String) {
        connectedUsers.add(username)
    }

    fun markUserAsDisconnected(username: String) {
        connectedUsers.remove(username)
    }
}
