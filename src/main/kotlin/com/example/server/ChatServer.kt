package com.example.server

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.*

class ChatServer(private val host: String, private val port: Int) {

    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun startServer() {
        val selectorManager = SelectorManager(Dispatchers.IO)
        val serverSocket = aSocket(selectorManager).tcp().bind(host, port)

        println("Server is listening at ${serverSocket.localAddress}")

        serverScope.launch {
            listenForServerConsole()
        }

        serverScope.launch {
            acceptClientConnections(serverSocket)
        }

        //makes the server wait for the job to finish
        serverScope.coroutineContext.job.join()
    }

    private suspend fun listenForServerConsole() {
        while (serverScope.isActive) {
            val serverInput = readlnOrNull() ?: ""
            if (serverInput.startsWith("/")) {
                val withoutSlash = serverInput.drop(1).trim()
                val parts = withoutSlash.split(" ")
                val room = parts[0]
                val message = parts.drop(1).joinToString(" ")
                ChatRoomManager.broadcast(room, message)
                println("sent message to room $room")
            } else {
                ChatRoomManager.broadcastToAllRooms(serverInput)
                println("sent message to all rooms")
            }
        }
    }

    private suspend fun acceptClientConnections(serverSocket: ServerSocket) {
        while (serverScope.isActive) {
            val socket = serverSocket.accept()
            println("Accepted ${socket.remoteAddress} connection")

            // Handle each client in a separate coroutine
            serverScope.launch() {
                val handler = ClientHandler(
                    socket = socket,
                    userManager = UserManager,       // or pass references
                    chatRoomManager = ChatRoomManager
                )
                handler.run()  // run() is a suspend function that reads requests
            }
        }
    }
}