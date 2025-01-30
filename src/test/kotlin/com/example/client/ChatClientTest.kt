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
        server = ChatServer(testHost, testPort)
        serverScope.launch { server.startServer() }

        // Delay to ensure the server is ready
        delay(100)
    }

    @AfterEach
    fun tearDown() {
        serverScope.cancel()
    }

//    @Test
//    fun `can client login`() = runTest {
//        client = ChatClient(testHost, testPort, timeout)
//        val clientJob = launch { client.startClient() }
//
//        val signInJob = launch {
//            delay(300) // Delay until start
//            client.requestLogin()
//        }
//
//        signInJob.join()
//
//        // Assert: Validate client state
//        assertTrue(client.isLoggedIn, "Client should be logged in after signup.")
//    }
}