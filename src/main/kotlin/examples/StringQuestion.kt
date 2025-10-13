package nl.joozd.questionbus.examples

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import nl.joozd.questionbus.Question

internal data class StringQuestion(val question: String): Question



data class TypedClass<T : Any>(val data: String, private val deferred: CompletableDeferred<T>){
    fun answer(answer: T){
        deferred.complete(answer)
    }
}

val channel = Channel<TypedClass<out Any>>()

fun doSomething(){
    val coroutineContext = Dispatchers.Default + Job()
    val job = coroutineContext.job
    val deferred = CompletableDeferred<Int>(job)
    val x = TypedClass<Int>("foo", deferred)
    channel.trySend(x)
    runBlocking {
        println(deferred.await())
    }
}

fun doSomethingElse(ch: ReceiveChannel<TypedClass<Any>>) = runBlocking{
    val x = ch.receive()
    x.answer("foo")
}