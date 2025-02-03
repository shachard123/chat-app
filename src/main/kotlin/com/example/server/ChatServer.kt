package com.example.server

import com.example.utils.initializeServerSocket
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.*

class ChatServer(private val host: String, private val port: Int) {

    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun startServer() {
        val serverSocket = initializeServerSocket(host, port)
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
            val input = readlnOrNull() ?: continue
            val (room, message) = parseServerInput(input)

            if (room != null) {
                ChatRoomManager.broadcast(room, message)
                println("Sent message to room $room")
            } else {
                ChatRoomManager.broadcastToAllRooms(message)
                println("Sent message to all rooms")
            }
        }
    }


    private fun parseServerInput(input: String): Pair<String?, String> {
        return if (input.startsWith("/")) {
            val withoutSlash = input.drop(1).trim()
            val parts = withoutSlash.split(" ")
            val room = parts.getOrNull(0)
            val message = parts.drop(1).joinToString(" ")
            Pair(room, message)
        } else {
            Pair(null, input) // No room specified, it's a broadcast
        }
    }

    private suspend fun acceptClientConnections(serverSocket: ServerSocket) {
        while (serverScope.isActive) {
            val socket = serverSocket.accept()
            println("Accepted ${socket.remoteAddress} connection")

            // Handle each client in a separate coroutine
            serverScope.launch() {
                ClientHandler(
                    socket = socket,
                    userManager = UserManager,       // or pass references
                    chatRoomManager = ChatRoomManager
                ).run()
            }
        }
    }
}