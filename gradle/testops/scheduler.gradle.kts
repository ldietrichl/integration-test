@file:Suppress("UNCHECKED_CAST")
import java.io.File
val buildContext = extra["platformBuildContext"] as MutableMap<String, Any?>
val regressionResultsRoot get() = buildContext["regressionResultsRoot"] as File
val regressionEnvironment get() = buildContext["regressionEnvironment"] as String
val schedulerTestTaskNames get() = buildContext["schedulerTestTaskNames"] as List<String>
val bypassToolSourceSet get() = buildContext["bypassToolSourceSet"] as org.gradle.api.tasks.SourceSet
fun normalizedFileEnvironment(): String = (buildContext["normalizedFileEnvironment"] as () -> String)()
fun configValue(name: String, vararg envNames: String, defaultValue: String? = null): String? =
    (buildContext["configValue"] as (String, Array<out String>, String?) -> String?)(name, envNames, defaultValue)
fun registerTestOpsUploadTask(taskName: String, taskGroup: String, preparationTask: String,
    resultsDirectory: () -> File, launchScope: String? = null, registrationOnly: Boolean = false): org.gradle.api.tasks.TaskProvider<org.gradle.api.Task> =
    (buildContext["registerTestOpsUploadTask"] as (String, String, String, () -> File, String?, Boolean) -> org.gradle.api.tasks.TaskProvider<org.gradle.api.Task>)(
        taskName, taskGroup, preparationTask, resultsDirectory, launchScope, registrationOnly)

val prepareSchedulerTestOpsResults = tasks.register<JavaExec>("prepareSchedulerTestOpsResults") {
    group = "testops"
    description = "Audit one completed scheduler run and prepare raw Allure; no stand access and no upload"
    dependsOn(tasks.named(bypassToolSourceSet.classesTaskName))
    classpath = bypassToolSourceSet.runtimeClasspath
    mainClass.set("ru.sber.qa.tools.reporting.SchedulerAllureBundleTool")
    outputs.upToDateWhen { false }
    mustRunAfter(schedulerTestTaskNames)
    doFirst {
        val selectedStage = configValue("scheduler.results.stage", defaultValue = "scheduler-regression")!!
        val selectedRun = configValue("scheduler.results.run")
        val legacyRun = configValue("schedulerTestOpsRun", "SCHEDULER_TESTOPS_RUN")
        if (selectedRun != null && legacyRun != null && selectedRun != legacyRun)
            throw GradleException("Conflicting scheduler run selectors: use one run ID")
        if (legacyRun != null && selectedStage != "scheduler-regression")
            throw GradleException("schedulerTestOpsRun selects scheduler-regression only; use scheduler.results.run for another phase")
        setArgs(listOf(projectDir.absolutePath, regressionEnvironment,
            selectedStage, selectedRun ?: legacyRun ?: "latest"))
    }
}
registerTestOpsUploadTask(
    "schedulerTestOpsUpload", "testops", "prepareSchedulerTestOpsResults",
    resultsDirectory = { File(regressionResultsRoot, "scheduler-testops/allure-results") },
    launchScope = "Scheduler [${normalizedFileEnvironment()}]"
)
gradle.taskGraph.whenReady {
    if (allTasks.any { it.name in setOf("prepareSchedulerTestOpsResults", "schedulerTestOpsUpload") }
        && allTasks.any { it.name in setOf("clean", "cleanRegressionResults") })
        throw GradleException("Do not combine evidence cleanup with scheduler TestOps preparation/upload")
}
