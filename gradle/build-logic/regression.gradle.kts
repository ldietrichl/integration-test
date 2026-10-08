@file:Suppress("UNCHECKED_CAST")

import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestOutputListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.process.JavaForkOptions
import groovy.json.JsonOutput
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Suppress("UNCHECKED_CAST")
val buildContext = extra["platformBuildContext"] as MutableMap<String, Any?>
val allureResultsDirectory get() = buildContext["allureResultsDirectory"] as File
val testRuntimeProperties get() = buildContext["testRuntimeProperties"] as Properties

val sourceSets = extensions.getByType<SourceSetContainer>()
val includeManualTests get() = buildContext["includeManualTests"] as Boolean
val includeSplitterDataOperatorTests get() = buildContext["includeSplitterDataOperatorTests"] as Boolean
val splitterConfigKafkaStatusRequired get() = buildContext["splitterConfigKafkaStatusRequired"] as String
val splitterRuntimeSystemProperties get() = buildContext["splitterRuntimeSystemProperties"] as List<String>
fun optionalTestRuntimeProperty(name: String): String? = (buildContext["optionalTestRuntimeProperty"] as (String) -> String?)(name)
fun configuredRuntimeSystemProperty(name: String): String? = (buildContext["configuredRuntimeSystemProperty"] as (String) -> String?)(name)
fun fileFromProjectOrAbsolute(path: String): File = (buildContext["fileFromProjectOrAbsolute"] as (String) -> File)(path)
fun normalizedFileEnvironment(): String = (buildContext["normalizedFileEnvironment"] as () -> String)()
fun propertyEnvName(name: String): String = (buildContext["propertyEnvName"] as (String) -> String)(name)
fun throwableStackTrace(throwable: Throwable): String = (buildContext["throwableStackTrace"] as (Throwable) -> String)(throwable)
fun splitterConfigLoadModeNotice(mode: String): String = (buildContext["splitterConfigLoadModeNotice"] as (String) -> String)(mode)
fun appendSplitterRegressionLog(file: File, text: String): Unit = (buildContext["appendSplitterRegressionLog"] as (File, String) -> Unit)(file, text)
fun configValue(name: String, vararg envNames: String, defaultValue: String? = null): String? =
    (buildContext["configValue"] as (String, Array<out String>, String?) -> String?)(name, envNames, defaultValue)
val bypassToolSourceSet get() = buildContext["bypassToolSourceSet"] as org.gradle.api.tasks.SourceSet
val auditReportingTags get() = buildContext["auditReportingTags"] as org.gradle.api.tasks.TaskProvider<org.gradle.api.Task>


// One Gradle panel for the rebuilt suites. Each stage has its own discovery, run and evidence.
val regressionEnvironment = normalizedFileEnvironment()
val regressionResultsRoot = layout.buildDirectory.dir("regression-results/$regressionEnvironment").get().asFile
val regressionProfileFile = rootProject.file("src/test/resources/regression-profiles.properties")
val regressionProfiles = Properties().apply {
    if (regressionProfileFile.isFile) regressionProfileFile.inputStream().use { load(it) }
}
val regressionRunId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")) +
        "-" + UUID.randomUUID().toString().take(8)

fun regressionRuntimeProperties(mode: String, dataOperator: Boolean, splitterTestProfile: String = "current"): Map<String, String> {
    val result = linkedMapOf(
        "env" to regressionEnvironment,
        "splitter.config.load.mode" to mode,
        "splitter.test.profile" to splitterTestProfile,
        "splitter.config.kafka.status.required" to splitterConfigKafkaStatusRequired,
        "includeSplitterDataOperatorTests" to dataOperator.toString(),
        "includeManualTests" to "false",
        "secure.placeholders.fail-on-unresolved" to "false"
    )
    splitterRuntimeSystemProperties.forEach { key ->
        configuredRuntimeSystemProperty(key)?.let { result[key] = it }
    }
    val prefix = "$regressionEnvironment."
    regressionProfiles.stringPropertyNames().filter { it.startsWith(prefix) }.forEach { key ->
        val property = key.removePrefix(prefix)
        result[property] = regressionProfiles.getProperty(key).trim()
    }
    return result
}

fun applyRegressionRuntime(task: JavaForkOptions, properties: Map<String, String>) {
    // Non-secret fixture switches may be overridden for a run; connections live in their resource files.
    val effective = LinkedHashMap<String, Any>(task.systemProperties)
    effective.keys.removeAll { it.matches(Regex("(?:ignite|data-operator\\.fixture|links\\.fixture)\\.(?:dev|ift|ift-dm|lt|local)\\..+")) }
    val cli = gradle.startParameter.projectProperties
    (System.getProperties().stringPropertyNames() + cli.keys).filter { key ->
        key.startsWith("data-operator.fixture.") || key.startsWith("links.fixture.")
    }.forEach { key -> (cli[key] ?: System.getProperty(key))?.let { effective[key] = it } }
    effective.putAll(properties)
    task.setSystemProperties(effective)
    task.environment("ENV", regressionEnvironment)
    properties.filterKeys { it.startsWith("splitter.") }.forEach { (key, value) ->
        task.environment(propertyEnvName(key), value)
    }
}

fun configureRebuiltRegression(taskName: String, stage: String, mode: String, patterns: List<String>,
                               fixedSchedulerPhase: String? = null, splitterTestProfile: String = "current") {
    val dataOperator = stage == "data-operator"
    val includeDisabledInStage = false // Ordinary regressions must honor JUnit @Disabled.
    val runtime = regressionRuntimeProperties(mode, dataOperator, splitterTestProfile).toMutableMap().apply {
        if (stage.startsWith("scheduler")) {
            val phase = fixedSchedulerPhase ?: configValue("scheduler.regression.phase",
                "SCHEDULER_REGRESSION_PHASE", defaultValue = "full")!!
            require(phase in setOf("read-only", "fixtures", "jobs", "full")) { "Invalid scheduler.regression.phase" }
            val approved = configValue("scheduler.mutation.window.approved",
                "SCHEDULER_MUTATION_WINDOW_APPROVED", defaultValue = "false")!!
            require(approved in setOf("true", "false")) { "scheduler.mutation.window.approved must be true/false" }
            put("scheduler.regression.phase", phase)
            put("scheduler.mutation.window.approved", approved)
            put("scheduler.regression.selection.enabled", (stage != "scheduler-infrastructure").toString())
            // Keep discovery and execution consistent; manual opt-in only affects this stage.
            val standSchedulerProperties = Properties().apply {
                val source = file("src/test/resources/scheduler.properties")
                if (source.isFile) source.inputStream().use { load(it) }
            }
            val manual = testRuntimeProperties.getProperty("scheduler.$regressionEnvironment.prepared.enabled")
                ?: testRuntimeProperties.getProperty("scheduler.prepared.enabled")
                ?: standSchedulerProperties.getProperty("scheduler.$regressionEnvironment.prepared.enabled")
                ?: standSchedulerProperties.getProperty("scheduler.prepared.enabled", "false")
            require(manual == "true" || manual == "false") { "includeManualTests must be true or false" }
            put("includeManualTests", (manual == "true").toString())
            val schedulerTunnel = gradle.startParameter.projectProperties["schedulerTunnel"]
                ?: System.getProperty("scheduler.tunnel.enabled")
            if (schedulerTunnel != null) {
                require(schedulerTunnel == "true" || schedulerTunnel == "false") {
                    "schedulerTunnel must be true or false"
                }
                put("scheduler.tunnel.enabled", schedulerTunnel)
            }
        }
    }
    val stageDir = File(regressionResultsRoot, stage)
    val runDir = File(stageDir, "runs/$regressionRunId")
    val rawDir = File(runDir, "allure-results")
    val discoveryDir = layout.buildDirectory.dir("report-eligibility/$regressionEnvironment/$stage")
    val exclusions = discoveryDir.map {
        it.file(if (includeDisabledInStage) "excluded-tests-include-disabled.txt" else "excluded-tests.txt").asFile
    }
    val scanner = tasks.register<JavaExec>("${taskName}Eligibility") {
        val scannerTask = this
        dependsOn(auditReportingTags, tasks.named(bypassToolSourceSet.classesTaskName))
        classpath = bypassToolSourceSet.runtimeClasspath
        mainClass.set("ru.sber.qa.tools.reporting.TestReportEligibilityScanner")
        args(file("src/test/java").absolutePath, file("config/reporting/outdated-tests.properties").absolutePath,
            discoveryDir.get().asFile.absolutePath)
        inputs.dir("src/test/java")
        inputs.dir("src/main/java/steps")
        inputs.files("src/test/resources/stand.properties", "src/test/resources/scheduler.properties",
            "src/test/resources/test.properties", regressionProfileFile,
            "src/test/resources/kafka-consumers.properties", "config/reporting/outdated-tests.properties")
        inputs.properties(runtime)
        outputs.dir(discoveryDir)
        // Credential availability can change without a tracked source change.
        outputs.upToDateWhen { false }
        doFirst {
            if (regressionEnvironment !in setOf("dev", "ift", "lt")) {
                throw GradleException("Rebuilt regression profiles are configured for dev/ift/lt; selected $regressionEnvironment")
            }
            if (!regressionProfileFile.isFile) throw GradleException("Install ${regressionProfileFile.name}")
            if (stage.startsWith("scheduler") && runtime["scheduler.regression.phase"] != "read-only"
                && runtime["scheduler.mutation.window.approved"] != "true") {
                throw GradleException("MUTATION_WINDOW_REQUIRED: use -Pscheduler.mutation.window.approved=true only in an agreed window")
            }
            applyRegressionRuntime(scannerTask, runtime)
        }
    }
    val outcomeCounts = ConcurrentHashMap<String, Long>()
    tasks.named<Test>(taskName) {
        val regressionTest = this
        group = "regression"
        dependsOn(tasks.named("testClasses"), scanner)
        testClassesDirs = sourceSets.getByName("test").output.classesDirs
        classpath = sourceSets.getByName("test").runtimeClasspath
        filter.setIncludePatterns(*patterns.toTypedArray())
        filter.setFailOnNoMatchingTests(true)
        outputs.upToDateWhen { false }
        maxParallelForks = 1
        reports.junitXml.outputLocation.set(File(runDir, "test-results"))
        reports.html.outputLocation.set(File(runDir, "reports"))
        doFirst {
            outcomeCounts.clear()
            val effective = LinkedHashMap(runtime)
            effective["allure.results.directory"] = rawDir.absolutePath
            effective["stand.artifacts.directory"] = file(".workload-recovery/private/regression/$regressionEnvironment/$stage/$regressionRunId").absolutePath
            if (stage == "splitter-rest" || stage == "splitter-kafka") {
                effective["splitter.regression.logs.dir"] = runDir.absolutePath
                logger.lifecycle(splitterConfigLoadModeNotice(mode))
            }
            effective["report.exclusions.file"] = exclusions.get().absolutePath
            effective["junit.jupiter.conditions.deactivate"] = ""
            effective["junit.jupiter.execution.parallel.enabled"] = "false"
            if (dataOperator) {
                effective["data-operator.fixture.$regressionEnvironment.enabled"] = "true"
                effective["data-operator.fixture.$regressionEnvironment.output.directory"] =
                    layout.buildDirectory.dir("regression-fixtures/$regressionEnvironment/$regressionRunId").get().asFile.absolutePath
            }
            if (stage == "experiment") {
                val toggle = optionalTestRuntimeProperty("EXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED") ?: "false"
                if (toggle !in setOf("true", "false")) throw GradleException("EXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED must be true/false in test.properties")
                effective["EXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED"] = toggle
                effective["exlab2696.running.cache.wait.timeout.ms"] = "60000"
                effective["exlab2696.running.cache.wait.poll.ms"] = "3000"
                val base = "ru.sber.qa.experiments.EXPLAB_2696."
                if (toggle == "true") {
                    filter.excludeTestsMatching(base + "RunningExperimentsV1Cache2696FlowTest")
                    filter.excludeTestsMatching(base + "RunningSplitsV1Cache2696FlowTest")
                } else filter.excludeTestsMatching(base + "RunningV1CacheV2CjEnabled2696FlowTest")
                logger.lifecycle("Experiment server must have EXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=$toggle")
            }
            applyRegressionRuntime(regressionTest, effective)
            if (!exclusions.get().isFile) throw GradleException("Missing regression eligibility output")
            exclusions.get().readLines().map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") }
                .forEach { filter.excludeTestsMatching(it) }
            if (!dataOperator) filter.excludeTestsMatching("ru.sber.qa.splitter.EXPLAB_2729.*")
            if (!rawDir.mkdirs() && !rawDir.isDirectory) throw GradleException("Cannot create $rawDir")
            file("allure/categories.json").takeIf { it.isFile }?.copyTo(File(rawDir, "categories.json"), overwrite = true)
            if (stage.startsWith("scheduler")) {
                val decisions = exclusions.get().readLines().filter {
                    it.startsWith("ru.sber.qa.scheduler.regression.") || it.startsWith("ru.sber.qa.scheduler.infrastructure.")
                }
                File(runDir, "selection.json").writeText(JsonOutput.prettyPrint(JsonOutput.toJson(mapOf(
                    "phase" to runtime["scheduler.regression.phase"],
                    "mutationWindowApproved" to (runtime["scheduler.mutation.window.approved"] == "true"),
                    "includePatterns" to filter.includePatterns.sorted(),
                    "excludedTests" to decisions,
                    "disabledDataContracts" to listOf("SCH-013", "SCH-014", "SCH-018"),
                    "omittedChecksAreNotPassed" to true
                ))), Charsets.UTF_8)
                val schedulerCategories = file("allure/scheduler-categories.json")
                if (schedulerCategories.isFile) {
                    val parser = groovy.json.JsonSlurper()
                    val base = File(rawDir, "categories.json")
                    val combined = (parser.parse(schedulerCategories) as List<*>) +
                        (if (base.isFile) parser.parse(base) as List<*> else emptyList<Any>())
                    base.writeText(JsonOutput.toJson(combined), Charsets.UTF_8)
                }
            }
            File(stageDir, "latest.txt").writeText("runs/$regressionRunId\n", Charsets.UTF_8)
            File(runDir, "summary.json").writeText(JsonOutput.toJson(mapOf(
                "environment" to regressionEnvironment, "stage" to stage, "completed" to false
            )), Charsets.UTF_8)
            logger.lifecycle("Regression $stage: env=$regressionEnvironment (test.properties), results=$runDir")
        }
        addTestOutputListener(object : TestOutputListener {
            override fun onOutput(descriptor: TestDescriptor, event: TestOutputEvent) {
                appendSplitterRegressionLog(File(runDir, "console.log"),
                    "[${event.destination}] ${descriptor.className}.${descriptor.name}: ${event.message}")
            }
        })
        addTestListener(object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) { }
            override fun beforeTest(testDescriptor: TestDescriptor) { }
            override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
                val message = result.exceptions.joinToString("\n") { throwableStackTrace(it) }
                val kind = when {
                    result.resultType == TestResult.ResultType.SUCCESS -> "PASSED"
                    message.contains("PHASE_NOT_SELECTED") -> "NOT_SELECTED"
                    Regex("FOREIGN_RUNNABLE_TASKS|DEPENDENCY_BLOCKED|MUTATION_WINDOW_REQUIRED|KUBECONFIG_USER_AUTH_REJECTED|KUBECONFIG_AUTH|WORKLOAD_READ_AUTH_REQUIRED").containsMatchIn(message) -> "BLOCKED_ENVIRONMENT"
                    result.resultType == TestResult.ResultType.SKIPPED -> "SKIPPED"
                    result.exceptions.any { it is AssertionError || it.javaClass.name.contains("MultipleFailures") }
                        || message.contains("AssertionFailedError") || message.contains("AssertionError") -> "ASSERTION_FAILURE"
                    else -> "EXECUTION_ERROR"
                }
                outcomeCounts.merge(kind, 1L) { left, right -> left + right }
                appendSplitterRegressionLog(File(runDir, "console.log"),
                    "[${result.resultType}] ${testDescriptor.className}.${testDescriptor.name}\n" +
                        result.exceptions.joinToString("\n") { throwableStackTrace(it) })
            }
            override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                if (suite.parent != null) return
                File(runDir, "summary.json").writeText(JsonOutput.prettyPrint(JsonOutput.toJson(mapOf(
                    "environment" to regressionEnvironment, "stage" to stage, "completed" to true,
                    "total" to result.testCount, "passed" to result.successfulTestCount,
                    "failed" to result.failedTestCount, "skipped" to result.skippedTestCount,
                    "phase" to (runtime["scheduler.regression.phase"] ?: "not-applicable"),
                    "outcomes" to outcomeCounts.toSortedMap(),
                    "completionDoesNotMeanPassed" to true
                ))), Charsets.UTF_8)
            }
        })
    }
}

// One service regression = one real Test task, one eligibility scanner, one run directory.
// Splitter alone has two explicit modes; tickets and diagnostics are separate entry points.
data class ServiceRegressionSpec(
    val name: String, val stage: String, val mode: String,
    val patterns: List<String>, val description: String
)
val serviceRegressions = listOf(
    ServiceRegressionSpec("schedulerRegression", "scheduler-regression", "rest",
        listOf("ru.sber.qa.scheduler.regression.*"),
        "Scheduler: регресс сервиса; по умолчанию full, изменения только в согласованное окно"),
    ServiceRegressionSpec("experimentServiceRegression", "experiment", "rest",
        listOf("ru.sber.qa.experiments.*", "ru.sber.qa.controllers.refBookController.*"),
        "Experiment: регресс сервиса и справочников"),
    ServiceRegressionSpec("dataOperatorRegression", "data-operator", "rest",
        listOf("ru.sber.qa.dataoperator.regression.*", "ru.sber.qa.dataoperator.EXPLAB_2411.*",
            "ru.sber.qa.dataoperator.EXPLAB_2974.DataOperatorLinksFunctionalFlowTest",
            "ru.sber.qa.dataoperator.EXPLAB_2974.DataOperatorLinksValidationFlowTest",
            "ru.sber.qa.splitter.EXPLAB_2729.*"),
        "Data Operator: регресс REST и управляемых тестовых данных"),
    ServiceRegressionSpec("splitterRestRegression", "splitter-rest", "rest",
        listOf("ru.sber.qa.splitter.*"), "Splitter: регресс с загрузкой конфигурации через REST"),
    ServiceRegressionSpec("splitterKafkaRegression", "splitter-kafka", "kafka",
        listOf("ru.sber.qa.splitter.*"), "Splitter: регресс с загрузкой конфигурации через Kafka")
)
serviceRegressions.forEach { spec ->
    tasks.register<Test>(spec.name) {
        description = spec.description
        if (spec.name == "schedulerRegression")
            filter.excludeTestsMatching("ru.sber.qa.scheduler.regression.testsheduler")
    }
    configureRebuiltRegression(spec.name, spec.stage, spec.mode, spec.patterns)
}
tasks.register<Test>("explab2690MapperCoverage") {
    description = "EXPLAB-2690: MAPPER-only coverage for no-MAIN, alternative MAIN and finalExpGroup contract"
    filter.excludeTestsMatching("ru.sber.qa.splitter.EXPLAB_2690.SplitterReactions*")
}
configureRebuiltRegression(
    "explab2690MapperCoverage", "splitter-rest-2690-mapper", "rest",
    listOf("ru.sber.qa.splitter.EXPLAB_2690.*"),
    splitterTestProfile = "mapper-alternative-contract"
)
tasks.register<Test>("schedulerInfrastructureDiagnostics") {
    description = "Scheduler infrastructure diagnostics including explicitly permitted Deployment/ConfigMap operations"
}
configureRebuiltRegression("schedulerInfrastructureDiagnostics", "scheduler-infrastructure", "rest", listOf(
    "ru.sber.qa.scheduler.infrastructure.*"), "full")
tasks.register<Test>("schedulerPreflight") {
    description = "Read-only scheduler schema/dictionary diagnostics"
}
configureRebuiltRegression("schedulerPreflight", "scheduler-preflight", "rest", listOf(
    "ru.sber.qa.scheduler.regression.SchedulerPreflightFlowTest"), "read-only")
val schedulerTestTaskNames = listOf("schedulerRegression", "schedulerPreflight", "schedulerInfrastructureDiagnostics")
gradle.taskGraph.whenReady {
    val selected = allTasks.filter { it.project == project && it.name in schedulerTestTaskNames }
    if (selected.size > 1)
        throw GradleException("Run one scheduler regression or diagnostic task per invocation: " + selected.joinToString { it.name })
    if (selected.isNotEmpty() && allTasks.any { it.name in setOf("clean", "cleanRegressionResults") })
        throw GradleException("Do not combine scheduler execution and cleanup of evidence in one invocation")
}
extra["regressionResultsRoot"] = regressionResultsRoot
extra["regressionEnvironment"] = regressionEnvironment
extra["serviceRegressionDescriptions"] = serviceRegressions.associate { it.name to it.description }

val regressionTaskNames = serviceRegressions.map { it.name }

// Explicit cross-script contract; task actions remain lazy.
buildContext["regressionEnvironment"] = regressionEnvironment
buildContext["regressionResultsRoot"] = regressionResultsRoot
buildContext["schedulerTestTaskNames"] = schedulerTestTaskNames
buildContext["regressionTaskNames"] = regressionTaskNames
