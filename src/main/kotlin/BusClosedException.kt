package nl.joozd.questionbus

class BusClosedException(
    val unSentItem: Question<*>? = null,
    cause: Throwable? = null
): IllegalStateException("Channel Closed, could not send $unSentItem", cause)