package com.example.server

import com.example.models.ClientRequest
import com.example.models.ServerResponse
import com.example.models.Status
import com.example.utils.asFlow
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.bson.types.ObjectId

class ClientHandler(
    private val socket: Socket,
    private val userManager: UserManager,
    private val chatRoomManager: ChatRoomManager
) {
    private val receiveChannel = socket.openReadChannel()
    private val sendChannel = socket.openWriteChannel(autoFlush = true)

    private var userId: ObjectId? = null
    private var currentRoom: String? = null

    suspend fun run() {
        try {
            receiveChannel
                .asFlow()
                .map { Json.decodeFromString<ClientRequest>(it) }
                .collect { request -> handleRequest(request) }
        } catch (e: Throwable) {
            println("Client disconnected: $e")
        } finally {
            handleDisconnect()
            socket.close()
        }
    }

    suspend fun handleRequest(request: ClientRequest) {
        when (request) {
            is ClientRequest.Login -> handleLogin(request)
            is ClientRequest.SignUp -> handleSignUp(request)
            is ClientRequest.JoinRoom -> handleJoinRoom(request)
            is ClientRequest.SendMessage -> handleSendMessage(request)
        }
    }

    private suspend fun handleLogin(request: ClientRequest.Login) {
        val user = userManager.getUserByUsername(request.username)
        if (user == null) {
            sendErrorResponse(request.id, "Login failed - user not found.")
            return
        }

        if (userManager.isUserConnected(user._id)) {
            sendErrorResponse(request.id, "User is already connected.")
            return
        }

        if (userManager.areCredentialsValid(request.username, request.password)) {
            userId = user._id
            userManager.markUserAsConnected(user._id)
            sendSuccessResponse(request.id, "Login successful.")
        } else {
            sendErrorResponse(request.id, "Login failed - invalid credentials.")
        }
    }

    private suspend fun handleSignUp(request: ClientRequest.SignUp) {
        if (!userManager.doesUserExist(request.username)) {
            userManager.addUser(request.username, request.password)
            sendSuccessResponse(request.id, "Signup successful.")
        } else {
            sendErrorResponse(request.id, "Signup failed - user already exists.")
        }
    }

    private fun isLoggedIn() = userId != null

    private suspend fun handleJoinRoom(request: ClientRequest.JoinRoom) {
        if (!isLoggedIn()) {
            sendErrorResponse(request.id, "You must be logged in to join a room.")
            return
        }

        currentRoom?.let { chatRoomManager.removeClientFromRoom(it, this) }

        currentRoom = request.roomName
        chatRoomManager.addClientToRoom(request.roomName, this)

        sendSuccessResponse(request.id, "Joined room: ${request.roomName}")
    }

    private suspend fun handleSendMessage(request: ClientRequest.SendMessage) {
        if (!isLoggedIn()) {
            sendErrorResponse(request.id, "You must be logged in to send messages.")
            return
        }

        currentRoom?.let { room ->
            chatRoomManager.broadcast(room, request.message, this)
            sendSuccessResponse(request.id, "Message sent.")
        } ?: sendErrorResponse(request.id, "You must be in a room to send messages.")
    }

    suspend fun sendResponse(response: ServerResponse) {
        val jsonString = Json.encodeToString(response)
        sendChannel.writeStringUtf8(jsonString + "\n")
    }

    private suspend fun sendSuccessResponse(id: String, message: String) {
        sendResponse(ServerResponse.Response(id, Status.SUCCESS, message))
    }

    private suspend fun sendErrorResponse(id: String, message: String) {
        sendResponse(ServerResponse.Response(id, Status.ERROR, message))
    }

    private fun handleDisconnect() {
        currentRoom?.let {
            chatRoomManager.removeClientFromRoom(it, this)
        }
        userId?.let { userManager.markUserAsDisconnected(it) }
    }

    public fun getUserName(): String? {
        return userManager.getUserById(userId!!)?.username
    }
}
