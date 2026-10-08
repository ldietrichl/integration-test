@file:Suppress("UNCHECKED_CAST")

import java.math.BigInteger as ArtifactBigInteger
import org.gradle.api.tasks.testing.Test
import java.io.File

@Suppress("UNCHECKED_CAST")
val buildContext = extra["platformBuildContext"] as MutableMap<String, Any?>

fun configValue(name: String, vararg envNames: String, defaultValue: String? = null): String? =
    (buildContext["configValue"] as (String, Array<out String>, String?) -> String?)(name, envNames, defaultValue)
fun artifactUnredirected(file: File): File = (buildContext["artifactUnredirected"] as (File) -> File)(file)
fun artifactContained(boundary: File, candidate: File): File = (buildContext["artifactContained"] as (File, File) -> File)(boundary, candidate)
fun artifactReadObject(file: File): Map<*, *> = (buildContext["artifactReadObject"] as (File) -> Map<*, *>)(file)
fun artifactFileHash(file: File): String = (buildContext["artifactFileHash"] as (File) -> String)(file)
fun artifactCounter(value: Any?, context: String): ArtifactBigInteger = (buildContext["artifactCounter"] as (Any?, String) -> ArtifactBigInteger)(value, context)
fun selectedArtifactActions(environment: String, input: File, registration: Boolean = false): Pair<() -> Unit, () -> Unit> = (buildContext["selectedArtifactActions"] as (String, File, Boolean) -> Pair<() -> Unit, () -> Unit>)(environment, input, registration)
fun regressionArtifactActions(environment: String, root: File): Pair<() -> Unit, () -> Unit> = (buildContext["regressionArtifactActions"] as (String, File) -> Pair<() -> Unit, () -> Unit>)(environment, root)
val bypassRoot get() = buildContext["bypassRoot"] as File
val bypassEnvironment get() = buildContext["bypassEnvironment"] as String
val bypassTests get() = buildContext["bypassTests"] as org.gradle.api.tasks.TaskProvider<Test>
fun registerTestOpsUploadTask(taskName: String, taskGroup: String, preparationTask: String,
    resultsDirectory: () -> File, launchScope: String? = null, registrationOnly: Boolean = false): org.gradle.api.tasks.TaskProvider<org.gradle.api.Task> =
    (buildContext["registerTestOpsUploadTask"] as (String, String, String, () -> File, String?, Boolean) -> org.gradle.api.tasks.TaskProvider<org.gradle.api.Task>)(
        taskName, taskGroup, preparationTask, resultsDirectory, launchScope, registrationOnly)
val regressionEnvironment get() = buildContext["regressionEnvironment"] as String
val regressionResultsRoot get() = buildContext["regressionResultsRoot"] as File
val schedulerTestTaskNames get() = buildContext["schedulerTestTaskNames"] as List<String>
val regressionTaskNames get() = buildContext["regressionTaskNames"] as List<String>

val regressionArtifacts = regressionArtifactActions(regressionEnvironment, regressionResultsRoot)
tasks.register("prepareBypassTestOpsResults") {
    group = "registration"
    description = "Validate and prepare one completed registration-only run; never run tests or upload"
    mustRunAfter(bypassTests)
    outputs.upToDateWhen { false }
    doLast {

        artifactUnredirected(bypassRoot)
        val requested = configValue("bypass.results.run")
        val pointer = File(bypassRoot, "latest.txt")
        artifactUnredirected(pointer)
        val relative = if (requested == null) {
            if (!pointer.isFile) throw GradleException("Run bypassTests first or select -Pbypass.results.run")
            pointer.readText(Charsets.UTF_8).trim()
        } else "runs/" + requested
        if (!Regex("runs/[A-Za-z0-9][A-Za-z0-9_-]*").matches(relative))
            throw GradleException("Invalid registration run selector")
        val run = artifactContained(bypassRoot, File(bypassRoot, relative))
        artifactUnredirected(run)
        val summaryFile = File(run, "summary.json")
        artifactUnredirected(summaryFile)
        val summaryHash = artifactFileHash(summaryFile)
        val summary = artifactReadObject(summaryFile)
        val count = artifactCounter(summary["total"], "registration total")
        if (summary["completed"] != true || summary["registrationOnly"] != true ||
            summary["environment"] != bypassEnvironment || count <= ArtifactBigInteger.ZERO ||
            artifactCounter(summary["passed"], "registration passed") != count ||
            artifactCounter(summary["failed"], "registration failed") != ArtifactBigInteger.ZERO ||
            artifactCounter(summary["skipped"], "registration skipped") != ArtifactBigInteger.ZERO)
            throw GradleException("Registration run did not complete successfully; no older-run fallback")
        val raw = File(run, "allure-results")
        val results = raw.listFiles()?.count { it.isFile && it.name.endsWith("-result.json") } ?: 0
        if (ArtifactBigInteger.valueOf(results.toLong()) != count)
            throw GradleException("Registration JUnit/Allure result count mismatch")
        selectedArtifactActions(bypassEnvironment, raw, true).first.invoke()
        if (artifactFileHash(summaryFile) != summaryHash)
            throw GradleException("Registration summary changed during preparation; do not upload this bundle")
    }
}
registerTestOpsUploadTask("bypassTestOpsUpload", "registration", "prepareBypassTestOpsResults",
    resultsDirectory = { layout.buildDirectory.dir("bypass-testops-results/" + bypassEnvironment + "/allure-results").get().asFile },
    launchScope = "REGISTRATION ONLY [NOT EXECUTED]", registrationOnly = true)
gradle.taskGraph.whenReady {
    // Naming the upload task is consent; dependencies/finalizers must never upload implicitly.
    val registrationUpload = allTasks.firstOrNull {
        it.project == project && it.name == "bypassTestOpsUpload"
    }
    if (registrationUpload != null && gradle.startParameter.taskNames.none {
        it == registrationUpload.name || it == registrationUpload.path
    }) {
        throw GradleException(
            "Registration upload must be requested directly as bypassTestOpsUpload or " +
                registrationUpload.path + "; implicit dependency/finalizer uploads are prohibited"
        )
    }
    val registrationExport = allTasks.any { it.name in setOf("prepareBypassTestOpsResults", "bypassTestOpsUpload") }
    if (registrationExport && allTasks.any { (it is Test && it.name != "bypassTests") ||
        it.name in setOf("clean", "testOpsUpload", "regressionTestOpsUpload", "schedulerTestOpsUpload", "prepareTestOpsResults", "prepareRegressionTestOpsResults",
            "prepareSchedulerTestOpsResults") })
        throw GradleException("Registration export must be separate from functional execution/export and clean")
}

val allureResultsDirectory get() = buildContext["allureResultsDirectory"] as File
fun fileFromProjectOrAbsolute(path: String): File = (buildContext["fileFromProjectOrAbsolute"] as (String) -> File)(path)
extra["testOpsSourceResultsDir"] =
    configValue("testOpsSourceResultsDir", "TESTOPS_SOURCE_RESULTS_DIR")?.let(::fileFromProjectOrAbsolute)
        ?: allureResultsDirectory

val selectedTestOpsArtifacts = selectedArtifactActions(regressionEnvironment, extra["testOpsSourceResultsDir"] as File)
tasks.register("prepareRegressionTestOpsResults") {
    group = "testops"
    description = "Validate and combine the latest completed regression stages for TestOps without uploading"
    outputs.upToDateWhen { false }
    doLast { regressionArtifacts.first.invoke() }
}
tasks.register("cleanRegressionResults") {
    group = "testops"
    description = "Clear all regression results for the selected environment; retain fixture ownership manifests"
    doLast { regressionArtifacts.second.invoke() }
}
tasks.register("prepareTestOpsResults") {
    group = "testops"
    description = "Validate all selected-test Allure results and prepare a separate TestOps bundle without running tests or uploading"
    outputs.upToDateWhen { false }
    doLast { selectedTestOpsArtifacts.first.invoke() }
}
tasks.register("cleanTestOpsResults") {
    group = "testops"
    description = "Clear the selected TestOps bundle and recognized default raw Allure files before a new selected-test run"
    doLast { selectedTestOpsArtifacts.second.invoke() }
}
gradle.taskGraph.whenReady {
    if (hasTask(tasks.named("cleanTestOpsResults").get()) &&
        (hasTask(tasks.named("prepareTestOpsResults").get()) || allTasks.any { it.project == project && it.name == "testOpsUpload" })) {
        throw GradleException("Run cleanTestOpsResults separately before tests. Do not combine cleanup with TestOps preparation or upload in one command.")
    }
}

regressionTaskNames.forEachIndexed { index, taskName ->
    tasks.named(taskName) { mustRunAfter(regressionTaskNames.take(index)) }
}
// REST/Kafka need an operator to switch the server flag between independent Gradle invocations.
gradle.taskGraph.whenReady {
    if (allTasks.any { it.project == project && it.name == "splitterRestRegression" }
        && allTasks.any { it.project == project && it.name == "splitterKafkaRegression" }) {
        throw GradleException("Run splitterRestRegression and splitterKafkaRegression separately; switch SPLITTER_CONFIG_API_CONFIG_LOAD on MAPPER and REACTIONS between them.")
    }
}
tasks.named("prepareRegressionTestOpsResults") { mustRunAfter(regressionTaskNames + schedulerTestTaskNames) }
gradle.taskGraph.whenReady {
    val cleanup = hasTask(tasks.named("cleanRegressionResults").get())
    val exporting = listOf("prepareRegressionTestOpsResults", "regressionTestOpsUpload",
        "prepareSchedulerTestOpsResults", "schedulerTestOpsUpload")
        .any { hasTask(tasks.named(it).get()) }
    if (cleanup && (exporting || allTasks.any { it.project == project && it is Test }))
        throw GradleException("Run cleanRegressionResults separately; never delete regression evidence during tests or export.")
    if (configValue("schedulerTestOpsRun", "SCHEDULER_TESTOPS_RUN") != null
            && hasTask(tasks.named("regressionTestOpsUpload").get()))
        throw GradleException("schedulerTestOpsRun selects scheduler only; use schedulerTestOpsUpload, not the mixed upload.")
}
tasks.named("cleanRegressionResults") { mustRunAfter(regressionTaskNames + listOf("prepareRegressionTestOpsResults", "regressionTestOpsUpload")) }
tasks.named("prepareTestOpsResults") { mustRunAfter(tasks.withType<Test>(), tasks.named("copyAllureCategories")) }
tasks.withType<Test>().configureEach { mustRunAfter("cleanTestOpsResults") }
