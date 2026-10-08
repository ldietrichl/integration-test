@file:Suppress("UNCHECKED_CAST")

import org.gradle.api.tasks.testing.Test
import java.io.File

@Suppress("UNCHECKED_CAST")
val buildContext = extra["platformBuildContext"] as MutableMap<String, Any?>

val sourceSets = extensions.getByType<SourceSetContainer>()
val splitterConfigLoadMode get() = buildContext["splitterConfigLoadMode"] as String
val splitterTestProfile get() = buildContext["splitterTestProfile"] as String
val activeTestEnv get() = buildContext["activeTestEnv"] as String
val splitterConfigKafkaStatusRequired get() = buildContext["splitterConfigKafkaStatusRequired"] as String
val allureResultsDirectory get() = buildContext["allureResultsDirectory"] as File
val splitterRuntimeSystemProperties get() = buildContext["splitterRuntimeSystemProperties"] as List<String>
fun configuredRuntimeSystemProperty(name: String): String? = (buildContext["configuredRuntimeSystemProperty"] as (String) -> String?)(name)
fun normalizedFileEnvironment(): String = (buildContext["normalizedFileEnvironment"] as () -> String)()
val bypassTests get() = buildContext["bypassTests"] as org.gradle.api.tasks.TaskProvider<Test>


tasks {
    // Для компиляции ставим кодировку UTF-8
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
    withType<org.gradle.api.tasks.javadoc.Javadoc>().configureEach {
        options.encoding = "UTF-8"
        (options as org.gradle.external.javadoc.StandardJavadocDocletOptions).apply {
            charSet = "UTF-8"
            docEncoding = "UTF-8"
        }
    }
    // Для тестов внутри проекта используем платформу JUnit
    withType<Test> {
        useJUnitPlatform()
        systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")
        systemProperty("file.encoding", "UTF-8")
        systemProperty("splitter.config.load.mode", splitterConfigLoadMode)
        systemProperty("splitter.test.profile", splitterTestProfile)
        systemProperty("splitter.config.kafka.status.required", splitterConfigKafkaStatusRequired)
        systemProperty("env", activeTestEnv)
        systemProperty("allure.results.directory", allureResultsDirectory.absolutePath)
        systemProperty("report.outdated.tests.file", file("config/reporting/outdated-tests.properties").absolutePath)
        // Only tasks that generate an eligibility snapshot may configure its path.
        // Unit/stand tasks must also start immediately after clean.
        systemProperty("report.exclusions.file", "")
        testLogging {
            showStandardStreams = true
            events("PASSED", "SKIPPED", "FAILED", "STANDARD_OUT", "STANDARD_ERROR")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showCauses = true
            showExceptions = true
            showStackTraces = true
        }
        val forwardedTestSystemProperties = setOf(
            "env",
            "allure.testStage",
            "testStage",
            "EXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED",
            "exlab2696.running.cache.wait.timeout.ms",
            "exlab2696.running.cache.wait.poll.ms",
            "exlab2930.processor.timeout.seconds",
            "exlab2930.processor.stability.seconds"
        )
        System.getProperties().stringPropertyNames()
            .filter {
                it in forwardedTestSystemProperties ||
                        it.startsWith("splitter.config.kafka.") ||
                        it.startsWith("splitter.config.load.") ||
                        it.startsWith("splitter.config.load.monitoring.") ||
                        it.startsWith("splitter.endpoint.") ||
                        it.startsWith("splitter.local.") ||
                        it.startsWith("splitter.mapper.endpoint.") ||
                        it.startsWith("splitter.precalc.monitoring.") ||
                        it.startsWith("splitter.kap.") ||
                        it.startsWith("splitter.reactions.endpoint.")
            }
            .forEach { systemProperty(it, System.getProperty(it)) }
        splitterRuntimeSystemProperties.forEach { propertyName ->
            configuredRuntimeSystemProperty(propertyName)?.let { systemProperty(propertyName, it) }
        }
        doFirst {
            if (name != "bypassTests") systemProperty("property.layout.test.classpath", sourceSets.getByName("test").runtimeClasspath.asPath)
            systemProperty("env", normalizedFileEnvironment())
            environment("ENV", normalizedFileEnvironment())
        }
        if (name != "bypassTests") finalizedBy("copyAllureCategories")
    }
}
