package nl.joozd.questionbus

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * A lightweight bridge for relaying **typed** questions from producers (domain)
 * to one or more consumers (typically UI) that provide answers asynchronously.
 *
 * ## Overview
 * - Maintains a set of **pending** questions and exposes it via [questionsFlow].
 *   New collectors immediately receive the current snapshot (rotation-friendly).
 * - Producers call [ask] to register a question and receive a [Deferred] answer.
 * - Multiple consumers may answer; **first answer wins**. No ordering guarantees.
 *
 * ## Concurrency & lifecycle
 * - [ask] and [answerQuestion] are safe from multiple coroutines.
 * - The returned [Deferred] is parented to the caller’s scope; canceling the caller
 *   cancels that request and removes it from the pending set.
 * - [close] cancels all pending questions and marks the bus closed. Subsequent [ask]
 *   calls fail with a closed-bus exception.
 *
 * ## UI integration
 * - Treat [questionsFlow] as state: render the full set, respond to updates, and call
 *   [answerQuestion] when appropriate.
 */
class QuestionBus():  QuestionBusAskingSide, QuestionBusAnsweringSide {
    // private val logger = LoggerFactory.getLogger(this::class.simpleName) // not logging anything atm
    private val isClosedFlow = MutableStateFlow(false)
    private var isClosed get() = isClosedFlow.value
        set(isClosed) { isClosedFlow.update { isClosed }}

    // if we can drop deferred's, keep track of them
    private val pendingDeferredMapFlow = MutableStateFlow<Map<Question<*>, CompletableDeferred<*>>>(emptyMap())
    private var pendingDeferredMap
        get() = pendingDeferredMapFlow.value
        set(newMap){ pendingDeferredMapFlow.update { newMap } }

    private val _questionsFlow: Flow<Set<Question<*>>> = pendingDeferredMapFlow.map { it.keys.toSet() }

    /**
     * Stream of the current **pending** questions.
     * Emits a fresh snapshot on each change and replays the latest to new collectors.
     * When the bus is closed, the flow throws a [CancellationException].
     */
    override val questionsFlow: Flow<Set<Question<*>>> = combine(isClosedFlow, _questionsFlow){
        isClosed, questions ->
        if(isClosed) throw CancellationException("QuestionBus closed") // this cancels the Flow
        questions
    }

    /**
     * Complete the pending answer for [question] with [answer].
     *
     * No-op if [question] is not pending or already completed/canceled.
     * If multiple consumers race, the first completion wins.
     */
    override fun <T> answerQuestion(question: Question<T>, answer: T) {
        @Suppress("UNCHECKED_CAST") // answer type must be the same as question type and therefore CompletableDeferred type
        (pendingDeferredMap[question] as? CompletableDeferred<T>)?.complete(answer)
    }

    /**
     * Register (ask) a new [question] and obtain a [Deferred] that completes when it is answered.
     *
     * The returned [Deferred] is parented to the caller’s [CoroutineScope] (via the context receiver);
     * canceling that scope cancels the request and removes it from the pending set.
     *
     * @return a [Deferred] that completes with the answer provided by any consumer.
     * @throws BusClosedException (or a closed-bus specific exception) if the bus is closed.
     */
    context(scope: CoroutineScope)
    override fun <T> ask(question: Question<T>): Deferred<T> =
        if(isClosed) throw BusClosedException(question)
        else
            CompletableDeferred<T>(scope.coroutineContext[Job])
                .apply { invokeOnCompletion { pendingDeferredMap -= question } }
                .also { pendingDeferredMap += question to it }


    /**
     * Cancels **all** outstanding questions and clears the pending set.
     */
    override fun cancelAll(){
        val deferreds = pendingDeferredMap.values
        pendingDeferredMap = emptyMap()
        deferreds.forEach { it.cancel() }

    }

    /**
     * Closes the bus: cancels all pending questions and prevents further asks.
     * Collectors of [questionsFlow] will observe termination.
     */
    override fun close(){
        cancelAll()
        isClosed = true
    }
}