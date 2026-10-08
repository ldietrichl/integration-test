@file:Suppress("UNCHECKED_CAST")

import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Suppress("UNCHECKED_CAST")
val buildContext = extra["platformBuildContext"] as MutableMap<String, Any?>

val splitterConfigLoadMode get() = buildContext["splitterConfigLoadMode"] as String
fun normalizedFileEnvironment(): String = (buildContext["normalizedFileEnvironment"] as () -> String)()
val bypassToolSourceSet get() = buildContext["bypassToolSourceSet"] as org.gradle.api.tasks.SourceSet
val bypassTestsSourceSet get() = buildContext["bypassTestsSourceSet"] as org.gradle.api.tasks.SourceSet
val generatedBypassSourcesDir get() = buildContext["generatedBypassSourcesDir"] as org.gradle.api.provider.Provider<org.gradle.api.file.Directory>
val reportEligibilityOutputDir get() = buildContext["reportEligibilityOutputDir"] as org.gradle.api.provider.Provider<org.gradle.api.file.Directory>
val generateReportEligibility get() = buildContext["generateReportEligibility"] as org.gradle.api.tasks.TaskProvider<JavaExec>
fun artifactUnredirected(file: File): File = (buildContext["artifactUnredirected"] as (File) -> File)(file)
fun artifactContained(boundary: File, candidate: File): File = (buildContext["artifactContained"] as (File, File) -> File)(boundary, candidate)
fun artifactWriteJson(file: File, value: Any): Unit = (buildContext["artifactWriteJson"] as (File, Any) -> Unit)(file, value)

val generateBypassTests by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Generate registration-only tests only for report-eligible functional tests"
    dependsOn(generateReportEligibility)
    classpath = bypassToolSourceSet.runtimeClasspath
    mainClass.set("ru.sber.qa.tools.bypass.BypassTestsGenerator")
    args(
        file("src/test/java").absolutePath,
        generatedBypassSourcesDir.get().asFile.absolutePath,
        reportEligibilityOutputDir.get().file("eligible-tests.txt").asFile.absolutePath
    )
    inputs.dir("src/test/java")
    inputs.file(reportEligibilityOutputDir.map { it.file("eligible-tests.txt") })
    outputs.dir(generatedBypassSourcesDir)
}

tasks.named(bypassTestsSourceSet.compileJavaTaskName) {
    dependsOn(generateBypassTests)
}

val bypassEnvironment = normalizedFileEnvironment()
val bypassRunId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")) +
        "-" + UUID.randomUUID().toString().take(8)
val bypassRoot = layout.buildDirectory.dir("bypass-registration/" + bypassEnvironment).get().asFile
val bypassRun = File(bypassRoot, "runs/" + bypassRunId)
val bypassRaw = File(bypassRun, "allure-results")

val bypassTests by tasks.registering(Test::class) {
    group = "registration"
    description = "Register descriptions only; no functional execution, stand access or regression results"
    doNotTrackState("Each registration run keeps its own immutable evidence")
    dependsOn(tasks.named(bypassTestsSourceSet.classesTaskName))
    testClassesDirs = bypassTestsSourceSet.output.classesDirs
    classpath = bypassTestsSourceSet.runtimeClasspath
    useJUnitPlatform()
    filter.setFailOnNoMatchingTests(true)
    outputs.upToDateWhen { false }
    maxParallelForks = 1
    testLogging.showStandardStreams = false
    reports.junitXml.outputLocation.set(File(bypassRun, "test-results"))
    reports.html.outputLocation.set(File(bypassRun, "reports"))
    doFirst {

        artifactUnredirected(bypassRoot); artifactUnredirected(bypassRun)
        artifactContained(layout.buildDirectory.get().asFile, bypassRun)
        // Gradle creates JUnit report directories before doFirst; only raw evidence must be new.
        if (bypassRaw.exists() || File(bypassRun, "summary.json").exists())
            throw GradleException("Registration raw evidence already exists")
        if (!bypassRaw.mkdirs()) throw GradleException("Cannot create isolated registration directory")
        setSystemProperties(mapOf(
            "file.encoding" to "UTF-8", "env" to bypassEnvironment,
            "splitter.config.load.mode" to splitterConfigLoadMode,
            "junit.jupiter.execution.parallel.enabled" to "false",
            "junit.jupiter.extensions.autodetection.enabled" to "false",
            "allure.results.directory" to bypassRaw.absolutePath
        ))
        artifactWriteJson(File(bypassRun, "summary.json"), mapOf(
            "completed" to false, "environment" to bypassEnvironment, "registrationOnly" to true))
        // Publish the incomplete run before forking: a worker startup failure must not select older results.
        val pointer = File(bypassRoot, "latest.txt")
        artifactUnredirected(pointer)
        pointer.writeText("runs/" + bypassRunId + "\n", Charsets.UTF_8)
        logger.lifecycle("REGISTRATION ONLY: functional scenarios will NOT run. Results: " + bypassRun)
    }
    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) {}
        override fun beforeTest(testDescriptor: TestDescriptor) {}
        override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {}
        override fun afterSuite(suite: TestDescriptor, result: TestResult) {
            if (suite.parent != null) return
            val valid = result.testCount > 0 && result.failedTestCount == 0L && result.skippedTestCount == 0L

            artifactWriteJson(File(bypassRun, "summary.json"), mapOf(
                "completed" to valid, "environment" to bypassEnvironment, "registrationOnly" to true,
                "total" to result.testCount, "passed" to result.successfulTestCount,
                "failed" to result.failedTestCount, "skipped" to result.skippedTestCount))
            // Keep an invalid latest pointer too: preparation must never silently fall back to an older successful run.
            val pointer = File(bypassRoot, "latest.txt")
            artifactUnredirected(pointer)
            pointer.writeText("runs/" + bypassRunId + "\n", Charsets.UTF_8)
        }
    })
}

gradle.taskGraph.whenReady {
    if (hasTask(bypassTests.get())) {
        if (allTasks.any { it is Test && it != bypassTests.get() })
            throw GradleException("Run registration separately from functional tests")
        val forbidden = setOf("testOpsUpload", "regressionTestOpsUpload", "schedulerTestOpsUpload", "prepareTestOpsResults", "prepareRegressionTestOpsResults",
            "prepareSchedulerTestOpsResults", "clean",
            "cleanTestOpsResults", "cleanRegressionResults", "allureReport", "allureServe")
        if (allTasks.any { it.name in forbidden })
            throw GradleException("Registration cannot be combined with functional report preparation, upload or cleanup")
    }
}


// Explicit cross-script contract; task actions remain lazy.
buildContext["bypassRoot"] = bypassRoot
buildContext["bypassEnvironment"] = bypassEnvironment
buildContext["bypassTests"] = bypassTests
