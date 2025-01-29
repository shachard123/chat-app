package com.example.client

import com.example.models.ClientRequest
import com.example.server.ChatServer
import io.ktor.utils.io.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ChatClientTest {
    private lateinit var server: ChatServer
    private lateinit var client: ChatClient
    private val testHost = "localhost"
    private val testPort = 8080
    private val timeout = 5000L
    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @BeforeEach
    fun setUp() = runBlocking {
        // Start the server
        server = ChatServer(testHost, testPort)
        serverScope.launch { server.startServer() }

        // Delay to ensure the server is ready
        delay(100)
    }

    @AfterEach
    fun tearDown() {
        // Stop the server and cleanup resources
        serverScope.cancel()
    }

//    @Test
//    fun `client can login and join a room`() = runTest {
//        // Arrange: Start the client
//        client = ChatClient(testHost, testPort, timeout)
//        val clientJob = launch { client.startClient() }
//
//        // Simulate user interaction
//        val signUpJob = launch {
//            delay(300) // Simulate delay for server to fully start
//            client.requestSignUp("testUser", "testPassword")
//        }
//
//        val joinRoomJob = launch {
//            delay(500) // Ensure signup completes before joining room
//            client.requestJoinRoom("testRoom")
//        }
//
//        // Wait for tasks to complete
//        signUpJob.join()
//        joinRoomJob.join()
//
//        // Assert: Validate client state
//        assertTrue(client.isLoggedIn, "Client should be logged in after signup.")
//    }
}