package com.example.client

import com.example.Configuration
import kotlinx.coroutines.runBlocking


suspend fun main() {
    ChatClient(Configuration.HOST, Configuration.PORT, Configuration.TIMEOUT).startClient()
}
