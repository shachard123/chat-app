package com.example.client

import com.example.models.ClientRequest
import com.example.models.ServerResponse
import com.example.utils.asFlow
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

    private val menuString = """
    ============================================
               Shachar's Chat App
    ============================================
    
    [${MenuOption.LOGIN.code}] Login
    [${MenuOption.SIGN_UP.code}] Sign Up
    [${MenuOption.EXIT.code}] Exit
    
    ============================================
    Please select an option:
    
    """.trimIndent()

    private var clientUsername = ""

    //store responses by request id to be able to match them
    private val pendingResponses = mutableMapOf<String, CompletableDeferred<ServerResponse>>()

    //incoming chat messages
    private val incomingChatMessages = Channel<ServerResponse.IncomingChatMessage>()

    private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var isLoggedIn = false


    suspend fun startClient() {
        val selectorManager = SelectorManager(Dispatchers.IO)
        val clientSocket = aSocket(selectorManager).tcp().connect(host, port)

        val sendChannel = clientSocket.openWriteChannel(autoFlush = true)

        // Read messages from the server and pass them to the channel
        clientScope.launch {
            listenForServerResponses(clientSocket)
        }


        // login and show chat
        clientScope.launch {
            runMainMenuLoop(clientSocket)

            if(isLoggedIn) {
                startChatSession(sendChannel)
            }
        }
    }

    private suspend fun listenForServerResponses(socket: Socket) {
        val receiveChannel = socket.openReadChannel()
        receiveChannel
            .asFlow()
            .map { Json.decodeFromString<ServerResponse>(it) }
            .collect { serverResponse ->
                serverResponse.handleServerResponse(receiveChannel)
            }
    }

    private suspend fun ServerResponse.handleServerResponse(receiveChannel: ByteReadChannel) {
        when (this) {
            is ServerResponse.IncomingChatMessage -> {
                //send chat message
                incomingChatMessages.send(this)
            }

            else -> {
                //if response to a request, complete the request
                pendingResponses[responseId]?.complete(this)
                pendingResponses.remove(responseId)
            }
        }
    }

    private suspend fun runMainMenuLoop(socket : Socket) {
        val sendChannel = socket.openWriteChannel(autoFlush = true)
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

    // asks the user for their username and password,
    // sends a login request to the server,
    // and prints the server's response
    private suspend fun requestLogin(
        sendChannel: ByteWriteChannel,
    ) {
        val username = getInput("Please enter your username: ")
        val password = getInput("Please enter your password: ")
        val requestId = generateId()
        val request = ClientRequest.Login(requestId, username, password)
        val response = request.sendAndAwaitResponse(sendChannel)

        try {
            if (response is ServerResponse.Success) {
                clientUsername = username
                isLoggedIn = true
                println(response.message)
            } else if (response is ServerResponse.Error) {
                println(response.errorMessage)
            }
        } catch (e: TimeoutCancellationException) {
            println("Login failed: Server did not respond in time.")
        }
    }

    // asks the user for a new username and password,
    // sends a sign-up request to the server,
    // and prints the server's response
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
                    println(response.errorMessage)
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
                    println("${serverMessage.sender}: ${serverMessage.content}")
                }
            }
            // send messages
            launch(Dispatchers.IO) {
                while (isActive) {
                    val message = readlnOrNull() ?: continue
                    val requestId = generateId()
                    val request = ClientRequest.OutgoingChatMessage(requestId, clientUsername, message)
                    request.send(sendChannel)
                }
            }
        }
    }


    private suspend fun ClientRequest.sendAndAwaitResponse(sendChannel: ByteWriteChannel): ServerResponse {
        //create pending response
        val pendingResponse = CompletableDeferred<ServerResponse>()
        pendingResponses[requestId] = pendingResponse

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