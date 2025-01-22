package com.example.server

import com.example.Configuration
import kotlinx.coroutines.*


fun main() {
    runBlocking {
        val client = ChatServer(Configuration.HOST, Configuration.PORT)
        client.start()

    }
}