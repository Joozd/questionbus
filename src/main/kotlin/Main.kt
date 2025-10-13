package nl.joozd.questionbus

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import nl.joozd.questionbus.examples.answer
import nl.joozd.questionbus.examples.askingChannel
import org.slf4j.LoggerFactory


private val logger = LoggerFactory.getLogger("Main")
//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() {
    runBlocking {
        val scope: CoroutineScope = this
        logger.info("Creating Asking Channel")
        val receiveChannel = askingChannel(this)
        logger.info("starting Answering function...")
        answer(receiveChannel.receiveChannel)
    }
}