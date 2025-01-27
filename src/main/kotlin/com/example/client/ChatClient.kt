package com.example.client

import com.example.models.ClientRequest
import com.example.models.ServerResponse
import com.example.utils.asFlow
import com.example.utils.formatChatMessage
import com.example.utils.generateId
import com.example.utils.getInput
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.map
import kotlin.system.*
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString

class ChatClient(private val host: String, private val port: Int, private val timeout: Long) {

    //store responses by request id to be able to match them
    private val pendingRequests = mutableMapOf<String, CompletableDeferred<ServerResponse>>()
    //incoming chat messages
    private val incomingChatMessages = Channel<ServerResponse.ChatMessage>()

    private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var isLoggedIn = false

    suspend fun startClient() {
        val selectorManager = SelectorManager(Dispatchers.IO)
        val clientSocket = aSocket(selectorManager).tcp().connect(host, port)

        val sendChannel = clientSocket.openWriteChannel(autoFlush = true)
        val receiveChannel = clientSocket.openReadChannel()

        // Read messages from the server and pass them to the channel
        clientScope.launch {
            listenForServerResponses(receiveChannel)
        }


        // login and show chat
        clientScope.launch {
            runMainMenuLoop(sendChannel)

            if(isLoggedIn) {
                requestJoinRoom(sendChannel)
                startChatSession(sendChannel)
            }


        }

        // Wait for the client to finish
        clientScope.coroutineContext.job.join()
    }

    private suspend fun listenForServerResponses(receiveChannel : ByteReadChannel) {
        receiveChannel
            .asFlow()
            .map { Json.decodeFromString<ServerResponse>(it) }
            .collect { serverResponse ->
                serverResponse.handleServerResponse(receiveChannel)
            }
    }

    private suspend fun ServerResponse.handleServerResponse(receiveChannel: ByteReadChannel) {
        when (this) {
            is ServerResponse.ChatMessage -> {
                //send chat message
                incomingChatMessages.send(this)
            }

            is ServerResponse.Success,
            is ServerResponse.Error -> {
                //if response to a request, complete the request
                pendingRequests[id]?.complete(this)
                pendingRequests.remove(id)
            }
        }
    }

    private suspend fun runMainMenuLoop(sendChannel : ByteWriteChannel) {
        val menuString = """
        ============================================
                   Shachar's Chat App
        ============================================
        
        [${MenuOption.LOGIN.code}] Login
        [${MenuOption.SIGN_UP.code}] Sign Up
        [${MenuOption.EXIT.code}] Exit
        
        ============================================
        Please select an option:
        
        """.trimIndent()

        while (!isLoggedIn && clientScope.isActive) {
            val option = getInput(menuString)
            when (option) {
                "${MenuOption.LOGIN.code}" -> requestLogin(sendChannel)

                "${MenuOption.SIGN_UP.code}" -> requestSignUp(sendChannel)

                "${MenuOption.EXIT.code}" -> exitProcess(0)

                else -> println("Invalid option, please try again.")
            }
        }
    }

    private suspend fun requestJoinRoom(sendChannel: ByteWriteChannel) {
        val room = getInput("Please enter the name of the chat room you want to join: ")
        val requestId = generateId()
        val request = ClientRequest.JoinRoom(requestId, room)

        val response = request.sendAndAwaitResponse(sendChannel)
        when (response) {
            is ServerResponse.Success -> println("Joined room: $room")
            is ServerResponse.Error -> println("Failed to join room: ${response.message}")
            else -> println("Unexpected response to join room.")
        }
    }

    // send login request to server and handles the response
    private suspend fun requestLogin(sendChannel: ByteWriteChannel) {
        val enteredUsername = getInput("Username: ")
        val enteredPassword = getInput("Password: ")
        val requestId = generateId()

        val request = ClientRequest.Login(requestId, enteredUsername, enteredPassword)
        val response = request.sendAndAwaitResponse(sendChannel)

        when (response) {
            is ServerResponse.Success -> {
                isLoggedIn = true
                println("Login successful: ${response.message}")
            }
            is ServerResponse.Error -> {
                println("Login failed: ${response.message}")
            }
            else -> {
                println("Unexpected response to login.")
            }
        }
    }

    // send signup request to server and handles the response
    private suspend fun requestSignUp(
        sendChannel: ByteWriteChannel,
    ) {
        var isValidUsername = false

        // Loop until a valid username is found
        while (!isValidUsername && clientScope.isActive) {
            val username = getInput("Please enter a username: ")
            val password = getInput("Please enter a password: ")
            val requestId = generateId()
            val request = ClientRequest.SignUp(requestId, username, password)
            try {
                val response = request.sendAndAwaitResponse(sendChannel)
                if (response is ServerResponse.Success) {
                    isValidUsername = true
                    println(response.message)
                    println("Please log in with your new account.")
                } else if (response is ServerResponse.Error) {
                    println(response.message)
                }
            } catch (e: TimeoutCancellationException) {
                println("Signup failed: Server did not respond in time.")
            }
        }
    }

    private suspend fun startChatSession(
        sendChannel: ByteWriteChannel
    ) {
        coroutineScope {
            // receive messages
            launch {
                println("Welcome to the chat! Type a message and press Enter to send.")
                while (isActive) {
                    val serverMessage = incomingChatMessages.receive()
                    val formattedMessage = formatChatMessage(serverMessage.sender, serverMessage.message, serverMessage.room)
                    println(formattedMessage)
                }
            }
            // send messages
            launch(Dispatchers.IO) {
                while (isActive) {
                    val message = readlnOrNull() ?: continue
                    val requestId = generateId()
                    val request = ClientRequest.SendMessage(requestId, message)
                    request.send(sendChannel)
                }
            }
        }
    }


    private suspend fun ClientRequest.sendAndAwaitResponse(sendChannel: ByteWriteChannel): ServerResponse {
        //create pending response
        val pendingResponse = CompletableDeferred<ServerResponse>()
        pendingRequests[id] = pendingResponse

        //send the request
        this.send(sendChannel)

        //finish when the response is received
        return withTimeout(timeout) {
            pendingResponse.await()
        }
    }

    private suspend fun ClientRequest.send(sendChannel: ByteWriteChannel) {
        //send the request
        val jsonString = Json.encodeToString(this) + "\n"
        sendChannel.writeStringUtf8(jsonString)
    }
}