@file:Suppress("UNCHECKED_CAST")

import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestOutputListener
import org.gradle.api.tasks.testing.TestResult
import java.io.File

@Suppress("UNCHECKED_CAST")
val buildContext = extra["platformBuildContext"] as MutableMap<String, Any?>

val sourceSets = extensions.getByType<SourceSetContainer>()
val splitterRegressionLogsDir get() = buildContext["splitterRegressionLogsDir"] as org.gradle.api.provider.Provider<File>
fun configuredRuntimeSystemProperty(name: String): String? = (buildContext["configuredRuntimeSystemProperty"] as (String) -> String?)(name)
fun splitterRegressionLogNow(): String = (buildContext["splitterRegressionLogNow"] as () -> String)()
fun throwableStackTrace(throwable: Throwable): String = (buildContext["throwableStackTrace"] as (Throwable) -> String)(throwable)
fun splitterConfigLoadModeNotice(mode: String): String = (buildContext["splitterConfigLoadModeNotice"] as (String) -> String)(mode)
fun appendSplitterRegressionLog(file: File, text: String): Unit = (buildContext["appendSplitterRegressionLog"] as (File, String) -> Unit)(file, text)
fun splitterRunLogHeader(mode: String, taskPath: String, logFile: File, runtimeProperties: Map<String, *> = emptyMap<String, Any>()): String = (buildContext["splitterRunLogHeader"] as (String, String, File, Map<String, *>) -> String)(mode, taskPath, logFile, runtimeProperties)
fun Test.applyReportEligibilityExclusions() =
    (buildContext["applyReportEligibilityExclusions"] as (Test) -> Unit)(this)

fun Test.configureSplitterDiagnosticTask(
    mode: String,
    includePattern: String,
    logFileName: String
) {
    val runLogFileProvider = splitterRegressionLogsDir.map { File(it, logFileName) }

    group = "verification"
    dependsOn(tasks.named("testClasses"))
    outputs.file(runLogFileProvider)
    outputs.upToDateWhen { false }
    testClassesDirs = sourceSets.getByName("test").output.classesDirs
    classpath = sourceSets.getByName("test").runtimeClasspath
    applyReportEligibilityExclusions()
    systemProperty("splitter.config.load.mode", mode)
    systemProperty("splitter.regression.logs.dir", splitterRegressionLogsDir.get().absolutePath)
    systemProperty(
        "secure.placeholders.fail-on-unresolved",
        configuredRuntimeSystemProperty("secure.placeholders.fail-on-unresolved") ?: "false"
    )
    filter.includeTestsMatching(includePattern)
    doFirst("startSplitterRegressionTaskLog") {
        val logFile = runLogFileProvider.get()
        logFile.parentFile.mkdirs()
        logFile.writeText(splitterRunLogHeader(mode, path, logFile, systemProperties), Charsets.UTF_8)
        logger.lifecycle(splitterConfigLoadModeNotice(mode))
        logger.lifecycle("Splitter regression task log: ${logFile.absolutePath}")
    }
    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) {
        }

        override fun afterSuite(suite: TestDescriptor, result: TestResult) {
            if (suite.parent != null) {
                return
            }
            appendSplitterRegressionLog(
                runLogFileProvider.get(),
                """
                    ${splitterRegressionLogNow()} [SUITE ${result.resultType}]
                    tests=${result.testCount}
                    passed=${result.successfulTestCount}
                    failed=${result.failedTestCount}
                    skipped=${result.skippedTestCount}
                    finishedAt=${splitterRegressionLogNow()}

                """.trimIndent() + System.lineSeparator()
            )
        }

        override fun beforeTest(testDescriptor: TestDescriptor) {
            appendSplitterRegressionLog(
                runLogFileProvider.get(),
                "${splitterRegressionLogNow()} [TEST START] ${testDescriptor.className}.${testDescriptor.name}${System.lineSeparator()}"
            )
        }

        override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
            val className = testDescriptor.className ?: "<unknown class>"
            val testName = testDescriptor.name
            val duration = result.endTime - result.startTime
            val exceptions = result.exceptions.joinToString(System.lineSeparator()) { throwable -> throwableStackTrace(throwable) }
            appendSplitterRegressionLog(
                runLogFileProvider.get(),
                "${splitterRegressionLogNow()} [TEST ${result.resultType}] $className.$testName durationMs=$duration${System.lineSeparator()}" +
                        if (exceptions.isBlank()) "" else exceptions + System.lineSeparator()
            )
        }
    })
    addTestOutputListener(object : TestOutputListener {
        override fun onOutput(testDescriptor: TestDescriptor, event: TestOutputEvent) {
            val className = testDescriptor.className ?: "<unknown class>"
            val testName = testDescriptor.name
            val lineSeparator = System.lineSeparator()
            val message = event.message
            val ending = if (message.endsWith(lineSeparator) || message.endsWith("\n")) "" else lineSeparator
            appendSplitterRegressionLog(
                runLogFileProvider.get(),
                "${splitterRegressionLogNow()} [${event.destination}] $className.$testName$lineSeparator$message$ending"
            )
        }
    })
}

val splitterRestDebug by tasks.registering(Test::class) {
    description = "Run CFG-01 splitter scenario with REST config load flow"
    configureSplitterDiagnosticTask(
        mode = "rest",
        includePattern = "ru.sber.qa.splitter.SplitterFunctionalPlanTest_flow.configShouldBeLoaded",
        logFileName = "splitter-rest-debug-run.log"
    )
}

val splitterKafkaDebug by tasks.registering(Test::class) {
    description = "Run CFG-01 splitter scenario with Kafka config load flow"
    configureSplitterDiagnosticTask(
        mode = "kafka",
        includePattern = "ru.sber.qa.splitter.SplitterFunctionalPlanTest_flow.configShouldBeLoaded",
        logFileName = "splitter-kafka-debug-run.log"
    )
}
// File/credential isolation checks; no stand connections are opened by this suite.
tasks.register<Test>("propertyLayoutTest") {
    group = "verification"
    description = "Verify native connection profiles, secret source isolation and tracked properties"
    dependsOn(tasks.named("testClasses"))
    testClassesDirs = sourceSets.getByName("test").output.classesDirs
    classpath = sourceSets.getByName("test").runtimeClasspath
    useJUnitPlatform()
    filter {
        includeTestsMatching("config.services.core.*Test")
        includeTestsMatching("config.services.db.DatabaseProfileConfigurationTest")
        includeTestsMatching("util.ignite.IgniteConfigurationTest")
        includeTestsMatching("util.ignite.EnvironmentPropertiesTest")
    }
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
    systemProperty("secure.placeholders.fail-on-unresolved", "true")
}
