package com.motodash.e2e.harness

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.util.concurrent.CopyOnWriteArrayList

class TestEventCollector<T>(flow: Flow<T>, scope: CoroutineScope) {
    private val values = CopyOnWriteArrayList<T>()
    private val job: Job = scope.launch {
        flow.collect { values.add(it) }
    }

    fun getValues(): List<T> = values.toList()
    fun latest(): T {
        assertTrue("Expected at least one collected value", values.isNotEmpty())
        return values.last()
    }

    fun assertCount(expected: Int) {
        assertEquals("Collector count mismatch", expected, values.size)
    }

    fun assertContains(predicate: (T) -> Boolean) {
        assertTrue("Predicate not satisfied in collected values", values.any(predicate))
    }

    fun stop() {
        job.cancel()
    }
}
