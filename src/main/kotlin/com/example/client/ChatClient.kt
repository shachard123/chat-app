package com.example.client

import com.example.models.ClientRequest
import com.example.models.ServerResponse
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

    fun start() {
        runBlocking {
            val selectorManager = SelectorManager(Dispatchers.IO)
            val socket = aSocket(selectorManager).tcp().connect(host, port)

            val receiveChannel = socket.openReadChannel()
            val sendChannel = socket.openWriteChannel(autoFlush = true)
            val messageChannel = Channel<String>()

            // Read messages from the server and pass them to the channel
            launch {
                try {
                    while (true) {
                        val message = receiveChannel.readUTF8Line() ?: break
                        messageChannel.send(message)
                    }
                } catch (e: Exception) {
                    println("Error reading from server: ${e.message}")
                } finally {
                    messageChannel.close() // Close the channel if the connection ends
                }
            }

            // Show menu repeatedly
            while (true) {
                showMenu(sendChannel, messageChannel)
            }
        }
    }

    private suspend fun showMenu(sendChannel: ByteWriteChannel, messageChannel: ReceiveChannel<String>) {
        val option = getInput(menuString)
        when (option) {
            "${MenuOption.LOGIN.code}" -> {
                handleLogin(sendChannel, messageChannel)
            }

            "${MenuOption.SIGN_UP.code}" -> {
                handleSignUp(sendChannel, messageChannel)
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
        messageChannel: ReceiveChannel<String>
    ) {
        val username = getInput("Please enter your username: ")
        val password = getInput("Please enter your password: ")
        ClientRequest.Login(username, password).sendRequest(sendChannel)

        try {
            val response = awaitServerResponse(messageChannel)

            if (response is ServerResponse.Success) {
                clientUsername = username
                println(response.message)
                enterChat(sendChannel, messageChannel)
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
        messageChannel: ReceiveChannel<String>
    ) {
        var isValidUsername = false

        // Loop until a valid username is found
        while (!isValidUsername) {
            val username = getInput("Please enter a username: ")
            val password = getInput("Please enter a password: ")
            ClientRequest.SignUp(username, password).sendRequest(sendChannel)
            try {
                val response = awaitServerResponse(messageChannel)
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

    private suspend fun enterChat(
        sendChannel: ByteWriteChannel,
        messageChannel: ReceiveChannel<String>
    ) {
        coroutineScope {
            // Print chat messages from the server
            launch {
                println("Welcome to the chat! Type a message and press Enter to send.")
                while (true) {
                    val serverMessage = messageChannel.receive()
                    val response = Json.decodeFromString<ServerResponse>(serverMessage)
                    if (response is ServerResponse.ChatMessage) {
                        println("${response.sender}: ${response.content}")
                    } else {
                        println("Error: Unexpected server response.")
                    }
                }
            }
            // Read user input and send chat messages to the server
            launch(Dispatchers.IO) {
                while (true) {
                    val message = readlnOrNull() ?: continue
                    ClientRequest.ChatMessage(clientUsername, message).sendRequest(sendChannel)
                }
            }
        }
    }


    private suspend fun awaitServerResponse(messageChannel: ReceiveChannel<String>): ServerResponse {
        return withTimeout(timeout) {
            Json.decodeFromString(messageChannel.receive())
        }
    }

    private suspend fun ClientRequest.sendRequest(sendChannel: ByteWriteChannel) {
        val jsonString = Json.encodeToString(this) + "\n"
        sendChannel.writeStringUtf8(jsonString)
    }


    private fun getInput(string: String): String {
        print(string)
        return readlnOrNull() ?: ""
    }
}