package com.bifrostorbit.cms

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class RawResponse(val code: Int, val retryAfter: String?, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299
}

// [EXPLAIN-D248]
internal suspend fun Call.awaitRaw(): RawResponse = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            val raw = try {
                response.use { RawResponse(it.code, it.header("Retry-After"), it.body.string()) }
            } catch (error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
                return
            }
            if (continuation.isActive) continuation.resume(raw)
        }
    })
}
