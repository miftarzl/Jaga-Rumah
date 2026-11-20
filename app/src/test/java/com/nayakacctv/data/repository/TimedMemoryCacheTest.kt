package com.nayakacctv.data.repository

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimedMemoryCacheTest {
    @Test
    fun returnsCachedValueUntilTtlExpires() = runBlocking {
        var now = 0L
        var loadCount = 0
        val cache = TimedMemoryCache<String>(ttlMillis = 100, nowMillis = { now })
        val loader = {
            loadCount++
            Result.success("value-$loadCount")
        }

        assertEquals("value-1", cache.getOrLoad(loader = loader).getOrThrow())
        assertEquals("value-1", cache.getOrLoad(loader = loader).getOrThrow())
        now = 100
        assertEquals("value-2", cache.getOrLoad(loader = loader).getOrThrow())
        assertEquals(2, loadCount)
    }

    @Test
    fun doesNotCacheFailuresAndSupportsForcedRefresh() = runBlocking {
        var loadCount = 0
        val cache = TimedMemoryCache<String>(ttlMillis = 100)

        val failure = cache.getOrLoad {
            loadCount++
            Result.failure(IllegalStateException("offline"))
        }
        assertTrue(failure.isFailure)

        assertEquals("fresh", cache.getOrLoad {
            loadCount++
            Result.success("fresh")
        }.getOrThrow())
        assertEquals("updated", cache.getOrLoad(forceRefresh = true) {
            loadCount++
            Result.success("updated")
        }.getOrThrow())
        assertEquals(3, loadCount)
    }

    @Test
    fun concurrentLoadsForSameKeyShareOneRequest() = runBlocking {
        var loadCount = 0
        val cache = TimedMemoryCache<String>(ttlMillis = 100)

        coroutineScope {
            val first = async {
                cache.getOrLoad {
                    loadCount++
                    delay(20)
                    Result.success("shared")
                }
            }
            val second = async {
                cache.getOrLoad {
                    loadCount++
                    Result.success("unexpected")
                }
            }

            assertEquals("shared", first.await().getOrThrow())
            assertEquals("shared", second.await().getOrThrow())
        }

        assertEquals(1, loadCount)
    }

    @Test
    fun clearForcesNextCallToLoadAgain() = runBlocking {
        var loadCount = 0
        val cache = TimedMemoryCache<String>(ttlMillis = 100)
        val loader = {
            loadCount++
            Result.success("value-$loadCount")
        }

        cache.getOrLoad(loader = loader)
        cache.clear()

        assertEquals("value-2", cache.getOrLoad(loader = loader).getOrThrow())
        assertEquals(2, loadCount)
    }
}
