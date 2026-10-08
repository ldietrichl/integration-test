@file:Suppress("UNCHECKED_CAST")

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Suppress("UNCHECKED_CAST")
val buildContext = extra["platformBuildContext"] as MutableMap<String, Any?>

fun fileFromProjectOrAbsolute(path: String): File = (buildContext["fileFromProjectOrAbsolute"] as (String) -> File)(path)
fun normalizedFileEnvironment(): String = (buildContext["normalizedFileEnvironment"] as () -> String)()
fun resolveAllureUploadResultsDirectory(): File =
    layout.buildDirectory.dir("testops-results/${normalizedFileEnvironment()}/allure-results").get().asFile
fun resolveRegressionUploadResultsDirectory(): File =
    layout.buildDirectory.dir("regression-results/${normalizedFileEnvironment()}/testops").get().asFile
fun configValue(name: String, vararg envNames: String, defaultValue: String? = null): String? =
    (buildContext["configValue"] as (String, Array<out String>, String?) -> String?)(name, envNames, defaultValue)
fun configFlag(name: String, vararg envNames: String, defaultValue: Boolean = false): Boolean =
    (buildContext["configFlag"] as (String, Array<out String>, Boolean) -> Boolean)(name, envNames, defaultValue)
fun artifactReadObject(file: File): Map<*, *> = (buildContext["artifactReadObject"] as (File) -> Map<*, *>)(file)
fun artifactAssertExecutionKind(result: Map<*, *>, registration: Boolean, context: String): Unit = (buildContext["artifactAssertExecutionKind"] as (Map<*, *>, Boolean, String) -> Unit)(result, registration, context)

fun findExecutableOnPath(executableName: String): File? {
    val windows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    val names = if (windows && !executableName.endsWith(".exe", ignoreCase = true)) {
        listOf("$executableName.exe", executableName)
    } else {
        listOf(executableName)
    }

    return (System.getenv("PATH") ?: "")
        .split(File.pathSeparator)
        .asSequence()
        .filter { it.isNotBlank() }
        .flatMap { directory -> names.asSequence().map { name -> File(directory, name) } }
        .firstOrNull { it.isFile }
}

fun resolveAllurectlExecutable(): String {
    configValue("allurectlPath", "ALLURECTL_PATH")?.let { configuredPath ->
        val configuredFile = fileFromProjectOrAbsolute(configuredPath)
        if (configuredFile.isFile) {
            return configuredFile.absolutePath
        }
        throw GradleException("Configured allurectl executable not found: ${configuredFile.absolutePath}")
    }

    val localName = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        "allurectl.exe"
    } else {
        "allurectl"
    }
    val localAllurectl = rootProject.file("allure/bin/$localName")
    if (localAllurectl.isFile) {
        return localAllurectl.absolutePath
    }

    findExecutableOnPath("allurectl")?.let { return it.absolutePath }

    throw GradleException(
        "allurectl executable not found. Set ALLURECTL_PATH/-PallurectlPath, " +
                "or place allurectl into ${rootProject.file("allure/bin").absolutePath}."
    )
}

fun parsePositiveInt(value: String, name: String): Int =
    value.toIntOrNull()?.takeIf { it > 0 }
        ?: throw GradleException("$name must be a positive integer, but was: $value")

val testOpsLaunchTimestamp: String =
    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

fun testOpsLaunchName(): String {
    val baseName = configValue(
        "allureLaunchName",
        "ALLURE_LAUNCH_NAME",
        defaultValue = "ExpLab Gradle"
    )!!
    return "$baseName $testOpsLaunchTimestamp"
}

val validateTestOpsUploadConfig by tasks.registering {
    group = "verification"
    description = "Validate credentials and executable for an explicit Allure TestOps upload"

    doLast {
        val endpoint = configValue(
            "allureEndpoint",
            "ALLURE_ENDPOINT",
            defaultValue = "https://testops.sigma.sbrf.ru"
        )!!.trimEnd('/')
        val projectId = parsePositiveInt(
            configValue("allureProjectId", "ALLURE_PROJECT_ID", defaultValue = "3359")!!,
            "allureProjectId/ALLURE_PROJECT_ID"
        )
        val projectUrl = configValue("allureProjectUrl", "ALLURE_PROJECT_URL")
            ?: "$endpoint/project/$projectId"
        val launchName = testOpsLaunchName()
        val dryRun = configFlag("allureDryRun", "DRY_RUN")

        println("TestOps upload preflight")
        println("Endpoint   : $endpoint")
        println("Project    : $projectId")
        println("Project URL: $projectUrl")
        println("Launch     : $launchName")
        if (dryRun) {
            println("Dry-run    : enabled")
            return@doLast
        }

        configValue("allureToken", "ALLURE_TOKEN")
            ?: throw GradleException(
                "Set allureToken in secure.local.override.properties before running TestOps upload tasks."
            )

        println("allurectl  : ${resolveAllurectlExecutable()}")
    }
}

fun registerTestOpsUploadTask(
    taskName: String,
    taskGroup: String,
    preparationTask: String,
    resultsDirectory: () -> File,
    launchScope: String? = null,
    registrationOnly: Boolean = false
) = tasks.register(taskName) {
    group = taskGroup
    description = if (registrationOnly)
        "Register descriptions in TestOps without executing scenarios; invoke this task directly"
    else if (preparationTask.contains("Regression"))
        "Prepare and upload the latest regression stages to TestOps without running tests"
    else "Prepare and upload results of selected IDEA/Gradle tests to TestOps without running tests"
    dependsOn(validateTestOpsUploadConfig)
    dependsOn(preparationTask)

    doLast {
        val endpoint = configValue(
            "allureEndpoint",
            "ALLURE_ENDPOINT",
            defaultValue = "https://testops.sigma.sbrf.ru"
        )!!.trimEnd('/')
        val projectId = parsePositiveInt(
            configValue("allureProjectId", "ALLURE_PROJECT_ID", defaultValue = "3359")!!,
            "allureProjectId/ALLURE_PROJECT_ID"
        )
        val projectUrl = configValue("allureProjectUrl", "ALLURE_PROJECT_URL")
            ?: "$endpoint/project/$projectId"
        val resultsDir = resultsDirectory()
        val uploadBatch = parsePositiveInt(
            configValue(
                "allureUploadBatch",
                "ALLURE_UPLOAD_BATCH",
                "ALLURE_IMPORT_BATCH",
                defaultValue = "100"
            )!!,
            "allureUploadBatch/ALLURE_UPLOAD_BATCH"
        )
        val launchName = listOfNotNull(launchScope, testOpsLaunchName()).joinToString(" | ")
        val launchTags = configValue("allureLaunchTags", "ALLURE_LAUNCH_TAGS")
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        val dryRun = configFlag("allureDryRun", "DRY_RUN")
        val insecure = configFlag(
            "allureInsecure",
            "ALLURE_INSECURE",
            defaultValue = endpoint.contains("sigma.sbrf.ru", ignoreCase = true)
        )

        if (!resultsDir.isDirectory) {
            throw GradleException(
                "Allure results directory not found: ${resultsDir.absolutePath}. " +
                        "Inspect the output of $preparationTask."
            )
        }

        val allResultFiles = resultsDir.listFiles()
            ?.filter { it.isFile }
            .orEmpty()
        val testResultFiles = allResultFiles.filter { it.name.endsWith("-result.json") }
        if (testResultFiles.isEmpty()) {
            throw GradleException("No *-result.json files found in ${resultsDir.absolutePath}.")
        }

        testResultFiles.forEach { artifactAssertExecutionKind(artifactReadObject(it), registrationOnly, it.name) }
        println("Execution : " + if (registrationOnly) "REGISTRATION ONLY - tests not executed" else "functional results")
        println("Endpoint  : $endpoint")
        println("Project   : $projectId")
        println("Project URL: $projectUrl")
        println("Results   : ${resultsDir.absolutePath}")
        println("Launch    : $launchName")
        if (launchTags.isNotEmpty()) {
            println("Tags      : ${launchTags.joinToString(", ")}")
        }
        println("Found ${allResultFiles.size} file(s), ${testResultFiles.size} test result(s).")

        if (dryRun) {
            println("[dry-run] Would upload ${allResultFiles.size} file(s) via allurectl.")
            return@doLast
        }

        val token = configValue("allureToken", "ALLURE_TOKEN")
            ?: throw GradleException(
                "Set allureToken in secure.local.override.properties before uploading to TestOps."
            )
        val allurectl = resolveAllurectlExecutable()

        val uploadArgs = mutableListOf(
            "upload",
            resultsDir.absolutePath,
            "--endpoint",
            endpoint,
            "--project-id",
            projectId.toString(),
            "--launch-name",
            launchName,
            "--size",
            uploadBatch.toString()
        )
        if (launchTags.isNotEmpty()) {
            uploadArgs.addAll(listOf("--launch-tags", launchTags.joinToString(",")))
        }
        if (insecure) {
            uploadArgs.add("--insecure")
        }

        val uploadEnvironment = mutableMapOf<String, String>(
            "ALLURE_ENDPOINT" to endpoint,
            "ALLURE_PROJECT_ID" to projectId.toString(),
            "ALLURE_PROJECT_URL" to projectUrl,
            "ALLURE_TOKEN" to token,
            "ALLURE_LAUNCH_NAME" to launchName,
            "ALLURE_RESULTS" to resultsDir.absolutePath
        )
        if (insecure) {
            uploadEnvironment["NODE_TLS_REJECT_UNAUTHORIZED"] = "0"
        }

        project.exec {
            executable = allurectl
            args(uploadArgs)
            environment(uploadEnvironment)
        }
    }
}
val testOpsUpload = registerTestOpsUploadTask(
    "testOpsUpload", "testops", "prepareTestOpsResults", ::resolveAllureUploadResultsDirectory
)
val regressionTestOpsUpload = registerTestOpsUploadTask(
    "regressionTestOpsUpload", "testops", "prepareRegressionTestOpsResults", ::resolveRegressionUploadResultsDirectory
)

// Explicit cross-script contract; task actions remain lazy.
buildContext["registerTestOpsUploadTask"] = { name: String, group: String, preparation: String, results: () -> File, scope: String?, registration: Boolean -> registerTestOpsUploadTask(name, group, preparation, results, scope, registration) }
