package nl.joozd.questionbus.examples

import nl.joozd.questionbus.Question

internal sealed interface ExampleQuestion<T>: Question<T>

internal data object QuestionOfLifeUniverseAndEverything: ExampleQuestion<Int>

internal data object WhoIsBallerinaCappuccinasLover: ExampleQuestion<String>