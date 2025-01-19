package com.example.client

import com.example.Configuration


fun main() {
    val client = ChatClient(Configuration.HOST, Configuration.PORT, Configuration.TIMEOUT)
    client.start()
}
