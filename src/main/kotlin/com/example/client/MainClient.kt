package com.example.client

import com.example.Configuration
import kotlinx.coroutines.runBlocking


suspend fun main() {
    runBlocking {
        val client = ChatClient(Configuration.HOST, Configuration.PORT, Configuration.TIMEOUT)
        client.startClient()
    }

}
