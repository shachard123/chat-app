package com.example.server

import com.example.models.User

class UserManager {

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

}