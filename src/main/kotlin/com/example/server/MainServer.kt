package com.example.server

import com.example.Configuration
import kotlinx.coroutines.*


suspend fun main() {
    ChatServer(Configuration.HOST, Configuration.PORT).startServer()
}