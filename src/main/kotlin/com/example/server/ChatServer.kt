package com.example.server

import com.example.models.ClientRequest
import com.example.models.ServerResponse
import com.example.utils.generateId
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

//    private val clientManager = ClientManager()
//    private val userManager = UserManager()
//    private val pendingRequests = mutableMapOf<String, CompletableDeferred<ServerResponse>>()


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

    private suspend fun sendErrorResponsesWithWrongIds(sendChannel: ByteWriteChannel) {
        while (true) {
            // Generate a random ID that does not match any request ID
            val wrongId = generateId()
            val errorResponse = ServerResponse.Error(wrongId, "This is a mismatched response.")

            // Send the erroneous response
            errorResponse.sendResponse(sendChannel)

            // Add a small delay to simulate a realistic flow
            delay(500) // 500 milliseconds
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

    private fun ByteReadChannel.asFlow(): Flow<String> = flow {
        while (!isClosedForRead) {
            val line = readUTF8Line() ?: break
            emit(line)
        }
    }

    private suspend fun attemptSignUp(
        id: String,
        username: String,
        password: String,
        sendChannel: ByteWriteChannel
    ) {
        if (UserManager.checkUserExists(username)) {
            ServerResponse.Error(id, "Signup failed - user already exists.").sendResponse(sendChannel)
            return
        }
        UserManager.addUser(username, password)
        ServerResponse.Success(id, "Signup successful.").sendResponse(sendChannel)
    }

    private suspend fun attemptLogin(
        id: String,
        username: String,
        password: String,
        sendChannel: ByteWriteChannel
    ) {
        if (UserManager.checkCredentials(username, password)) {
            ClientManager.addClient(username, sendChannel)
            ServerResponse.Success(id, "Login successful.").sendResponse(sendChannel)
            println("number of connected clients: ${ClientManager.getClients().size}")
        } else {
            ServerResponse.Error(id, "Login failed - invalid credentials.").sendResponse(sendChannel)
        }
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