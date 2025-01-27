package com.example.server

import com.example.models.User

object UserManager {

    private val users = mutableListOf(
        User("shachar", "1234"),
        User("shachar2", "1234"),
        User("shachar3", "1234"),
        User("shachar4", "1234"),

    )

    fun addUser(username: String, password: String) {
        users.add(User(username, password))
    }

    fun removeUser(username: String) {
        users.removeIf { it.username == username }
    }


    fun areCredentialsValid(username: String, attemptedPassword: String): Boolean {
        //check if password of username equals attemptedPassword
        val realPassword = users.find { it.username == username }?.password
        return realPassword == attemptedPassword
    }

    fun doesUserExist(username: String): Boolean {
        return users.any { it.username == username }
    }

}