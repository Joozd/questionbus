package nl.joozd.questionbus

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.onFailure
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

/**
 * A lightweight bridge for relaying typed questions from a producer (typically domain logic)
 * to a consumer (typically UI) that can provide answers asynchronously.
 *
 * ## Overview
 * - A `QuestionBus` exposes a single [Channel] of [QuestionWithCallback] objects.
 * - Producers call [ask] to enqueue a question and obtain a [Deferred] that completes
 *   when the question is answered through its callback.
 * - Each question is sent exactly once; any persistence or replay behaviour must be
 *   implemented by the receiver.
 * - The bus can be [close]d to signal that no further questions will be sent.
 *
 * ## Concurrency & Lifecycle
 * - [ask] is safe to call from multiple coroutines.
 * - The returned [Deferred] is parented to the caller's coroutine context, so
 *   cancellation of the caller propagates to the waiting question.
 * - Once [close] is called, subsequent [ask] calls will fail with a closed-channel exception,
 *   and all outstanding [Deferred] will be canceled.
 *
 * Typical usage:
 * ```
 * val bus = QuestionBus()
 * val deferred = coroutineContext.ask(MyQuestion(...))
 * // in UI layer: collect [receiveChannel], display question, invoke callback with answer
 * val result = deferred.await()
 * ```
 */
class QuestionBus(
    capacity: Int = Channel.BUFFERED,
    private val onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND
) : Closeable {
    private val canDropItems = capacity !in listOf(Channel.BUFFERED, Channel.RENDEZVOUS)
    // if we can drop deferred's, keep track of them
    private val pendingDeferred =
        if (canDropItems) ConcurrentHashMap<Question, Deferred<*>>() else null

    private val _channel = Channel<QuestionWithCallback<*>>(
        capacity = capacity,
        onBufferOverflow = onBufferOverflow,
        onUndeliveredElement = { e -> pendingDeferred?.let { it[e.question]?.cancel() } }
    ).apply{
        invokeOnClose {
            // close all outstanding deferred's on close
            pendingDeferred?.values?.forEach{
                it.cancel()
            }
        }
    }

    /**
     * Observe this to receive Questions
     */
    val receiveChannel: ReceiveChannel<QuestionWithCallback<*>> = _channel

    /**
     * Ask a question through this bus. The question expects an answer of type [T]
     */
    fun <T> ask(coroutineScope: CoroutineScope, question: Question): Deferred<T> {
        val deferred = CompletableDeferred<T>(coroutineScope.coroutineContext[Job])
        pendingDeferred?.let{ it[question] = deferred }

        val qWithCallback = QuestionWithCallback<T>(question) { answer ->
            deferred.complete(answer)
            pendingDeferred?.remove(question)
        }

        _channel.trySend(qWithCallback)
            .onFailure { cause ->
                deferred.cancel()
                throw ChannelClosedException(question, cause)
            }

        return deferred
    }

    override fun close() { _channel.close() }
}