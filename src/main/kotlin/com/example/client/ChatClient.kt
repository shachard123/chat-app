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
import org.jetbrains.annotations.VisibleForTesting

class ChatClient(private val host: String, private val port: Int, private val timeout: Long) {

    private val incomingChatMessages = Channel<ServerResponse.ChatMessage>()

    private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var isLoggedIn = false

    suspend fun startClient() {
        val selectorManager = SelectorManager(Dispatchers.IO)
        val clientSocket = aSocket(selectorManager).tcp().connect(host, port)

        val sendChannel = clientSocket.openWriteChannel(autoFlush = true)
        val receiveChannel = clientSocket.openReadChannel()

        val requestManager = RequestManager(sendChannel, timeout)

        // Read messages from the server and pass them to the channel
        clientScope.launch {
            listenForServerResponses(receiveChannel, requestManager)
        }

        // login and show chat
        clientScope.launch {
            runMainMenuLoop(requestManager)

            if (isLoggedIn) {
                requestJoinRoom(requestManager)
                startChatSession(sendChannel)
            }


        }

        // Wait for the client to finish
        clientScope.coroutineContext.job.join()
    }

    private suspend fun listenForServerResponses(
        receiveChannel: ByteReadChannel,
        requestManager: RequestManager
    ) {
        receiveChannel.asFlow()
            .map { Json.decodeFromString<ServerResponse>(it) }
            .collect { serverResponse ->
                if(serverResponse is ServerResponse.ChatMessage){
                    incomingChatMessages.send(serverResponse)
                }
                requestManager.completeResponse(serverResponse)
//                when (serverResponse) {
//                    is ServerResponse.ChatMessage -> incomingChatMessages.send(serverResponse)
//                    is ServerResponse.Success,
//                    is ServerResponse.Error -> requestManager.completeResponse(serverResponse)
//                }
            }
    }

    private suspend fun runMainMenuLoop(requestManager: RequestManager) {
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
            when (val option = getInput(menuString)) {
                "${MenuOption.LOGIN.code}" -> requestLogin(requestManager)
                "${MenuOption.SIGN_UP.code}" -> requestSignUp(requestManager)
                "${MenuOption.EXIT.code}" -> exitProcess(0)
                else -> println("Invalid option, please try again.")
            }
        }
    }

    private suspend fun requestJoinRoom(requestManager: RequestManager) {
        val room = getInput("Please enter the name of the chat room you want to join: ")
        val requestId = generateId()
        val request = ClientRequest.JoinRoom(requestId, room)

        when (val response = requestManager.sendRequest(request)) {
            is ServerResponse.Success -> println("Joined room: $room")
            is ServerResponse.Error -> println("Failed to join room: ${response.message}")
            else -> println("Unexpected response to join room.")
        }
    }

    // send login request to server and handles the response
    private suspend fun requestLogin(requestManager: RequestManager) {
        val username = getInput("Username: ")
        val password = getInput("Password: ")
        val requestId = generateId()
        val request = ClientRequest.Login(requestId, username, password)
        when (val response = requestManager.sendRequest(request)) {
            is ServerResponse.Success -> {
                isLoggedIn = true
                println("Login successful: ${response.message}")
            }

            is ServerResponse.Error -> println("Login failed: ${response.message}")
            else -> println("Unexpected response to login.")
        }
    }

    // send signup request to server and handles the response
    private suspend fun requestSignUp(requestManager: RequestManager) {
        var isValidUsername = false
        while (!isValidUsername && clientScope.isActive) {
            val username = getInput("Please enter a username: ")
            val password = getInput("Please enter a password: ")
            val requestId = generateId()
            val request = ClientRequest.SignUp(requestId, username, password)
            when (val response = requestManager.sendRequest(request)) {
                is ServerResponse.Success -> {
                    isValidUsername = true
                    println(response.message)
                    println("Please log in with your new account.")
                }

                is ServerResponse.Error -> println(response.message)
                else -> println("Unexpected response to sign up.")
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
                    val formattedMessage =
                        formatChatMessage(serverMessage.sender, serverMessage.message, serverMessage.room)
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

    private suspend fun ClientRequest.send(sendChannel: ByteWriteChannel) {
        //send the request
        val jsonString = Json.encodeToString(this) + "\n"
        sendChannel.writeStringUtf8(jsonString)
    }
}