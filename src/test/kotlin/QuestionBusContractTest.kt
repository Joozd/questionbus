@file:OptIn(ExperimentalCoroutinesApi::class)

package nl.joozd.questionbus

import app.cash.turbine.test
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.test.assertFails

/**
 * Minimal domain question used in tests.
 * Replace with your real `Question<T>` if needed.
 */
private data class TestQuestion<T>(val id: String) : Question<T>

/**
 * Contract tests for QuestionBus interfaces.
 * Focuses on observable behavior; does not rely on implementation details.
 */
class QuestionBusContractTest {

    private val testDispatcher = StandardTestDispatcher()

    /** Asking a single question emits it in questionsFlow and answering completes the Deferred. */
    @Test
    fun `ask then answer - single question`() = runTest(testDispatcher) {
        val bus = QuestionBus()
        val scope = backgroundScope

        val q = TestQuestion<Int>("q1")
        val d = with(scope) { bus.ask(q) }

        // questionsFlow includes q
        val pending = bus.questionsFlow.first()
        assertTrue(q in pending, "Pending should contain the asked question")

        // answer completes
        bus.answerQuestion(q, 42)
        assertEquals(42, d.await())
        // after completion, question should be removed
        val after = bus.questionsFlow.first()
        assertTrue(q !in after)
    }

    /** Multiple questions can be asked and answered one-by-one. */
    @Test
    fun `ask multiple - answer one-by-one in order`() = runTest(testDispatcher) {
        val bus = QuestionBus()
        val scope = backgroundScope

        val q1 = TestQuestion<String>("q1")
        val q2 = TestQuestion<Int>("q2")
        val d1 = with(scope) { bus.ask(q1) }
        val d2 = with(scope) { bus.ask(q2) }

        // Both present
        assertEquals(setOf(q1, q2), bus.questionsFlow.first())

        // Answer first, then second
        bus.answerQuestion(q1, "A")
        assertEquals("A", d1.await())
        assertEquals(setOf(q2), bus.questionsFlow.first())

        bus.answerQuestion(q2, 7)
        assertEquals(7, d2.await())
        assertEquals(emptySet<Question<*>>(), bus.questionsFlow.first())
    }

    /** Answers arriving in different order than the questions are supported. */
    @Test
    fun `answers can arrive out of order`() = runTest(testDispatcher) {
        val bus = QuestionBus()
        val scope = backgroundScope

        val q1 = TestQuestion<Int>("q1")
        val q2 = TestQuestion<Int>("q2")
        val d1 = with(scope) { bus.ask(q1) }
        val d2 = with(scope) { bus.ask(q2) }

        bus.answerQuestion(q2, 200)
        bus.answerQuestion(q1, 100)

        assertEquals(200, d2.await())
        assertEquals(100, d1.await())
        assertEquals(emptySet<Question<*>>(), bus.questionsFlow.first())
    }

    /** Cancellation of the caller scope cancels the returned Deferred and removes the question. */
    @Test
    fun `cancelling caller cancels deferred and removes question`() = runTest(testDispatcher) {
        val bus = QuestionBus()
        val callerScope = CoroutineScope(SupervisorJob() + testDispatcher)

        val q = TestQuestion<Unit>("q")
        val d = with(callerScope) { bus.ask(q) }

        // Ensure pending contains q
        assertTrue(q in bus.questionsFlow.first())

        // Cancel the caller -> deferred should cancel and pending should clear
        callerScope.cancel()

        assertTrue(d.isCancelled, "Deferred should be cancelled when caller scope is cancelled")

        // Advance to process completion callbacks
        testScheduler.advanceUntilIdle()
        assertTrue(q !in bus.questionsFlow.first())
    }

    /** A few questions asked first, then a few answers; flow snapshots should reflect transitions. */
    @Test
    fun `few questions then few answers - snapshots reflect transitions`() = runTest(testDispatcher) {
        val bus = QuestionBus()
        val scope = backgroundScope

        val q1 = TestQuestion<String>("q1")
        val q2 = TestQuestion<String>("q2")
        val q3 = TestQuestion<String>("q3")

        val d1 = with(scope) { bus.ask(q1) }
        val d2 = with(scope) { bus.ask(q2) }
        val d3 = with(scope) { bus.ask(q3) }

        // All present
        assertEquals(setOf(q1, q2, q3), bus.questionsFlow.first())

        // Answer two
        bus.answerQuestion(q2, "B")
        bus.answerQuestion(q3, "C")
        assertEquals("B", d2.await())
        assertEquals("C", d3.await())
        assertEquals(setOf(q1), bus.questionsFlow.first())

        // Answer the last
        bus.answerQuestion(q1, "A")
        assertEquals("A", d1.await())
        assertEquals(emptySet<Question<*>>(), bus.questionsFlow.first())
    }

    /** First answer wins when multiple consumers race to answer the same question. */
    @Test
    fun `first answer wins under racing consumers`() = runTest(testDispatcher) {
        val bus = QuestionBus()
        val scope = backgroundScope
        val q = TestQuestion<Int>("q")
        val d = with(scope) { bus.ask(q) }

        val job1 = launch { bus.answerQuestion(q, 1) }
        val job2 = launch { bus.answerQuestion(q, 2) }
        joinAll(job1, job2)

        val v = d.await()
        assertTrue(v == 1 || v == 2, "First completion wins; got $v")
        assertEquals(emptySet<Question<*>>(), bus.questionsFlow.first())
    }

    /** questionsFlow is replayable: collector sees the current pending set on subscription (rotation). */
    @Test
    fun `questionsFlow replays latest snapshot for new collectors`() = runTest(testDispatcher) {
        val bus = QuestionBus()
        val scope = backgroundScope

        val q = TestQuestion<String>("q")
        with(scope) { bus.ask(q) }

        // New collector should immediately see q
        bus.questionsFlow.take(1).test {
            assertEquals(setOf(q), awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    /** Closing the bus cancels all pending questions and prevents new asks. */
    @Test
    fun `close cancels pending and prevents new asks`() = runTest(testDispatcher) {
        val bus = QuestionBus()
        val scope = backgroundScope

        val q = TestQuestion<Int>("q")
        val d = with(scope) { bus.ask(q) }

        bus.close()

        // Pending cancelled
        assertTrue(d.isCancelled)

        // New ask must fail with some "closed" exception per contract
        assertFails { with(scope) { bus.ask(TestQuestion<Int>("q2")) } }
    }

    /** questionsFlow terminates for collectors when the bus is closed (cancellation). */
    @Test
    fun `questionsFlow cancels collectors when closed`() = runTest(testDispatcher) {
        val bus = QuestionBus()

        val t = launch {
            bus.questionsFlow.test {
                // Initial snapshot
                awaitItem()
                // Close -> CancellationException observed by Turbine
                bus.close()
                val err = awaitError()
                assertTrue(err is CancellationException, "Flow should cancel on close")
            }
        }
        t.join()
    }

    /** cancelAll cancels each outstanding question and clears the set without closing the bus. */
    @Test
    fun `cancelAll cancels outstanding without closing bus`() = runTest(testDispatcher) {
        val bus = QuestionBus()
        val scope = backgroundScope

        val q1 = TestQuestion<Unit>("q1")
        val q2 = TestQuestion<Unit>("q2")
        val d1 = with(scope) { bus.ask(q1) }
        val d2 = with(scope) { bus.ask(q2) }

        bus.cancelAll()
        assertTrue(d1.isCancelled && d2.isCancelled)
        assertEquals(emptySet<Question<*>>(), bus.questionsFlow.first())

        // Still can ask after cancelAll
        val q3 = TestQuestion<Int>("q3")
        val d3 = with(scope) { bus.ask(q3) }
        bus.answerQuestion(q3, 99)
        assertEquals(99, d3.await())
    }
}
