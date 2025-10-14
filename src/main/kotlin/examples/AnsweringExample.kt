package nl.joozd.questionbus.examples

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import nl.joozd.questionbus.QuestionBusAnsweringSide
import org.slf4j.LoggerFactory


private val logger = LoggerFactory.getLogger("Answer")

internal fun CoroutineScope.answerExample(qb: QuestionBusAnsweringSide) {
    val questionsFlow = qb.questionsFlow
    launch {
        questionsFlow.collect { qSet ->
            val q = qSet.firstOrNull() ?: return@collect
            require(q is ExampleQuestion) { "Only ExampleQuestions must be asked!" }
            when(q){
                is QuestionOfLifeUniverseAndEverything -> {
                    logger.info("Answering QuestionOfLifeUniverseAndEverything")
                    qb.answerQuestion(q,42)
                }
                is WhoIsBallerinaCappuccinasLover -> {
                    logger.info("Answering WhoIsBallerinaCappuccinasLover")
                    qb.answerQuestion(q, "Tung Tung Tung Sahur!")
                }
            }
        }
        logger.debug("No longer collecting!")
    }
}