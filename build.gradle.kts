import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.2.20"
    id("maven-publish")
    id("org.jetbrains.dokka") version "2.0.0"
}

val versionName = "0.1.1-beta"
val groupID = "nl.joozd.questionbus"

group = groupID
version = versionName

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.slf4j:slf4j-api:2.0.7")
    testImplementation("ch.qos.logback:logback-classic:1.5.19")


    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")

    // Coroutines test utilities
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    // (optional, nice asserts)
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")

    // turbine for testing Flows
    testImplementation("app.cash.turbine:turbine:1.1.0")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

//val compileKotlin: KotlinCompile by tasks
//compileKotlin.compilerOptions {
//    freeCompilerArgs.set(listOf("-Xcontext-parameters"))
//}


tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.freeCompilerArgs.add("-Xcontext-parameters")
}

val sourceJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.named("main").map { it.allSource })
}

/**
 * Dokka v2 — central configuration
 */
dokka {
    // Shown in the docs header
    moduleName.set("QuestionBus")

    // HTML output (your previous build/docs location)
    dokkaPublications.html {
        outputDirectory.set(layout.buildDirectory.dir("docs"))
        // Optional: fail the build on Dokka warnings
        // failOnWarning.set(true)
        // Optional: suppress inherited members, etc.
        // suppressInheritedMembers.set(true)
    }

    // Also emit Javadoc-format output when the javadoc plugin is applied
    dokkaPublications.findByName("javadoc")?.apply {
        outputDirectory.set(layout.buildDirectory.dir("javadoc"))
    }

    dokkaSourceSets.main {
        includes.from("README.md")

        // JDK target
        jdkVersion.set(21)

        // Source links (use helper that wraps URI in v2)
        sourceLink {
            localDirectory.set(file("src/main/kotlin"))
            remoteUrl("https://github.com/Joozd/questionbus/tree/master/src/main/kotlin")
            remoteLineSuffix.set("#L")
        }

        // Typical v2 tweaks you might want:
        // reportUndocumented.set(true)
        // documentedVisibilities(VisibilityModifier.Public)
    }

    // Example for custom assets/styles in v2 (type-safe)
    // pluginsConfiguration.html {
    //     customAssets.from("docs/logo.png")
    //     customStyleSheets.from("docs/styles.css")
    // }
}

/**
 * Package Dokka outputs as jars (v2)
 * The single `dokkaGenerate` task produces the configured publications.
 */
val dokkaGenerate = tasks.named("dokkaGenerate")

val dokkaHtmlJar by tasks.registering(Jar::class) {
    dependsOn(dokkaGenerate)
    archiveClassifier.set("html-docs")
    from(layout.buildDirectory.dir("docs"))
}

val dokkaJavadocJar by tasks.registering(Jar::class) {
    // Only meaningful if org.jetbrains.dokka-javadoc is applied
    dependsOn(dokkaGenerate)
    archiveClassifier.set("javadoc")
    from(layout.buildDirectory.dir("javadoc"))
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
            // Attach Javadoc JAR only if present (plugin applied)
            if (plugins.hasPlugin("org.jetbrains.dokka-javadoc")) {
                artifact(dokkaJavadocJar.get())
            }

            pom {
                name.set("QuestionBus")
                description.set("A Bus for aswking questions and getting answers")
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
            url = uri("https://joozd.nl/nexus/repository/maven-releases/")
            credentials {
                username = (findProperty("nexusUsername") ?: System.getenv("NEXUS_USERNAME") ?: "").toString()
                password = (findProperty("nexusPassword") ?: System.getenv("NEXUS_PASSWORD") ?: "").toString()
            }
        }
    }
}