package nl.joozd.questionbus

data class QuestionWithCallback<T> (
    val question: Question,
    val callback: (T) -> Unit
)