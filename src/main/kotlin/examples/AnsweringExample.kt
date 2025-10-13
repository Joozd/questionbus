package nl.joozd.questionbus.examples

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.runBlocking
import nl.joozd.questionbus.AnswerableQuestion
import org.slf4j.LoggerFactory


private val logger = LoggerFactory.getLogger("Answer")

// this runs blocking
internal fun answer(questionsChannel: ReceiveChannel<AnswerableQuestion<*>>) = runBlocking {
    val questionsFlow = questionsChannel.consumeAsFlow()
    questionsFlow.collect{ q ->
        val question = q.question as? StringQuestion ?: throw IllegalStateException("I don't know how to answer a question of type ${q::class.simpleName}")
        logger.info("Ooh I got a question! It's ${question.question}.. Let me think on it a bit")
        delay(2000)
        logger.info("Got it! Answering now!")
        q.answer(42)
    }
}