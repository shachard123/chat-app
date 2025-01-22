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

    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun start() {
        val selectorManager = SelectorManager(Dispatchers.IO)
        val serverSocket = aSocket(selectorManager).tcp().bind(host, port)
        println("Server is listening at ${serverSocket.localAddress}")


        serverScope.launch {
            handleServerInput()
        }

        serverScope.launch {
            acceptClients(serverSocket)
        }

        //makes the server wait for the job to finish
        serverScope.coroutineContext.job.join()
    }

    private suspend fun handleServerInput() {
        while (serverScope.isActive) {
            val serverInput = readlnOrNull() ?: ""
            broadcastChatMessage(generateId(), "Server", serverInput, false)
        }
    }

    private suspend fun acceptClients(serverSocket: ServerSocket) {
        while (serverScope.isActive) {
            val socket = serverSocket.accept()
            println("Accepted ${socket.remoteAddress} connection")

            // Handle each client in a separate coroutine
            serverScope.launch() {
                handleClient(socket)
            }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        val receiveChannel = socket.openReadChannel()
        val sendChannel = socket.openWriteChannel(autoFlush = true)
        try {
            receiveChannel
                .asFlow()
                .map { Json.decodeFromString<ClientRequest>(it) }
                .collect { clientRequest ->
                    clientRequest.handleClientRequest(sendChannel)

                }
        } catch (e: Throwable) {
            println("a client has disconnected")
        } finally {
            socket.close()
        }
    }

    private suspend fun ClientRequest.handleClientRequest(
        sendChannel: ByteWriteChannel
    ) {
        when (this) {
            is ClientRequest.Login -> {
                attemptLogin(
                    this.requestId,
                    this.username,
                    this.password,
                    sendChannel
                )
            }

            is ClientRequest.SignUp -> {
                attemptSignUp(
                    this.requestId,
                    this.username,
                    this.password,
                    sendChannel
                )
            }

            is ClientRequest.OutgoingChatMessage -> {
                broadcastChatMessage(
                    this.requestId,
                    this.username,
                    this.content
                )
            }
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