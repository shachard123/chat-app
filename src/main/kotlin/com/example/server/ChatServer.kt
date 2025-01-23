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
            broadcastChatMessage(generateId(), "Server", serverInput, false)
        }
    }

    private suspend fun acceptClientConnections(serverSocket: ServerSocket) {
        while (serverScope.isActive) {
            val socket = serverSocket.accept()
            println("Accepted ${socket.remoteAddress} connection")

            // Handle each client in a separate coroutine
            serverScope.launch() {
                handleClientConnection(socket)
            }
        }
    }

    private suspend fun handleClientConnection(socket: Socket) {
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
                loginUser(
                    this.requestId,
                    this.username,
                    this.password,
                    sendChannel
                )
            }

            is ClientRequest.SignUp -> {
                signUpUser(
                    this.requestId,
                    this.username,
                    this.password,
                    sendChannel
                )
            }

            is ClientRequest.JoinChatRoom -> {
                joinRoom(
                    this.requestId,
                    this.username,
                    this.roomName,
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


    private suspend fun signUpUser(
        id: String,
        username: String,
        password: String,
        sendChannel: ByteWriteChannel
    ) {
        // error if user already exists
        if (UserManager.checkUserExists(username)) {
            ServerResponse.Error(id, "Signup failed - user already exists.").sendToChannel(sendChannel)
            return
        }
        // add user if user does not exist
        UserManager.addUser(username, password)
        ServerResponse.Success(id, "Signup successful.").sendToChannel(sendChannel)
    }

    private suspend fun loginUser(
        id: String,
        username: String,
        password: String,
        sendChannel: ByteWriteChannel
    ) {
        // error if user does not exist
        if (!UserManager.areCredentialsValid(username, password)) {
            ServerResponse.Error(id, "Login failed - invalid credentials.").sendToChannel(sendChannel)
            return
        }
        // add client if user exists
        //ClientManager.addClient(username, sendChannel)
        ServerResponse.Success(id, "Login successful.").sendToChannel(sendChannel)
        //println("number of connected clients: ${ClientManager.getClients().size}")
    }

    private suspend fun joinRoom(
        id: String,
        username: String,
        roomName: String,
        sendChannel: ByteWriteChannel
    ) {
        ChatRoomManager.addClientToRoom(roomName, username, sendChannel)
        ServerResponse.Success(id, "Joined room $roomName.").sendToChannel(sendChannel)
        println("number of clients in room $roomName: ${ChatRoomManager.getClientsInRoom(roomName)?.size}")
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
//        ClientManager.getClients().forEach { entry ->
//            if (entry.key != username) {
//                val sendChannel = entry.value
//                response.sendToChannel(sendChannel)
//            }
//        }
        ChatRoomManager.getClientsFromUser(username)?.forEach { entry ->
            if (entry.key != username) {
                val sendChannel = entry.value
                response.sendToChannel(sendChannel)
            }
        }
    }

    // Extension function to send a ServerResponse to a channel
    private suspend fun ServerResponse.sendToChannel(sendChannel: ByteWriteChannel) {
        sendChannel.writeStringUtf8(Json.encodeToString(this) + "\n")
    }
}