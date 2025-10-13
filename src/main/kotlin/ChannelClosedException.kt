package nl.joozd.questionbus

class ChannelClosedException(
    val unSentItem: Question,
    cause: Throwable? = null
): IllegalStateException("Channel Closed, could not send $unSentItem", cause)