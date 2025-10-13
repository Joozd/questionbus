package nl.joozd.questionbus.examples

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import nl.joozd.questionbus.QuestionBus
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Ask")

internal fun askingChannel(coroutineScope: CoroutineScope): QuestionBus {
    val questionBus = QuestionBus()
    val question = StringQuestion("What is the answer to the question of Life, the Universe and everything?")
    coroutineScope.launch {
        logger.info("Asking question...")
        val answer = questionBus.ask<Int>(coroutineScope, question)
        logger.info("The answer is ${answer.await()}")
        questionBus.close() // close the channel so any collectors terminate
    }
    return questionBus
}