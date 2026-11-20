package com.nayakacctv.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class TimedMemoryCache<T : Any>(
    private val ttlMillis: Long,
    private val maxEntries: Int = 20,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    private data class Entry<T>(
        val value: T,
        val cachedAtMillis: Long
    )

    private val entries = LinkedHashMap<String, Entry<T>>(maxEntries, 0.75f, true)
    private val mutex = Mutex()
    private var generation = 0L

    init {
        require(ttlMillis > 0) { "Cache TTL must be positive" }
        require(maxEntries > 0) { "Cache capacity must be positive" }
    }

    suspend fun getOrLoad(
        key: String = "default",
        forceRefresh: Boolean = false,
        loader: suspend () -> Result<T>
    ): Result<T> = mutex.withLock {
        val now = nowMillis()
        val (cachedValue, loadGeneration) = synchronized(entries) {
            val currentGeneration = generation
            val entry = entries[key]
            val cached = entry?.takeIf { now - it.cachedAtMillis < ttlMillis }?.value
            if (entry != null && cached == null) {
                entries.remove(key)
            }
            cached to currentGeneration
        }

        if (!forceRefresh && cachedValue != null) {
            return@withLock Result.success(cachedValue)
        }

        loader().onSuccess { value ->
            synchronized(entries) {
                if (generation == loadGeneration) {
                    entries[key] = Entry(value, nowMillis())
                    while (entries.size > maxEntries) {
                        val oldestKey = entries.entries.iterator().next().key
                        entries.remove(oldestKey)
                    }
                }
            }
        }
    }

    fun clear() {
        synchronized(entries) {
            generation++
            entries.clear()
        }
    }
}
