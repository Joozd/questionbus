package nl.joozd.questionbus

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import java.io.Closeable

/**
 * Interface for the **asking** side of a QuestionBus.
 *
 * A producer registers questions and receives a [Deferred] that completes when any consumer
 * provides an answer. This side is [Closeable]: closing signals that **no new questions**
 * will be asked and cancels all outstanding requests.
 */
interface QuestionBusAskingSide : Closeable {

    /**
     * Ask a question. Returns a [Deferred] that completes with the answer.
     *
     * The returned [Deferred] is parented to the caller’s [CoroutineScope] (via the context receiver).
     * If the bus has been closed, this throws a *closed-bus* exception.
     */
    context(scope: CoroutineScope)
    fun <T> ask(question: Question<T>): Deferred<T>

    /** Cancels **all** outstanding questions registered by this side. */
    fun cancelAll()
}