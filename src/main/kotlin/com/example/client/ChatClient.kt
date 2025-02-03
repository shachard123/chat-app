package com.example.client

import com.example.models.ClientRequest
import com.example.models.ServerResponse
import com.example.utils.*
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

    private val incomingChatMessages = Channel<ServerResponse.ChatMessage>()

    private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var isLoggedIn = false

    suspend fun startClient() {
        val (sendChannel, receiveChannel) = initializeClientSocket(host,port)

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
                startChatSession(requestManager)
            }


        }

        // Wait for the client to finish
        clientScope.coroutineContext.job.join()
    }

    private suspend fun listenForServerResponses(
        receiveChannel: ByteReadChannel,
        requestManager: RequestManager
    ) {
        try {
            receiveChannel.asFlow()
                .map { Json.decodeFromString<ServerResponse>(it) }
                .collect { serverResponse ->
                    if (serverResponse is ServerResponse.ChatMessage) {
                        incomingChatMessages.send(serverResponse)
                    }
                    requestManager.completeResponse(serverResponse)
                }
        } catch (e: Exception) {
            println("Server disconnected: $e")
            clientScope.cancel()
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
            when (getInput(menuString).toInt()) {
                MenuOption.LOGIN.code -> requestLogin(requestManager)
                MenuOption.SIGN_UP.code -> requestSignUp(requestManager)
                MenuOption.EXIT.code -> exitProcess(0)
                else -> println("Invalid option, please try again.")
            }
        }
    }

    private suspend fun requestJoinRoom(requestManager: RequestManager) {
        val room = getInput("Please enter the name of the chat room you want to join: ")
        val requestId = generateId()
        val request = ClientRequest.JoinRoom(requestId, room)

        val response = requestManager.sendRequest(request)
        response.printResponse()
    }

    // send login request to server and handles the response
    private suspend fun requestLogin(requestManager: RequestManager) {
        val username = getInput("Username: ")
        val password = getInput("Password: ")
        val requestId = generateId()
        //create and send request
        val request = ClientRequest.Login(requestId, username, password)
        val response = requestManager.sendRequest(request)
        //handle response
        response.printResponse()
        isLoggedIn = response is ServerResponse.Success

    }

    // send signup request to server and handles the response
    private suspend fun requestSignUp(requestManager: RequestManager) {
        var isValidUsername = false
        while (!isValidUsername && clientScope.isActive) {
            val username = getInput("Please enter a username: ")
            val password = getInput("Please enter a password: ")
            val requestId = generateId()
            //create and send request
            val request = ClientRequest.SignUp(requestId, username, password)
            val response = requestManager.sendRequest(request)
            //handle response
            response.printResponse()
            isValidUsername = response is ServerResponse.Success
        }
    }

    private suspend fun startChatSession(
        requestManager: RequestManager
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
                    requestManager.sendRequest(request)
                }
            }
        }
    }

    private fun ServerResponse.printResponse() {
        when (this) {
            is ServerResponse.Success -> println("✅ $message")
            is ServerResponse.Error -> println("❌ $message")
            else -> println("⚠️ Unexpected server response.")
        }
    }
}