import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.4.20"
    id("maven-publish")
    id("org.jetbrains.dokka") version "2.2.0"
}

val versionName = "0.1.2-beta"
val groupID = "nl.joozd.questionbus"

group = groupID
version = versionName

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.slf4j:slf4j-api:2.0.20")

    testImplementation("ch.qos.logback:logback-classic:1.6.4")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")

    // Coroutines test utilities
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")

    // Optional, nice asserts
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")

    // Turbine for testing Flows
    testImplementation("app.cash.turbine:turbine:1.2.1")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.freeCompilerArgs.add("-Xcontext-parameters")
}

tasks.test {
    useJUnitPlatform()
}

val sourceJar = tasks.register<Jar>("sourceJar") {
    description = "Packages the main source files into a sources JAR."
    group = "build"

    archiveClassifier.set("sources")
    from(sourceSets.named("main").map { it.allSource })
}


/**
 * Dokka configuration.
 */
dokka {
    moduleName.set("QuestionBus")

    dokkaPublications.html {
        outputDirectory.set(layout.buildDirectory.dir("docs"))
    }

    dokkaSourceSets.main {
        includes.from("README.md")

        jdkVersion.set(21)

        sourceLink {
            localDirectory.set(file("src/main/kotlin"))
            remoteUrl("https://github.com/Joozd/questionbus/tree/master/src/main/kotlin")
            remoteLineSuffix.set("#L")
        }
    }
}

/**
 * Packages the generated Dokka HTML documentation.
 */
val dokkaHtmlJar = tasks.register<Jar>("dokkaHtmlJar") {
    description = "Packages the generated Dokka HTML documentation into a JAR."
    group = "documentation"

    dependsOn(tasks.named("dokkaGenerate"))
    archiveClassifier.set("html-docs")
    from(layout.buildDirectory.dir("docs"))
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            groupId = groupID
            artifactId = "questionbus"
            version = versionName

            artifact(sourceJar.get())
            artifact(dokkaHtmlJar.get())

            pom {
                name.set("QuestionBus")
                description.set("A bus for asking questions and getting answers")
                url.set("https://github.com/Joozd/questionbus")

                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                        distribution.set("repo")
                    }
                }
            }
        }
    }

    repositories {
        maven {
            name = "reposilite"

            url = uri(
                if (versionName.endsWith("-SNAPSHOT")) {
                    "https://repo.joozd.nl/snapshots"
                } else {
                    "https://repo.joozd.nl/releases"
                }
            )

            credentials {
                username = findProperty("repoUsername")?.toString()
                    ?: error("Missing Gradle property: repoUsername")
                password = findProperty("repoPassword")?.toString()
                    ?: error("Missing Gradle property: repoPassword")
            }
        }
    }
}