package nl.joozd.questionbus

import kotlinx.coroutines.flow.Flow
import java.io.Closeable

/**
 * Interface for the **answering** side of a QuestionBus.
 *
 * A consumer observes [questionsFlow] to see the current set of **pending** questions and
 * completes them by calling [answerQuestion]. This side is [Closeable]: closing signals that
 * **no more answers** will be provided; new asks fail and existing pending requests are canceled.
 */
interface QuestionBusAnsweringSide : Closeable {

    /**
     * Replayable stream of the **current pending** questions.
     *
     * Emits a fresh snapshot on each change and replays the latest to new collectors
     * (e.g., after activity recreation). Collectors may throw if the bus is closed.
     */
    val questionsFlow: Flow<Set<Question<*>>>

    /**
     * Complete the pending answer for [question] with [answer].
     *
     * If the question is unknown, already completed, or canceled, this is a no-op.
     * When multiple consumers race to answer, the **first** completion wins.
     */
    fun <T> answerQuestion(question: Question<T>, answer: T)
}
