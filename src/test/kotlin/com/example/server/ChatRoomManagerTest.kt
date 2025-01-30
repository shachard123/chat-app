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


import org.junit.jupiter.api.Assertions.*

class ChatRoomManagerTest {
    private val mockClient1 = mockk<ClientHandler>(relaxed = true)
    private val mockClient2 = mockk<ClientHandler>(relaxed = true)

    @BeforeEach
    fun setup() {
        clearAllMocks()
        ChatRoomManager.apply{
            clearRooms()
        }
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `test add client to room`() {
        ChatRoomManager.addClientToRoom("room1", mockClient1)

        assertTrue(ChatRoomManager.rooms["room1"]!!.contains(mockClient1))
    }

    @Test
    fun `test remove client from room`() {
        ChatRoomManager.addClientToRoom("room1", mockClient1)
        ChatRoomManager.removeClientFromRoom("room1", mockClient1)

        assertFalse(ChatRoomManager.rooms["room1"]!!.contains(mockClient1))
    }
}