@file:Suppress("UNCHECKED_CAST")

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Properties

@Suppress("UNCHECKED_CAST")
val buildContext = extra["platformBuildContext"] as MutableMap<String, Any?>



val gradleLocalProperties = Properties()
val gradleLocalPropertiesFile = rootProject.file("gradle.local.properties")
if (gradleLocalPropertiesFile.isFile) {
    gradleLocalPropertiesFile.inputStream().use { gradleLocalProperties.load(it) }
}

val testRuntimeProperties = Properties()
val testRuntimePropertiesFile = rootProject.file("src/test/resources/test.properties")
if (testRuntimePropertiesFile.isFile) {
    testRuntimePropertiesFile.inputStream().use { testRuntimeProperties.load(it) }
}

val secureLocalProperties = Properties()
val secureLocalOverrideFile = rootProject.file("secure.local.override.properties")
if (secureLocalOverrideFile.isFile) {
    secureLocalOverrideFile.inputStream().use { secureLocalProperties.load(it) }
}

fun usableLocalProperty(value: String?): String? =
    value?.trim()
        ?.takeUnless { it.isBlank() }
        ?.takeUnless { it.startsWith("<SET_ME_") && it.endsWith(">") }

fun isSecretProperty(name: String): Boolean =
    name.startsWith("SECURE_") || name.contains("password", ignoreCase = true) ||
        name.contains("token", ignoreCase = true) || name.contains("secret", ignoreCase = true) ||
        name in setOf("nexusUserSigma", "nexusPasswordSigma")

fun optionalLocalProperty(name: String): String? =
    if (isSecretProperty(name)) usableLocalProperty(secureLocalProperties.getProperty(name))
    else usableLocalProperty(project.findProperty(name) as String?)
        ?: usableLocalProperty(gradleLocalProperties.getProperty(name))
        ?: usableLocalProperty(System.getenv(name))

fun optionalEnv(name: String): String? =
    usableLocalProperty(System.getenv(name))

fun optionalConfigProperty(name: String): String? =
    optionalLocalProperty(name)

fun optionalTestRuntimeProperty(name: String): String? =
    usableLocalProperty(testRuntimeProperties.getProperty(name))

fun configValue(name: String, vararg envNames: String, defaultValue: String? = null): String? =
    if (isSecretProperty(name)) optionalLocalProperty(name)
        ?: envNames.asSequence().mapNotNull { usableLocalProperty(secureLocalProperties.getProperty(it)) }.firstOrNull()
        ?: defaultValue
    else usableLocalProperty(System.getProperty(name))
        ?: optionalConfigProperty(name)
        ?: envNames.asSequence().mapNotNull(::optionalEnv).firstOrNull()
        ?: defaultValue

fun configFlag(name: String, vararg envNames: String, defaultValue: Boolean = false): Boolean {
    val value = configValue(name, *envNames)
        ?: return defaultValue
    return value.equals("true", ignoreCase = true) ||
            value.equals("yes", ignoreCase = true) ||
            value == "1"
}

fun isGradleTaskRequested(taskName: String): Boolean =
    gradle.startParameter.taskNames.any { requested ->
        requested == taskName || requested == ":$taskName" || requested.endsWith(":$taskName")
    }

fun isAnyGradleTaskRequested(vararg taskNames: String): Boolean =
    taskNames.any(::isGradleTaskRequested)

fun resolveSplitterConfigLoadMode(): String {
    val restTaskRequested = isAnyGradleTaskRequested("splitterRestRegression", "splitterRestDebug")
    val kafkaTaskRequested = isAnyGradleTaskRequested("splitterKafkaRegression", "splitterKafkaDebug")
    val requestedTaskMode = when {
        kafkaTaskRequested && !restTaskRequested -> "kafka"
        restTaskRequested && !kafkaTaskRequested -> "rest"
        else -> null
    }
    val raw = System.getProperty("splitter.config.load.mode")
        ?: optionalConfigProperty("splitter.config.load.mode")
        ?: configValue("splitterConfigLoadMode", "SPLITTER_CONFIG_LOAD_MODE")
        ?: requestedTaskMode
        ?: optionalTestRuntimeProperty("splitter.config.load.mode")
        ?: "rest"
    val mode = raw.trim().replace('-', '_').lowercase(Locale.ROOT)
    if (mode != "rest" && mode != "kafka") {
        throw GradleException("Unsupported splitter.config.load.mode=$raw. Expected one of: rest, kafka")
    }
    return mode
}

fun fileFromProjectOrAbsolute(pathValue: String): File {
    val candidate = File(pathValue)
    return if (candidate.isAbsolute) candidate else rootProject.file(pathValue)
}

fun generatedOutputDirectory(pathValue: String): File {
    val candidate = fileFromProjectOrAbsolute(pathValue).canonicalFile
    val buildRoot = layout.buildDirectory.get().asFile.canonicalFile.toPath()
    if (candidate.toPath() == buildRoot || !candidate.toPath().startsWith(buildRoot)) {
        throw GradleException("Generated test output must be in a subdirectory of build: $pathValue")
    }
    return candidate
}

// Версии зависимостей
val perfeccionistaVersion = project.properties["perfeccionistaVersion"] as String?
val platformvatframeworkVersion = project.properties["platformvatframeworkVersion"] as String?

val javaxAnnotationVersion = project.properties["javaxAnnotationVersion"] as String?
val slf4jVersion = project.properties["slf4jVersion"] as String?
val awaitilityVersion = project.properties["awaitilityVersion"] as String?
val mockitoVersion = project.properties["mockitoVersion"] as String?
val configuredUseLocalLibs = optionalLocalProperty("useLocalLibs")
val configuredLocalLibDir = optionalLocalProperty("localLibDir") ?: System.getenv("LOCAL_LIB_DIR")
val localLibsRequested = (configuredUseLocalLibs?.equals("true", ignoreCase = true) == true) ||
        (System.getenv("USE_LOCAL_LIBS")?.equals("true", ignoreCase = true) == true) ||
        !System.getenv("LOCAL_LIB_DIR").isNullOrBlank()
val localLibDir = if (configuredLocalLibDir.isNullOrBlank()) {
    rootProject.layout.projectDirectory.dir("../lib").asFile
} else {
    file(configuredLocalLibDir)
}
val localLibJars = fileTree(localLibDir) {
    include("**/*.jar")
}
val useLocalLibs = localLibsRequested && localLibDir.exists() && localLibJars.files.isNotEmpty()
val includeDisabledTests = configFlag("includeDisabledTests", "INCLUDE_DISABLED_TESTS")
val includeManualTests = configFlag("includeManualTests", "INCLUDE_MANUAL_TESTS")
val includeSplitterDataOperatorTests =
    configFlag("includeSplitterDataOperatorTests", "INCLUDE_SPLITTER_DATA_OPERATOR_TESTS")
val splitterConfigLoadMode = resolveSplitterConfigLoadMode()
val splitterTestProfile = usableLocalProperty(System.getProperty("splitter.test.profile"))
    ?: configValue("splitter.test.profile", "SPLITTER_TEST_PROFILE", defaultValue = "current")!!
// test.properties is authoritative for both IDEA and Gradle runs.
val activeTestEnv = optionalTestRuntimeProperty("env")
    ?: throw GradleException("Set env in src/test/resources/test.properties")
val splitterConfigKafkaStatusRequired =
    configValue("splitter.config.kafka.status.required", "SPLITTER_CONFIG_KAFKA_STATUS_REQUIRED")
        ?: optionalTestRuntimeProperty("splitter.config.kafka.status.required")
        ?: "false"
val allureResultsDirectory = generatedOutputDirectory(
    usableLocalProperty(System.getProperty("allure.results.directory"))
        ?: optionalConfigProperty("allure.results.directory")
        ?: "build/allure-results"
)
val splitterRegressionLogsDir = providers.provider {
    generatedOutputDirectory(
        configValue("splitter.regression.logs.dir", "SPLITTER_REGRESSION_LOGS_DIR")
            ?: "build/logs/splitter-regression"
    )
}

val splitterRuntimeSystemProperties = listOf(
    "splitter.config.kafka.env",
    "splitter.config.kafka.input.topic",
    "splitter.config.kafka.status.topic",
    "splitter.config.kafka.monitoring.topic",
    "splitter.config.kafka.status.required",
    "splitter.config.kafka.timeout.seconds",
    "splitter.config.kafka.unique.consumer.group.enabled",
    "splitter.config.kafka.consumer.group.prefix",
    "splitter.config.kafka.consumer.warmup.seconds",
    "splitter.kap.kafka.env",
    "splitter.kap.topic",
    "splitter.kap.monitoring.topic",
    "splitter.kap.timeout.seconds",
    "splitter.kap.monitoring.timeout.seconds",
    "splitter.precalc.monitoring.kafka.env",
    "splitter.precalc.monitoring.topic",
    "splitter.precalc.monitoring.timeout.seconds",
    "splitter.config.load.monitoring.kafka.env",
    "splitter.config.load.monitoring.topic",
    "splitter.config.load.monitoring.timeout.seconds",
    "splitter.kafka.consumer.available",
    "secure.placeholders.fail-on-unresolved"
)

fun propertyEnvName(propertyName: String): String =
    propertyName.replace('.', '_').replace('-', '_').uppercase(Locale.ROOT)

fun configuredRuntimeSystemProperty(propertyName: String): String? {
    val profiles = Properties()
    val profileFile = rootProject.file("src/test/resources/regression-profiles.properties")
    if (profileFile.isFile) profileFile.inputStream().use { profiles.load(it) }
    val scopedName = "${normalizedFileEnvironment()}.$propertyName"
    if (profiles.containsKey(scopedName)) {
        return profiles.getProperty(scopedName).trim()
    }
    return configValue(propertyName, propertyEnvName(propertyName))
        ?: optionalTestRuntimeProperty(propertyName)
}

val splitterRegressionLogLock = Any()
val splitterRegressionLogTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

fun splitterRegressionLogNow(): String =
    LocalDateTime.now().format(splitterRegressionLogTimestampFormatter)

fun appendSplitterRegressionLog(file: File, text: String) {
    synchronized(splitterRegressionLogLock) {
        file.parentFile.mkdirs()
        file.appendText(text, Charsets.UTF_8)
    }
}

fun throwableStackTrace(throwable: Throwable): String {
    val writer = StringWriter()
    throwable.printStackTrace(PrintWriter(writer))
    return writer.toString()
}

fun splitterRunLogPropertyValue(propertyName: String, mode: String): String =
    when (propertyName) {
        "env" -> activeTestEnv
        "splitter.config.load.mode" -> mode
        "splitter.test.profile" -> splitterTestProfile
        "splitter.config.kafka.status.required" -> splitterConfigKafkaStatusRequired
        "allure.results.directory" -> allureResultsDirectory.absolutePath
        "splitter.regression.logs.dir" -> splitterRegressionLogsDir.get().absolutePath
        "secure.placeholders.fail-on-unresolved" -> configuredRuntimeSystemProperty(propertyName) ?: "false"
        else -> configuredRuntimeSystemProperty(propertyName) ?: ""
    }

fun splitterRunLogHeader(mode: String, taskPath: String, logFile: File, runtimeProperties: Map<String, *> = emptyMap<String, Any>()): String {
    val propertyNames = (listOf(
        "env",
        "splitter.config.load.mode",
        "splitter.test.profile",
        "splitter.config.kafka.status.required",
        "allure.results.directory",
        "splitter.regression.logs.dir"
    ) + splitterRuntimeSystemProperties).distinct()
    val properties = propertyNames.joinToString(System.lineSeparator()) { propertyName ->
        "$propertyName=${runtimeProperties[propertyName] ?: splitterRunLogPropertyValue(propertyName, mode)}"
    }
    return """
        Splitter regression Gradle task log
        startedAt=${splitterRegressionLogNow()}
        task=$taskPath
        mode=$mode
        projectDir=${rootProject.projectDir.absolutePath}
        logFile=${logFile.absolutePath}
        gradle=${gradle.gradleVersion}
        java=${System.getProperty("java.version")}
        requestedTasks=${gradle.startParameter.taskNames.joinToString(",")}

        properties:
        $properties

        ${splitterConfigLoadModeNotice(mode)}
    """.trimIndent() + System.lineSeparator()
}

fun normalizedFileEnvironment(): String =
    when (val value = activeTestEnv.trim().lowercase(Locale.ROOT).replace('_', '-')) {
        "eift", "ift-ds", "eift-ds" -> "ift"
        "eift-dm" -> "ift-dm"
        "localhost" -> "local"
        "dev", "ift", "ift-dm", "lt", "local" -> value
        else -> throw GradleException("Unsupported env in src/test/resources/test.properties: $value")
    }

fun splitterConfigLoadModeNotice(mode: String): String {
    val apiConfigLoad = if (mode == "rest") "true" else "false"
    val flow = if (mode == "rest") "REST API" else "Kafka consumer"
    return """

        Splitter regression mode: $mode
        Required splitter config load flow: $flow
        Set config map/deployment flag before this run:
          MAPPER:    splitter.config.api-config-load=$apiConfigLoad
          REACTIONS: splitter.config.api-config-load=$apiConfigLoad
        Environment variable equivalent:
          MAPPER:    SPLITTER_CONFIG_API_CONFIG_LOAD=$apiConfigLoad
          REACTIONS: SPLITTER_CONFIG_API_CONFIG_LOAD=$apiConfigLoad

    """.trimIndent()
}


// Explicit cross-script contract; task actions remain lazy.
buildContext["testRuntimeProperties"] = testRuntimeProperties
buildContext["perfeccionistaVersion"] = perfeccionistaVersion
buildContext["platformvatframeworkVersion"] = platformvatframeworkVersion
buildContext["slf4jVersion"] = slf4jVersion
buildContext["awaitilityVersion"] = awaitilityVersion
buildContext["mockitoVersion"] = mockitoVersion
buildContext["useLocalLibs"] = useLocalLibs
buildContext["includeDisabledTests"] = includeDisabledTests
buildContext["includeManualTests"] = includeManualTests
buildContext["includeSplitterDataOperatorTests"] = includeSplitterDataOperatorTests
buildContext["splitterConfigLoadMode"] = splitterConfigLoadMode
buildContext["splitterTestProfile"] = splitterTestProfile
buildContext["activeTestEnv"] = activeTestEnv
buildContext["splitterConfigKafkaStatusRequired"] = splitterConfigKafkaStatusRequired
buildContext["localLibJars"] = localLibJars
buildContext["allureResultsDirectory"] = allureResultsDirectory
buildContext["splitterRegressionLogsDir"] = splitterRegressionLogsDir
buildContext["splitterRuntimeSystemProperties"] = splitterRuntimeSystemProperties
buildContext["optionalLocalProperty"] = { name: String -> optionalLocalProperty(name) }
buildContext["optionalTestRuntimeProperty"] = { name: String -> optionalTestRuntimeProperty(name) }
buildContext["configuredRuntimeSystemProperty"] = { name: String -> configuredRuntimeSystemProperty(name) }
buildContext["fileFromProjectOrAbsolute"] = { path: String -> fileFromProjectOrAbsolute(path) }
buildContext["normalizedFileEnvironment"] = {  -> normalizedFileEnvironment() }
buildContext["splitterRegressionLogNow"] = {  -> splitterRegressionLogNow() }
buildContext["propertyEnvName"] = { name: String -> propertyEnvName(name) }
buildContext["throwableStackTrace"] = { throwable: Throwable -> throwableStackTrace(throwable) }
buildContext["splitterConfigLoadModeNotice"] = { mode: String -> splitterConfigLoadModeNotice(mode) }
buildContext["appendSplitterRegressionLog"] = { file: File, text: String -> appendSplitterRegressionLog(file, text) }
buildContext["splitterRunLogHeader"] = { mode: String, taskPath: String, logFile: File, runtimeProperties: Map<String, *> -> splitterRunLogHeader(mode, taskPath, logFile, runtimeProperties) }
buildContext["configValue"] = { name: String, envNames: Array<out String>, defaultValue: String? -> configValue(name, *envNames, defaultValue = defaultValue) }
buildContext["configFlag"] = { name: String, envNames: Array<out String>, defaultValue: Boolean -> configFlag(name, *envNames, defaultValue = defaultValue) }
