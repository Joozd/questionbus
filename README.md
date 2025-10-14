Module questionbus

# QuestionBus

A lightweight, coroutine-friendly bridge for relaying **typed questions** from a producer (domain logic) to one or more consumers (typically UI) that provide **answers asynchronously**.

---

## Overview

`QuestionBus` decouples asking from answering:

- It exposes two roles:
    - **QuestionBusAskingSide** — producers call `ask(question)` and receive a `Deferred` that completes when answered.
    - **QuestionBusAnsweringSide** — consumers observe `questionsFlow: Flow<Set<Question<*>>>` and respond via `answerQuestion(question, answer)`.

- The bus maintains a **set of pending questions** and emits a fresh snapshot on each change. New collectors (e.g., after rotation) immediately receive the current set.

- Multiple consumers may answer; **first answer wins**. No ordering or single-consumer guarantee is provided.

---

## Concurrency & Lifecycle

- `ask()` and `answerQuestion()` are safe from multiple coroutines.
- The `Deferred` returned by `ask()` is **parented to the caller’s scope**; canceling the caller cancels that pending request and removes it from the set.
- Use `cancelAll()` to cancel and clear **all** outstanding questions (e.g., on shutdown).

---

## API (essentials)

- `questionsFlow: Flow<Set<Question<*>>>` — replayable stream of pending questions.
- `fun <T> ask(question: Question<T>): Deferred<T>` — registers a question and yields a `Deferred` for the answer.
- `fun <T> answerQuestion(question: Question<T>, answer: T)` — completes the matching deferred (no-op if already completed/canceled).
- `fun cancelAll()` — cancels all outstanding questions.

---

## Usage

**Producer (domain):**
```kotlin
sealed interface MyQuestion<T>: Question<T>
data object AgeQuestion: MyQuestion<Int>
data object AddressQuestion: MyQuestion<String>


val q1 = bus.ask(AgeQuestion)
val q2 = bus.ask(AddressQuestion)
val age = q1.await()
val address = q2.await()
bus.close() // this makes 
```

**Consumer (UI/ViewModel):**
```kotlin
questionsFlow.collect { set ->
  set.filterIsInstance<MyQuestion>().forEach { q ->
    bus.answerQuestion(q, computeAnswer(q))
  }
}
```

or even better
```kotlin
questionsFlow.collect { set ->
    set.filterIsInstance<MyQuestion>().forEach { q ->
        when(q){
            is AgeQuestion -> bus.answerQuestion(q, getAge())
            is AddressQuestion -> bus.answer(q, getAddress())
        }        
    }
}
```

---

## Rotation & Replay

Because `questionsFlow` replays the latest snapshot, a newly created UI (after activity recreation) immediately sees the current pending questions and can keep rendering/answering without extra wiring.

---

## Guidance

Define a sealed hierarchy for your domain questions for exhaustive handling:

```kotlin
sealed interface MyDomainQuestion<T> : Question<T>
```

---

## Examples

See the `example` package for a minimal producer/consumer setup.

---

## References

- Kotlin Flows: https://kotlinlang.org/docs/flow.html
- Coroutines `Deferred`: https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-deferred/

## Dependency
This uses Context Parameters, which at the moment of this writing are still in beta and need opt-in (alle code Gradle/Kotlin):
#### Kotlin/JVM
```kotlin
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.freeCompilerArgs.add("-Xcontext-parameters")
}
```
#### Kotlin/Multiplatform
```kotlin
kotlin {
    targets.all {
        compilations.all {
            compilerOptions.freeCompilerArgs.add("-Xcontext-parameters")
        }
    }
}
```
#### For multiple modules
in root `build.gradle.kts`
```kotlin
subprojects {
    plugins.withType<org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper> {
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
            compilerOptions.freeCompilerArgs.add("-Xcontext-parameters")
        }
    }
}
```


