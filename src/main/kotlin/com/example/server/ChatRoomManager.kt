package com.example.server

import io.ktor.utils.io.*

object ChatRoomManager {
    //key - room name | value - username and client map
    private val rooms = mutableMapOf<String, MutableMap<String, ByteWriteChannel>>()

    fun addClientToRoom(roomName: String, username: String, channel: ByteWriteChannel) {
        if (rooms[roomName] == null) {
            createRoom(roomName)
        }
        rooms[roomName]?.put(username, channel)
    }

    fun removeClientFromRoom(roomName: String, username: String) {
        rooms[roomName]?.remove(username)
        if (rooms[roomName]?.isEmpty() == true) {
            deleteRoom(roomName)
        }
    }

    fun getClientsInRoom(roomName: String): Map<String, ByteWriteChannel>? {
        return rooms[roomName]
    }

    fun getClientsFromUser(username: String):MutableMap<String, ByteWriteChannel>?{
        for (room in rooms){
            if (room.value.containsKey(username)){
                return room.value
            }
        }
        return null
    }

    private fun createRoom(roomName: String) {
        rooms[roomName] = mutableMapOf()
    }

    private fun deleteRoom(roomName: String) {
        rooms.remove(roomName)
    }


}