package com.example.server

import com.example.models.ClientRequest
import com.example.models.ServerResponse
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString

class ChatServer(private val host: String, private val port: Int) {

    private val clientManager = ClientManager()

    fun start() {
        runBlocking {
            val selectorManager = SelectorManager(Dispatchers.IO)
            val serverSocket = aSocket(selectorManager).tcp().bind(host, port)
            println("Server is listening at ${serverSocket.localAddress}")

            launch(Dispatchers.IO) {
                handleServerInput()
            }

            // Accept clients in the main coroutine
            while (true) {
                val socket = serverSocket.accept()
                println("Accepted $socket")

                // Handle each client in a separate coroutine
                launch(Dispatchers.IO) {
                    handleClient(socket)
                }
            }
        }
    }

    private suspend fun handleServerInput() {
        while (true) {
            val serverInput = readlnOrNull() ?: ""
            broadcastChatMessage("Server", serverInput, false)
        }
    }

    private suspend fun handleClient(socket: Socket) {
        val receiveChannel = socket.openReadChannel()
        val sendChannel = socket.openWriteChannel(autoFlush = true)

        var username: String? = null
        try {
            receiveChannel
                .asFlow()
                .map { Json.decodeFromString<ClientRequest>(it) }
                .collect{ clientRequest ->
                    when (clientRequest) {
                        is ClientRequest.Login -> {
                            username = clientRequest.username
                            attemptLogin(clientRequest.username, clientRequest.password, sendChannel)
                        }

                        is ClientRequest.SignUp -> {
                            username = clientRequest.username
                            attemptSignUp(clientRequest.username, clientRequest.password, sendChannel)
                        }

                        is ClientRequest.ChatMessage -> {
                            // username already set up in login/signup
                            broadcastChatMessage(clientRequest.username, clientRequest.content)
                        }
                    }

                }
        } catch (e: Throwable) {
            println("Client of user $username disconnected: ${e.message}")
        } finally {
            clientManager.removeClient(username ?: "")
            socket.close()

        }
    }

    private fun ByteReadChannel.asFlow(): Flow<String> = flow {
        while (!isClosedForRead) {
            val line = readUTF8Line() ?: break
            emit(line)
        }
    }

    private suspend fun attemptSignUp(
        username: String,
        password: String,
        sendChannel: ByteWriteChannel
    ) {
        if (clientManager.checkUserExists(username)) {
            ServerResponse.Error("Signup failed - user already exists.").sendResponse(sendChannel)
            return
        }
        clientManager.addUser(username, password)
        ServerResponse.Success("Signup successful.").sendResponse(sendChannel)
    }

    private suspend fun attemptLogin(
        username: String,
        password: String,
        sendChannel: ByteWriteChannel
    ) {
        if (clientManager.checkCredentials(username, password)) {
            clientManager.addClient(username, sendChannel)
            ServerResponse.Success("Login successful.").sendResponse(sendChannel)
            println("number of connected clients: ${clientManager.getClients().size}")
        } else {
            ServerResponse.Error("Login failed - invalid credentials.").sendResponse(sendChannel)
        }
    }

    private suspend fun broadcastChatMessage(username: String, message: String, printInServer: Boolean = true) {
        val response = ServerResponse.ChatMessage(username, message)
        if (printInServer) {
            println("${response.sender}: ${response.content}")
        }
        clientManager.getClients().forEach { entry ->
            if (entry.key != username) {
                val sendChannel = entry.value
                response.sendResponse(sendChannel)
            }
        }
    }

    // Extension function to send a ServerResponse to a channel
    private suspend fun ServerResponse.sendResponse(sendChannel: ByteWriteChannel) {
        sendChannel.writeStringUtf8(Json.encodeToString(this) + "\n")
    }
}