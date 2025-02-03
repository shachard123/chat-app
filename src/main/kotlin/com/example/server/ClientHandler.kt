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


class ClientHandler(
    private val socket: Socket,
    private val userManager: UserManager,
    private val chatRoomManager: ChatRoomManager
) {
    private val receiveChannel = socket.openReadChannel()
    private val sendChannel = socket.openWriteChannel(autoFlush = true)

    var username: String? = null
    var currentRoom: String? = null

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

    public suspend fun handleRequest(request: ClientRequest) {
        when (request) {
            is ClientRequest.Login -> handleLogin(request)
            is ClientRequest.SignUp -> handleSignUp(request)
            is ClientRequest.JoinRoom -> handleJoinRoom(request)
            is ClientRequest.SendMessage -> handleSendMessage(request)
        }
    }

    private suspend fun handleLogin(request: ClientRequest.Login) {
        if (userManager.areCredentialsValid(request.username, request.password)) {
            username = request.username
            sendResponse(
                ServerResponse.Response(
                    id = request.id,
                    status = Status.SUCCESS,
                    message = "Login successful."
                )
            )

        } else {
            sendResponse(
                ServerResponse.Response(
                    id = request.id,
                    status = Status.ERROR,
                    message = "Login failed - invalid credentials."
                )
            )

        }
    }

    private suspend fun handleSignUp(request: ClientRequest.SignUp) {
        if (!userManager.doesUserExist(request.username)) {
            userManager.addUser(request.username, request.password)
            sendResponse(
                ServerResponse.Response(
                    id = request.id,
                    status = Status.SUCCESS,
                    message = "Signup successful."
                )
            )
        } else {
            sendResponse(
                ServerResponse.Response(
                    id = request.id,
                    status = Status.ERROR,
                    message = "Signup failed - user already exists."
                )
            )
        }
    }

    private suspend fun handleJoinRoom(request: ClientRequest.JoinRoom) {
        // check if logged in
        if (username == null) {
            sendResponse(
                ServerResponse.Response(
                    id = request.id,
                    status = Status.ERROR,
                    message = "You must be logged in to join a room."
                )
            )
            return
        }

        //If it has room already remove from room
        currentRoom?.let {
            chatRoomManager.removeClientFromRoom(it, this)
        }

        // Join the new room
        currentRoom = request.roomName
        chatRoomManager.addClientToRoom(request.roomName, this)

        sendResponse(
            ServerResponse.Response(
                id = request.id,
                status = Status.SUCCESS,
                message = "Joined room: ${request.roomName}"
            )
        )
    }

    /**
     * Handle an outgoing chat message from the client.
     */
    private suspend fun handleSendMessage(request: ClientRequest.SendMessage) {
        // error if not logged in
        if (username == null) {
            sendResponse(
                ServerResponse.Response(
                    id = request.id,
                    status = Status.ERROR,
                    message = "You must be logged in to send messages."
                )
            )
            return
        }

        // error if not in a room
        if (currentRoom == null) {
            sendResponse(
                ServerResponse.Response(
                    id = request.id,
                    status = Status.ERROR,
                    message = "You must be in a room to send messages."
                )
            )
            return
        }

        // broadcast the message
        chatRoomManager.broadcast(
            roomName = currentRoom!!,
            message = request.message,
            sender = this
        )

        sendResponse(
            ServerResponse.Response(
                id = request.id,
                status = Status.SUCCESS,
                message = "Message sent."
            )
        )
    }

    suspend fun sendResponse(response: ServerResponse) {
        val jsonString = Json.encodeToString(response)
        sendChannel.writeStringUtf8(jsonString + "\n")
    }

    private fun handleDisconnect() {
        //clean up
        currentRoom?.let {
            chatRoomManager.removeClientFromRoom(it, this)
        }
    }

}



