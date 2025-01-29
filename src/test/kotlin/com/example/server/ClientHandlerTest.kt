package com.example.server

import com.example.models.ClientRequest
import com.example.models.ServerResponse
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class ClientHandlerTest {
    private val mockSocket = mockk<Socket>(relaxed = true)
    private val mockUserManager = mockk<UserManager>(relaxed = true)
    private val mockChatRoomManager = mockk<ChatRoomManager>(relaxed = true)
    private val mockReadChannel = mockk<ByteReadChannel>(relaxed = true)
    private val mockWriteChannel = mockk<ByteWriteChannel>(relaxed = true)

    private lateinit var spiedClientHandler: ClientHandler

    @BeforeEach
    fun setup() {
        every { mockSocket.openReadChannel() } returns mockReadChannel
        every { mockSocket.openWriteChannel(autoFlush = true) } returns mockWriteChannel
        val clientHandler = ClientHandler(mockSocket, mockUserManager, mockChatRoomManager)
        spiedClientHandler = spyk(clientHandler)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `test valid login`() = runTest {
        val request = ClientRequest.Login("1", "validUser", "pass123")
        // Mock the UserManager to return true for valid credentials
        every { mockUserManager.areCredentialsValid("validUser", "pass123") } returns true

        spiedClientHandler.handleRequest(request)

        assertEquals("validUser", spiedClientHandler.username)
        coVerify {
            spiedClientHandler.sendResponse(
                withArg { response ->
                    // Check that it's indeed a success response
                    assertTrue(response is ServerResponse.Success)
                }
            )
        }
    }

    @Test
    fun `test invalid login`() = runTest {
        val request = ClientRequest.Login("1", "invalidUser", "wrongPass")
        every { mockUserManager.areCredentialsValid("invalidUser", "wrongPass") } returns false

        spiedClientHandler.handleRequest(request)

        coVerify {
            spiedClientHandler.sendResponse(
                withArg { response ->
                    assertTrue(response is ServerResponse.Error)
                }
            )
        }
    }

    @Test
    fun `test successful signup`() = runTest {
        val request = ClientRequest.SignUp("1", "newUser", "password")
        every { mockUserManager.doesUserExist("newUser") } returns false
        justRun { mockUserManager.addUser("newUser", "password") }

        spiedClientHandler.handleRequest(request)

        coVerify {
            spiedClientHandler.sendResponse(
                withArg { response ->
                    assertTrue(response is ServerResponse.Success)
                }
            )
        }
    }

    @Test
    fun `test signup failure - user already exists`() = runTest {
        val request = ClientRequest.SignUp("1", "existingUser", "password")
        every { mockUserManager.doesUserExist("existingUser") } returns true

        spiedClientHandler.handleRequest(request)

        coVerify {
            spiedClientHandler.sendResponse(
                withArg { response ->
                    assertTrue(response is ServerResponse.Error)
                }
            )
        }
    }



    @Test
    fun `test join room when logged in`() = runTest {
        spiedClientHandler.username = "validUser"
        val request = ClientRequest.JoinRoom("1", "room1")
        justRun { mockChatRoomManager.addClientToRoom("room1", spiedClientHandler) }

        spiedClientHandler.handleRequest(request)

        assertEquals("room1", spiedClientHandler.currentRoom)
        coVerify {
            spiedClientHandler.sendResponse(
                withArg { response ->
                    assertTrue(response is ServerResponse.Success)
                }
            )
        }
    }

    @Test
    fun `test join room without logging in`() = runTest {
        val request = ClientRequest.JoinRoom("1", "room1")

        spiedClientHandler.handleRequest(request)

        coVerify {
            spiedClientHandler.sendResponse(
                withArg { response ->
                    assertTrue(response is ServerResponse.Error)
                }
            )
        }
    }

    @Test
    fun `test send message when in room`() = runTest {
        spiedClientHandler.username = "validUser"
        spiedClientHandler.currentRoom = "room1"
        val request = ClientRequest.SendMessage("1", "Hello!")
        //lets the function run without actually sending a message
        //justRun { mockChatRoomManager.broadcast("room1", "Hello!", spiedClientHandler) }

        spiedClientHandler.handleRequest(request)

        coVerify {
            mockChatRoomManager.broadcast("room1", "Hello!", spiedClientHandler)
        }
    }

    @Test
    fun `test send message without joining room`() = runTest {
        spiedClientHandler.username = "validUser"
        val request = ClientRequest.SendMessage("1", "Hello!")

        spiedClientHandler.handleRequest(request)

        coVerify {
            spiedClientHandler.sendResponse(
                withArg { response ->
                    assertTrue(response is ServerResponse.Error)
                }
            )
        }
    }


}