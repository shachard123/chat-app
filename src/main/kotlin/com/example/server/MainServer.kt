package com.example.server

import com.example.Configuration

fun main() {
    val client = ChatServer(Configuration.HOST, Configuration.PORT)
    client.start()
}