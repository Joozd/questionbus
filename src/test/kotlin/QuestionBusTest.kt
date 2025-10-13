package nl.joozd.questionbus

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

// --- Test helpers ---

// Access the private _channel so tests can act as the UI/receiver.
@Suppress("UNCHECKED_CAST")
private fun QuestionBus.reflectChannel(): ReceiveChannel<QuestionWithCallback<*>> {
    val f = QuestionBus::class.java.getDeclaredField("_channel")
    f.isAccessible = true
    return f.get(this) as Channel<QuestionWithCallback<*>>
}

private data class TestQuestion <T>(
    val answers: List<T>
) : Question

// --- Tests ---

class QuestionBusTest {

    @Test
    fun `answering completes deferred with value`() = runTest {
        val bus = QuestionBus()
        val ch = bus.reflectChannel()

        // Producer side asks a question (domain)
        val q = TestQuestion(answers = listOf("A", "B"))
        val deferred = bus.ask<String>(this, q)

        // Receiver side (UI) consumes and answers
        @Suppress("UNCHECKED_CAST")
        val qa = ch.receive() as QuestionWithCallback<String>
        qa.callback("B")

        assertEquals("B", deferred.await())
        bus.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `cancellation of parent job cancels deferred`() = runTest {
        val bus = QuestionBus()
        val ch = bus.reflectChannel()

        val parent = Job()
        val ctx = coroutineContext + parent

        val q = TestQuestion(answers = listOf(1, 2, 3))
        val deferred = bus.ask<String>(CoroutineScope(ctx),q)

        // Ensure the question was sent
        assertNotNull(ch.receive())

        // Cancel the parent -> should cancel the deferred
        parent.cancel()

        assertTrue(deferred.isCancelled)
        assertThrows<CancellationException> { deferred.getCompleted() }
        bus.close()
    }

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    @Test
    fun `closing bus prevents new asks`() = runTest {
        val bus = QuestionBus()

        bus.close()

        val q = TestQuestion(answers = listOf(true, false))

        val ex = assertThrows<ChannelClosedException> {
            bus.ask<String>(this, q)
        }
        println("APPLE")
        assertTrue(ex.message!!.contains("Channel Closed"))
        println("BANANA")
        assertEquals(q, ex.unSentItem)
        println("CHERRY")
    }

    @Test
    fun `multiple concurrent questions of different types`() = runTest {
        val bus = QuestionBus()
        val ch = bus.reflectChannel()

        val q1 = TestQuestion(answers = listOf("X", "Y"))
        val q2 = TestQuestion(answers = listOf(10, 20, 30))

        val d1 = bus.ask<String>(this,q1)
        val d2 = bus.ask<Int>(this,q2)

        // Receive in any order; answer accordingly
        repeat(2) {
            val next = ch.receive()
            when (val q = next.question) {
                is TestQuestion<*> -> {
                    when {
                        q == q1 -> {
                            @Suppress("UNCHECKED_CAST")
                            (next as QuestionWithCallback<String>).callback("Y")
                        }
                        q == q2 -> {
                            @Suppress("UNCHECKED_CAST")
                            (next as QuestionWithCallback<Int>).callback(30)
                        }
                    }
                }
            }
        }

        assertEquals("Y", d1.await())
        assertEquals(30, d2.await())
        bus.close()
    }

    @Test
    fun `pending question can still be answered after bus is closed`() = runTest {
        val bus = QuestionBus()
        val ch = bus.reflectChannel()

        val q = TestQuestion(answers = listOf("left", "right"))
        val d = bus.ask<String>(this,q)

        @Suppress("UNCHECKED_CAST")
        val qa = ch.receive() as QuestionWithCallback<String>

        // Close the bus BEFORE answering the pending question
        bus.close()

        // Pending question should still be answerable
        qa.callback("right")
        assertEquals("right", d.await())
    }
}
