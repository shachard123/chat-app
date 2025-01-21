package com.example.server

import com.example.models.ClientRequest
import com.example.models.ServerResponse
import com.example.utils.asFlow
import com.example.utils.generateId
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString

class ChatServer(private val host: String, private val port: Int) {

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
                launch() {
                    handleClient(socket)
                }

            }
        }
    }

    private suspend fun handleServerInput() {
        while (true) {
            val serverInput = readlnOrNull() ?: ""
            broadcastChatMessage(generateId(), "Server", serverInput, false)
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
                .collect { clientRequest ->
                    when (clientRequest) {
                        is ClientRequest.Login -> {
                            username = clientRequest.username
                            attemptLogin(
                                clientRequest.requestId,
                                clientRequest.username,
                                clientRequest.password,
                                sendChannel
                            )
                        }

                        is ClientRequest.SignUp -> {
                            username = clientRequest.username
                            attemptSignUp(
                                clientRequest.requestId,
                                clientRequest.username,
                                clientRequest.password,
                                sendChannel
                            )
                        }

                        is ClientRequest.OutgoingChatMessage -> {
                            // username already set up in login/signup
                            broadcastChatMessage(clientRequest.requestId, clientRequest.username, clientRequest.content)
                        }
                    }

                }
        } catch (e: Throwable) {
            println("Client of user $username disconnected: ${e.message}")
        } finally {
            ClientManager.removeClient(username ?: "")
            socket.close()
        }
    }

    private suspend fun attemptSignUp(
        id: String,
        username: String,
        password: String,
        sendChannel: ByteWriteChannel
    ) {
        // error if user already exists
        if (UserManager.checkUserExists(username)) {
            ServerResponse.Error(id, "Signup failed - user already exists.").sendResponse(sendChannel)
            return
        }
        // add user if user does not exist
        UserManager.addUser(username, password)
        ServerResponse.Success(id, "Signup successful.").sendResponse(sendChannel)
    }

    private suspend fun attemptLogin(
        id: String,
        username: String,
        password: String,
        sendChannel: ByteWriteChannel
    ) {
        // error if user does not exist
        if (!UserManager.areCredentialsValid(username, password)) {
            ServerResponse.Error(id, "Login failed - invalid credentials.").sendResponse(sendChannel)
            return
        }
        // add client if user exists
        ClientManager.addClient(username, sendChannel)
        ServerResponse.Success(id, "Login successful.").sendResponse(sendChannel)
        println("number of connected clients: ${ClientManager.getClients().size}")
    }

    private suspend fun broadcastChatMessage(
        id: String,
        username: String,
        message: String,
        printInServer: Boolean = true
    ) {
        val response = ServerResponse.IncomingChatMessage(id, username, message)
        if (printInServer) {
            println("${response.sender}: ${response.content}")
        }
        ClientManager.getClients().forEach { entry ->
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