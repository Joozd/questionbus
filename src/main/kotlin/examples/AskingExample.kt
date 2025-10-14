package nl.joozd.questionbus.examples

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import nl.joozd.questionbus.QuestionBus
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Ask")

internal fun CoroutineScope.askExample(questionBus: QuestionBus): Job {
    return launch {
        val question1 = QuestionOfLifeUniverseAndEverything
        val question2 = WhoIsBallerinaCappuccinasLover
        val answer1 = questionBus.ask(question1)
        val answer2 = questionBus.ask(question2)

        logger.info("The answer to \"$question1}\" is ${answer1.await()}")
        logger.info("The answer to \"$question2}\" is \"${answer2.await()}\"")

        questionBus.close()
    }
}