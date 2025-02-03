package com.example.utils

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers


suspend fun initializeClientSocket(host: String, port: Int): Pair<ByteWriteChannel, ByteReadChannel> {
    val selectorManager = SelectorManager(Dispatchers.IO)
    val clientSocket = aSocket(selectorManager).tcp().connect(host, port)
    return clientSocket.openWriteChannel(autoFlush = true) to clientSocket.openReadChannel()
}

suspend fun initializeServerSocket(host: String, port: Int): ServerSocket {
    val selectorManager = SelectorManager(Dispatchers.IO)
    return aSocket(selectorManager).tcp().bind(host, port)
}
