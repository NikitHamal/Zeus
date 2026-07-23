package com.zeus.code.data

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class RetryInterceptor(private val maxRetries: Int = 3, private val initialBackoffMs: Long = 500) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        var lastException: IOException? = null
        for (attempt in 0..maxRetries) {
            try {
                val response = chain.proceed(originalRequest)
                if (response.isSuccessful || attempt == maxRetries) return response
                val shouldRetry = response.code in RETRYABLE_STATUS_CODES
                if (!shouldRetry) return response
                response.close()
                if (attempt < maxRetries) {
                    Thread.sleep(initialBackoffMs * (1L shl attempt))
                }
            } catch (e: SocketTimeoutException) {
                lastException = e
                if (attempt < maxRetries) Thread.sleep(initialBackoffMs * (1L shl attempt))
            } catch (e: UnknownHostException) {
                throw e
            } catch (e: IOException) {
                lastException = e
                if (attempt < maxRetries) Thread.sleep(initialBackoffMs * (1L shl attempt))
            }
        }
        throw lastException ?: IOException("Max retries ($maxRetries) exhausted")
    }

    companion object {
        private val RETRYABLE_STATUS_CODES = listOf(408, 429, 500, 502, 503, 504)
    }
}
