package com.example.client

import com.example.models.ClientRequest
import com.example.models.ServerResponse
import com.example.utils.generateId
import com.example.utils.getInput
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlin.system.*
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString

class ChatClient(private val host: String, private val port: Int, private val timeout:Long) {

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


    fun start() {
        runBlocking {
            val selectorManager = SelectorManager(Dispatchers.IO)
            val socket = aSocket(selectorManager).tcp().connect(host, port)

            val receiveChannel = socket.openReadChannel()
            val sendChannel = socket.openWriteChannel(autoFlush = true)

            // Read messages from the server and pass them to the channel
            processReceivedData(receiveChannel)

            // Show menu repeatedly
            while (true) {
                showMenu(sendChannel)
            }
        }
    }

    private fun CoroutineScope.processReceivedData(receiveChannel: ByteReadChannel) {
        launch {
            while (true) {
                val responseJson = receiveChannel.readUTF8Line() ?: break
                val response = Json.decodeFromString<ServerResponse>(responseJson)

                when (response) {
                    is ServerResponse.IncomingChatMessage -> {
                        //send chat message
                        incomingChatMessages.send(response)
                    }

                    else -> {
                        //if response to a request, complete the request
                        pendingResponses[response.responseId]?.complete(response)
                        pendingResponses.remove(response.responseId)
                    }
                }
            }
        }
    }

    private suspend fun showMenu(sendChannel: ByteWriteChannel) {
        val option = getInput(menuString)
        when (option) {
            "${MenuOption.LOGIN.code}" -> {
                handleLogin(sendChannel)
            }

            "${MenuOption.SIGN_UP.code}" -> {
                handleSignUp(sendChannel)
            }

            "${MenuOption.EXIT.code}" -> {
                println("Exiting Shachar's Chat App. Goodbye!")
                exitProcess(0)
            }

            else -> {
                println("Invalid option, please try again.")
            }
        }
    }

    // asks the user for their username and password,
    // sends a login request to the server,
    // and prints the server's response
    private suspend fun handleLogin(
        sendChannel: ByteWriteChannel,
    ) {
        val username = getInput("Please enter your username: ")
        val password = getInput("Please enter your password: ")
        val requestId = generateId()
        val request = ClientRequest.Login(requestId,username, password)
        val response = request.sendAndAwaitResponse(sendChannel)

        try {
            if (response is ServerResponse.Success) {
                clientUsername = username
                println(response.message)
                enterChat(sendChannel)
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
    private suspend fun handleSignUp(
        sendChannel: ByteWriteChannel,
    ) {
        var isValidUsername = false

        // Loop until a valid username is found
        while (!isValidUsername) {
            val username = getInput("Please enter a username: ")
            val password = getInput("Please enter a password: ")
            val requestId = generateId()
            val request = ClientRequest.SignUp(requestId,username, password)
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

    //TODO : Replace enter chat with better implementation
    private suspend fun enterChat(
        sendChannel: ByteWriteChannel
    ) {
        coroutineScope {
            // Print chat messages from the server
            launch {
                println("Welcome to the chat! Type a message and press Enter to send.")
                while (true) {
                    val serverMessage = incomingChatMessages.receive()
                    println("${serverMessage.sender}: ${serverMessage.content}")
                }
            }
            // Read user input and send chat messages to the server
            launch(Dispatchers.IO) {
                while (true) {
                    val message = readlnOrNull() ?: continue
                    val requestId = generateId()
                    val request = ClientRequest.OutgoingChatMessage(requestId,clientUsername, message)
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