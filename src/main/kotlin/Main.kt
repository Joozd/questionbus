package nl.joozd.questionbus

import kotlinx.coroutines.runBlocking
import nl.joozd.questionbus.examples.answerExample
import nl.joozd.questionbus.examples.askExample
import org.slf4j.LoggerFactory


private val logger = LoggerFactory.getLogger("Main")
//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() {
    val qb = QuestionBus()
    runBlocking {
        logger.info("Creating Asking job...")
        val askJob = askExample(qb)
        logger.info("starting Answering function...")
        answerExample(qb)
        askJob.join()
    }
}