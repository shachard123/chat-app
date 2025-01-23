package com.example.server

import com.example.models.ClientRequest
import com.example.models.ServerResponse
import com.example.utils.asFlow
import com.example.utils.formatChatMessage
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
                    this.sessionId,
                    this.roomName,
                    sendChannel
                )
            }

            is ClientRequest.OutgoingChatMessage -> {
                broadcastChatMessage(
                    this.requestId,
                    this.sessionId,
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
        requestId: String,
        username: String,
        password: String,
        sendChannel: ByteWriteChannel
    ) {
        if (!UserManager.areCredentialsValid(username, password)) {
            ServerResponse.Error(requestId, "Login failed - invalid credentials.").sendToChannel(sendChannel)
            return
        }

        val sessionId = generateId()
        SessionManager.createSession(sessionId, username, sendChannel)
        ServerResponse.LoginSuccess(requestId, sessionId,"Login successful.").sendToChannel(sendChannel)
    }

    private suspend fun joinRoom(
        requestId: String,
        sessionId: String,
        roomName: String,
        sendChannel: ByteWriteChannel
    ) {
        val session = SessionManager.getSession(sessionId)
        if (session == null) {
            ServerResponse.Error(requestId, "Invalid session.").sendToChannel(sendChannel)
            return
        }

        SessionManager.updateRoom(sessionId, roomName)
        ChatRoomManager.addSessionToRoom(roomName, sessionId)

        ServerResponse.Success(requestId, "Joined room $roomName.").sendToChannel(sendChannel)
    }

    private suspend fun broadcastChatMessage(
        id: String,
        sessionId: String,
        message: String,
        printInServer: Boolean = true
    ) {
        val session = SessionManager.getSession(sessionId) ?: return

        val username = session.username
        val roomName = session.currentRoom ?: ""

        val response = ServerResponse.IncomingChatMessage(
            responseId = id,
            sender = username,
            roomName = roomName,
            content = message
        )

        // Optionally print on the server console
        if (printInServer) {
            println(formatChatMessage(username, roomName, message))
        }

        val sessionsInRoom = ChatRoomManager.getSessionsInRoom(roomName)

        // Send the message to each session except the sender
        sessionsInRoom.forEach { otherSessionId ->
            if (otherSessionId != sessionId) {
                val otherSession = SessionManager.getSession(otherSessionId)
                otherSession?.let {
                    response.sendToChannel(it.sendChannel)
                }
            }
        }
    }

    // Extension function to send a ServerResponse to a channel
    private suspend fun ServerResponse.sendToChannel(sendChannel: ByteWriteChannel) {
        sendChannel.writeStringUtf8(Json.encodeToString(this) + "\n")
    }
}