plugins { java }
val workspace = file("../../..")
val context = linkedMapOf<String, Any?>()
extra["platformBuildContext"] = context
context["configValue"] = { _: String, _: Array<out String>, fallback: String? -> fallback }
context["normalizedFileEnvironment"] = { "dev" }
context["regressionEnvironment"] = "dev"
context["bypassTests"] = tasks.register<Test>("bypassTests") {
    systemProperty("allure.results.directory", layout.buildDirectory.dir("bypass-results/dev/allure-results").get().asFile.absolutePath)
}
context["registerTestOpsUploadTask"] = { name: String, group: String, prep: String, dir: () -> File, scope: String?, registration: Boolean ->
    tasks.register(name) { doLast { error("Upload is forbidden in local layout checks") } }
}
extra["testOpsSourceResultsDir"] = file("build/allure-results")
listOf("prepareTestOpsResults", "cleanTestOpsResults", "copyAllureCategories").forEach { tasks.register(it) }
listOf("splitterRestRegression", "splitterKafkaRegression").forEach { tasks.register<Test>(it) }
apply(from = workspace.resolve("gradle/testops/artifacts.gradle.kts"))
apply(from = workspace.resolve("gradle/build-logic/tickets.gradle.kts"))
apply(from = workspace.resolve("gradle/testops/run-integrity.gradle"))
apply(from = workspace.resolve("gradle/testops/completed-stages.gradle"))
apply(from = workspace.resolve("gradle/testops/splitter-results.gradle"))
// No Test/JavaExec task is executed here. Production task configuration and artifact actions are exercised directly.
apply(from = "checks.gradle")
