package com.example.privateaiservice.service

import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

class RateLimitService(
    private val maxRequests: Int,
    private val windowMs: Long
) {
    private val buckets = ConcurrentHashMap<String, ArrayDeque<Long>>()

    fun check(key: String) {
        val now = System.currentTimeMillis()
        val bucket = buckets.computeIfAbsent(key) { ArrayDeque() }
        synchronized(bucket) {
            while (bucket.isNotEmpty() && now - bucket.first() >= windowMs) {
                bucket.removeFirst()
            }
            if (bucket.size >= maxRequests) {
                throw RateLimitException()
            }
            bucket.addLast(now)
        }
    }
}
