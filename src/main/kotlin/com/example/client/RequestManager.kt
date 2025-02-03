package com.example.client

import com.example.models.ClientRequest
import com.example.models.ServerResponse
import com.example.models.Status
import io.ktor.utils.io.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString

class RequestManager(private val sendChannel: ByteWriteChannel, private val timeout: Long) {
    private val pendingRequests = mutableMapOf<String, CompletableDeferred<ServerResponse>>()

    suspend fun sendRequest(request: ClientRequest): ServerResponse {
        val pendingResponse = CompletableDeferred<ServerResponse>()
        pendingRequests[request.id] = pendingResponse

        val jsonString = Json.encodeToString(request) + "\n"
        sendChannel.writeStringUtf8(jsonString)

        return try {
            withTimeout(timeout) {
                pendingResponse.await()
            }
        } catch (e: TimeoutCancellationException) {
            pendingRequests.remove(request.id)
            ServerResponse.Response(request.id, Status.ERROR ,"Request timed out.")
        }
    }

    fun completeResponse(response: ServerResponse) {
        pendingRequests[response.id]?.complete(response)
        pendingRequests.remove(response.id)
    }
}
