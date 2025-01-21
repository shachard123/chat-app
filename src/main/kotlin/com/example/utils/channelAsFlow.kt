package com.example.utils

import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

fun ByteReadChannel.asFlow(): Flow<String> = flow {
    while (!isClosedForRead) {
        val line = readUTF8Line() ?: break
        emit(line)
    }
}