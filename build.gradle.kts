import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestOutputListener
import org.gradle.api.tasks.testing.TestResult
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties

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
listOf(
    rootProject.file("secure.local.properties"),
    rootProject.file("secure.local.override.properties")
).filter { it.isFile }
    .forEach { file -> file.inputStream().use { secureLocalProperties.load(it) } }

fun usableLocalProperty(value: String?): String? =
    value?.trim()
        ?.takeUnless { it.isBlank() }
        ?.takeUnless { it.startsWith("<SET_ME_") && it.endsWith(">") }

fun optionalLocalProperty(name: String): String? =
    usableLocalProperty(project.findProperty(name) as String?)
        ?: usableLocalProperty(gradleLocalProperties.getProperty(name))
        ?: usableLocalProperty(secureLocalProperties.getProperty(name))
        ?: usableLocalProperty(System.getenv(name))

fun optionalEnv(name: String): String? =
    usableLocalProperty(System.getenv(name))

fun optionalConfigProperty(name: String): String? =
    optionalLocalProperty(name)

fun optionalTestRuntimeProperty(name: String): String? =
    usableLocalProperty(testRuntimeProperties.getProperty(name))

fun configValue(name: String, vararg envNames: String, defaultValue: String? = null): String? =
    usableLocalProperty(System.getProperty(name))
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
    val mode = raw.trim().replace('-', '_').toLowerCase()
    if (mode != "rest" && mode != "kafka") {
        throw GradleException("Unsupported splitter.config.load.mode=$raw. Expected one of: rest, kafka")
    }
    return mode
}

fun fileFromProjectOrAbsolute(pathValue: String): File {
    val candidate = File(pathValue)
    return if (candidate.isAbsolute) candidate else rootProject.file(pathValue)
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
val activeTestEnv = configValue("env", "ENV")
    ?: optionalTestRuntimeProperty("env")
    ?: "ift"
val splitterConfigKafkaStatusRequired =
    configValue("splitter.config.kafka.status.required", "SPLITTER_CONFIG_KAFKA_STATUS_REQUIRED")
        ?: optionalTestRuntimeProperty("splitter.config.kafka.status.required")
        ?: "false"
val defaultAllureResultsDirectoryPath = when {
    isAnyGradleTaskRequested("splitterRestRegression", "splitterKafkaRegression") ->
        "build/allure-results-splitter-rest-kafka"
    isAnyGradleTaskRequested("splitterRestDebug", "splitterKafkaDebug") ->
        "build/allure-results-splitter-debug"
    else ->
        "build/allure-results"
}
val allureResultsDirectory = fileFromProjectOrAbsolute(
    usableLocalProperty(System.getProperty("allure.results.directory"))
        ?: optionalConfigProperty("allure.results.directory")
        ?: defaultAllureResultsDirectoryPath
)
val splitterRegressionLogsDir = providers.provider {
    fileFromProjectOrAbsolute(
        configValue("splitter.regression.logs.dir", "SPLITTER_REGRESSION_LOGS_DIR")
            ?: "build/logs/splitter-regression"
    )
}
splitterRegressionLogsDir.get().mkdirs()

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
    propertyName.replace('.', '_').replace('-', '_').uppercase()

fun configuredRuntimeSystemProperty(propertyName: String): String? =
    configValue(propertyName, propertyEnvName(propertyName))
        ?: optionalTestRuntimeProperty(propertyName)

fun defaultSplitterKafkaEnv(testEnv: String): String =
    when (testEnv.trim().replace('-', '_').toLowerCase()) {
        "dev" -> "splitter_dev"
        "ift", "ift_ds", "ift_dm", "eift", "eift_ds", "eift_dm" -> "splitter_ift"
        else -> testEnv
    }

fun defaultSplitterRuntimeSystemProperty(propertyName: String): String? {
    val splitterKafkaEnv = defaultSplitterKafkaEnv(activeTestEnv)
    return when (propertyName) {
        "splitter.config.kafka.env" -> splitterKafkaEnv
        "splitter.config.kafka.input.topic" -> "splitting-config-created"
        "splitter.config.kafka.status.topic" -> "splitting-config-requested-and-received"
        "splitter.config.kafka.monitoring.topic" -> "omon_explab_splitter_log"
        "splitter.config.kafka.status.required" -> splitterConfigKafkaStatusRequired
        "splitter.config.kafka.unique.consumer.group.enabled" -> "true"
        "splitter.config.kafka.consumer.group.prefix" -> "integration-test-splitter-config-load"
        "splitter.config.kafka.consumer.warmup.seconds" -> "0"
        "splitter.kap.kafka.env" -> splitterKafkaEnv
        "splitter.kap.topic" -> "explab-splitting-result"
        "splitter.kap.monitoring.topic" -> "omon_explab_splitter_log"
        "splitter.precalc.monitoring.kafka.env" -> splitterKafkaEnv
        "splitter.precalc.monitoring.topic" -> "omon_explab_splitter_log"
        "splitter.config.load.monitoring.kafka.env" -> splitterKafkaEnv
        "splitter.config.load.monitoring.topic" -> "omon_explab_splitter_log"
        else -> null
    }
}

fun resolvedRuntimeSystemProperty(propertyName: String): String? =
    configuredRuntimeSystemProperty(propertyName) ?: defaultSplitterRuntimeSystemProperty(propertyName)

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
        else -> resolvedRuntimeSystemProperty(propertyName) ?: ""
    }

fun splitterRunLogHeader(mode: String, taskPath: String, logFile: File): String {
    val propertyNames = (listOf(
        "env",
        "splitter.config.load.mode",
        "splitter.test.profile",
        "splitter.config.kafka.status.required",
        "allure.results.directory",
        "splitter.regression.logs.dir"
    ) + splitterRuntimeSystemProperties).distinct()
    val properties = propertyNames.joinToString(System.lineSeparator()) { propertyName ->
        "$propertyName=${splitterRunLogPropertyValue(propertyName, mode)}"
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

fun resolveAllureUploadResultsDirectory(): File =
    fileFromProjectOrAbsolute(
        configValue(
            "allureResultsDir",
            "ALLURE_RESULTS_DIR",
            "ALLURE_RESULTS",
            "ALLURE_RESULTS_DIRECTORY"
        )
            ?: usableLocalProperty(System.getProperty("allure.results.directory"))
            ?: optionalConfigProperty("allure.results.directory")
            ?: "build/allure-results"
    )

plugins {
    java
    id("io.qameta.allure") version "2.11.2"
}

// Задаем координаты проекта - группу и версию
group = "ru.sber.qa.examples"
version = "0.0.2"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// Получаем ссылки на репозитории из файла проекта gradle.properties
val nexusPublicRepository: String by rootProject
val nexusInternalRepository: String by rootProject

// Получаем значения tokenName и tokenPassword из ~/.gradle/gradle.properties,
// -P параметров, ignored gradle.local.properties или legacy-полей проекта.
val tokenName = optionalLocalProperty("tokenName")
    ?: optionalLocalProperty("nexusUserSigma")
val tokenPassword = optionalLocalProperty("tokenPassword")
    ?: optionalLocalProperty("nexusPasswordSigma")

repositories {
    listOf(nexusPublicRepository, nexusInternalRepository).forEach {
        maven {
            url = uri(it)
            isAllowInsecureProtocol = true
            credentials {
                username = tokenName ?: ""
                password = tokenPassword ?: ""
            }
        }
    }
    mavenCentral()
}

dependencies {
    if (useLocalLibs) {
        implementation(localLibJars)
    } else {
        // Подключаем зависимость для работы с JUnit5
        implementation(group = "io.perfeccionista.framework", name = "environment-junit5", version = "$perfeccionistaVersion")
        // api - модуль для работы с REST_API
        implementation(group = "ru.sber.qa.platform-v-at-framework", name = "api", version = "$platformvatframeworkVersion")
        // database - модуль для работы с Базами данных
        implementation(group = "ru.sber.qa.platform-v-at-framework", name = "database", version = "$platformvatframeworkVersion")
        // kafka - модуль для работы Kafka
        implementation(group = "ru.sber.qa.platform-v-at-framework", name = "kafka", version = "$platformvatframeworkVersion")
        // session - модуль для работы с сессиями
        implementation(group = "ru.sber.qa.platform-v-at-framework", name = "session", version = "$platformvatframeworkVersion")
        //container - модуль для работы с OpenShift или Kubernetes
        implementation(group = "ru.sber.qa.platform-v-at-framework", name = "containers", version = "$platformvatframeworkVersion")

        //allure2 - модуль для работы с Allure2
        implementation(group = "ru.sber.qa.platform-v-at-framework", name = "allure2", version = "$platformvatframeworkVersion")
    }

    // database - клиент postgresql
    implementation(group = "org.postgresql", name = "postgresql", version = "42.7.7")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.17.2")
    implementation("com.fasterxml.jackson.core:jackson-core:2.17.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("io.qameta.allure:allure-java-commons:2.29.0")
    implementation("io.qameta.allure:allure-rest-assured:2.29.0")
    implementation("org.apache.httpcomponents:httpcore:4.4.16")
    implementation("org.apache.httpcomponents:httpclient:4.5.14")
    implementation("org.apache.httpcomponents:httpmime:4.5.14")
    implementation("org.apache.commons:commons-lang3:3.14.0")
    compileOnly("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2")
    implementation("org.apache.kafka:kafka-clients:3.7.1")
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.10.0.202406032230-r")
    implementation("org.apache.groovy:groovy:4.0.22")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")

    // Подключаем логирование для проекта
    implementation(group = "org.slf4j", name = "slf4j-simple", version = "$slf4jVersion")

    implementation(group = "org.awaitility", name = "awaitility", version = "$awaitilityVersion")

    implementation(group = "org.mockito", name = "mockito-junit-jupiter", version = "$mockitoVersion")
    implementation(group = "org.mockito", name = "mockito-inline", version = "$mockitoVersion")

    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

    testCompileOnly("org.projectlombok:lombok:1.18.30")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.30")
    if (!useLocalLibs) {
        implementation(group = "ru.sber.qa.platform-v-at-framework", name = "sbermock", version = "$platformvatframeworkVersion")
    }

}


// Технические source set'ы для формирования чистого отчета и registration-only bypass.
val bypassToolSourceSet = sourceSets.create("bypassTool") {
    java.srcDir("src/bypassTool/java")
}

val generatedBypassSourcesDir = layout.buildDirectory.dir("generated/sources/bypassTests/java")
val bypassTestsSourceSet = sourceSets.create("bypassTests") {
    java.srcDir("src/bypassTests/java")
    java.srcDir(generatedBypassSourcesDir.get().asFile)
    resources.srcDir("src/test/resources")
    compileClasspath += sourceSets.getByName("main").output + configurations.getByName("testCompileClasspath")
    runtimeClasspath += output + compileClasspath + configurations.getByName("testRuntimeClasspath")
}

configurations.named(bypassTestsSourceSet.implementationConfigurationName) {
    extendsFrom(configurations.getByName("testImplementation"))
}
configurations.named(bypassTestsSourceSet.runtimeOnlyConfigurationName) {
    extendsFrom(configurations.getByName("testRuntimeOnly"))
}

val reportEligibilityOutputDir = layout.buildDirectory.dir("report-eligibility")
val reportExclusionsFile = reportEligibilityOutputDir.map { it.file("excluded-tests.txt") }
val reportExclusionsWithDisabledIncludedFile =
    reportEligibilityOutputDir.map { it.file("excluded-tests-include-disabled.txt") }
val reportSplitterConfigLoadModeExclusionsFile =
    reportEligibilityOutputDir.map { it.file("excluded-tests-splitter-config-load-mode.txt") }

val auditReportingTags by tasks.registering {
    group = "verification"
    description = "Fail when legacy JUnit/Allure tags are added to test sources"
    inputs.dir("src/test/java")
    doLast {
        val violations = fileTree("src/test/java") {
            include("**/*.java")
        }.filter { source ->
            val text = source.readText()
            text.contains("@Tag(") || text.contains("Allure.label(\"tag\"")
        }.files.sortedBy { it.path }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Legacy reporting tags are forbidden. Use @CriticalRegression, @Regression or @ManualTest; " +
                        "task/service/automated tags are generated centrally. Violations: " +
                        violations.joinToString { it.relativeTo(projectDir).path }
            )
        }
    }
}

val generateReportEligibility by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Find disabled, assertion-less and explicitly outdated tests before Allure discovery"
    dependsOn(auditReportingTags)
    dependsOn(tasks.named(bypassToolSourceSet.classesTaskName))
    classpath = bypassToolSourceSet.output
    mainClass.set("ru.sber.qa.tools.reporting.TestReportEligibilityScanner")
    systemProperty("splitter.config.load.mode", splitterConfigLoadMode)
    systemProperty("splitter.test.profile", splitterTestProfile)
    systemProperty("splitter.config.kafka.status.required", splitterConfigKafkaStatusRequired)
    systemProperty("includeManualTests", includeManualTests.toString())
    systemProperty("includeSplitterDataOperatorTests", includeSplitterDataOperatorTests.toString())
    systemProperty("env", activeTestEnv)
    splitterRuntimeSystemProperties.forEach { propertyName ->
        resolvedRuntimeSystemProperty(propertyName)?.let { systemProperty(propertyName, it) }
    }
    System.getProperties().stringPropertyNames()
        .filter { it.startsWith("SECURE_") || it.startsWith("splitter.kap.") }
        .forEach { systemProperty(it, System.getProperty(it)) }
    secureLocalProperties.stringPropertyNames()
        .mapNotNull { name -> optionalLocalProperty(name)?.let { name to it } }
        .filter { (name, _) -> name.startsWith("SECURE_") }
        .forEach { (name, value) -> systemProperty(name, value) }
    args(
        file("src/test/java").absolutePath,
        file("config/reporting/outdated-tests.properties").absolutePath,
        reportEligibilityOutputDir.get().asFile.absolutePath
    )
    inputs.dir("src/test/java")
    inputs.file("config/reporting/outdated-tests.properties")
    inputs.file("src/test/resources/test.properties")
    inputs.file("src/test/resources/kafka-consumers.properties")
    inputs.property("splitter.config.load.mode", splitterConfigLoadMode)
    inputs.property("splitter.test.profile", splitterTestProfile)
    inputs.property("splitter.config.kafka.status.required", splitterConfigKafkaStatusRequired)
    inputs.property("env", activeTestEnv)
    splitterRuntimeSystemProperties.forEach { propertyName ->
        inputs.property(propertyName, resolvedRuntimeSystemProperty(propertyName) ?: "")
    }
    inputs.property("includeManualTests", includeManualTests)
    inputs.property("includeSplitterDataOperatorTests", includeSplitterDataOperatorTests)
    outputs.dir(reportEligibilityOutputDir)
    outputs.upToDateWhen { false }
}

val generateBypassTests by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Generate registration-only tests only for report-eligible functional tests"
    dependsOn(generateReportEligibility)
    classpath = bypassToolSourceSet.output
    mainClass.set("ru.sber.qa.tools.bypass.BypassTestsGenerator")
    args(
        file("src/test/java").absolutePath,
        generatedBypassSourcesDir.get().asFile.absolutePath,
        reportExclusionsFile.get().asFile.absolutePath
    )
    inputs.dir("src/test/java")
    inputs.file(reportExclusionsFile)
    outputs.dir(generatedBypassSourcesDir)
}

tasks.named(bypassTestsSourceSet.compileJavaTaskName) {
    dependsOn(generateBypassTests)
}

val bypassTests by tasks.registering(Test::class) {
    group = "verification"
    description = "Create passed TestOps registration results without executing functional logic"
    dependsOn(tasks.named(bypassTestsSourceSet.classesTaskName))
    testClassesDirs = bypassTestsSourceSet.output.classesDirs
    classpath = bypassTestsSourceSet.runtimeClasspath
    useJUnitPlatform()
    filter.setFailOnNoMatchingTests(false)
    testLogging.showStandardStreams = true
}

val prepareSplitterRegressionLogs by tasks.registering {
    group = "verification"
    description = "Create directory for splitter regression Gradle task logs"
    outputs.dir(splitterRegressionLogsDir)
    doLast {
        val directory = splitterRegressionLogsDir.get()
        if (!directory.mkdirs() && !directory.isDirectory) {
            throw GradleException("Cannot create splitter regression logs directory: ${directory.absolutePath}")
        }
        logger.lifecycle("Splitter regression logs directory: ${directory.absolutePath}")
    }
}

fun Test.applyReportEligibilityExclusions() {
    dependsOn(generateReportEligibility)
    filter.setFailOnNoMatchingTests(false)
    doFirst {
        if (includeDisabledTests) {
            systemProperty("junit.jupiter.conditions.deactivate", "org.junit.*DisabledCondition")
        }

        val exclusions = if (includeDisabledTests) {
            reportExclusionsWithDisabledIncludedFile.get().asFile
        } else {
            reportExclusionsFile.get().asFile
        }
        if (exclusions.exists()) {
            exclusions.readLines()
                .map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .forEach { filter.excludeTestsMatching(it) }
        }
    }
}

// Functional reports must contain only eligible tests. Exclusions are applied before test discovery,
// therefore disabled/no-assertion/outdated cases do not become skipped entries in Allure.
tasks.named<Test>("test") {
    applyReportEligibilityExclusions()
}

fun splitterConfigLoadModeNotice(mode: String): String {
    val apiConfigLoad = if (mode == "rest") "true" else "false"
    val flow = if (mode == "rest") "REST API" else "Kafka consumer"
    val configLoadHint = if (mode == "kafka") {
        """
        Config-load Kafka topics used by regression:
          input:      splitting-config-created
          monitoring: omon_explab_splitter_log
        Runtime split topics from splitter config-maps are not config-load confirmation topics:
          MAPPER:    splitting_request -> splitting_response
          REACTIONS: splitting_request_reactions -> splitting_response_reactions
        """.trimIndent()
    } else {
        "Config-load Kafka topics are not used by REST config-load regression."
    }
    return """

        Splitter regression mode: $mode
        Required splitter config load flow: $flow
        Set config map/deployment flag before this run:
          MAPPER:    SPLITTER_API_CONFIG_LOAD=$apiConfigLoad
          REACTIONS: SPLITTER_API_CONFIG_LOAD=$apiConfigLoad
        Environment variable equivalent:
          MAPPER:    SPLITTER_API_CONFIG_LOAD=$apiConfigLoad
          REACTIONS: SPLITTER_API_CONFIG_LOAD=$apiConfigLoad
        $configLoadHint

    """.trimIndent()
}

fun Test.configureSplitterRegressionTask(
    mode: String,
    includePattern: String = "ru.sber.qa.splitter.*",
    logFileName: String = "splitter-$mode-run.log"
) {
    val runLogFileProvider = splitterRegressionLogsDir.map { File(it, logFileName) }

    group = "verification"
    dependsOn(tasks.named("testClasses"))
    dependsOn(prepareSplitterRegressionLogs)
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
        logFile.writeText(splitterRunLogHeader(mode, path, logFile), Charsets.UTF_8)
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

val splitterRestRegression by tasks.registering(Test::class) {
    description = "Run report-eligible splitter tests with REST config load flow"
    configureSplitterRegressionTask("rest")
}

val splitterKafkaRegression by tasks.registering(Test::class) {
    description = "Run report-eligible splitter tests with Kafka config load flow"
    configureSplitterRegressionTask("kafka")
}

val splitterRestDebug by tasks.registering(Test::class) {
    description = "Run CFG-01 splitter scenario with REST config load flow"
    configureSplitterRegressionTask(
        mode = "rest",
        includePattern = "ru.sber.qa.splitter.SplitterFunctionalPlanTest_flow.configShouldBeLoaded",
        logFileName = "splitter-rest-debug-run.log"
    )
}

val splitterKafkaDebug by tasks.registering(Test::class) {
    description = "Run CFG-01 splitter scenario with Kafka config load flow"
    configureSplitterRegressionTask(
        mode = "kafka",
        includePattern = "ru.sber.qa.splitter.SplitterFunctionalPlanTest_flow.configShouldBeLoaded",
        logFileName = "splitter-kafka-debug-run.log"
    )
}

// Настраиваем Allure-plugin для локальных отчетов
allure {
    // Версия генератора отчетов
    report {
        // Версия должна быть той же, что тянется транзитивно из фреймворка
        version.set("2.30.0")
    }

    adapter {
        aspectjWeaver.set(false)
        frameworks {
            junit5 {
                // Версия должна быть той же, что тянется транзитивно из фреймворка
                adapterVersion.set("2.29.0")
            }
        }
    }
}

tasks.register("copyAllureCategories") {
    doLast {
        val categoriesFile = file("${projectDir}/allure/categories.json")
        if (!categoriesFile.isFile) {
            return@doLast
        }

        val destinations = linkedSetOf(allureResultsDirectory)
        if (isTestOpsUploadRequested()) {
            destinations.add(resolveAllureUploadResultsDirectory())
        }

        destinations.forEach { destination ->
            project.copy {
                from(categoriesFile)
                into(destination)
            }
        }
    }
}

val splitterRestKafkaResultsDir = rootProject.file("build/allure-results-splitter-rest-kafka")
val splitterRestKafkaReportDir = rootProject.file("build/reports/allure-report/splitter-rest-kafka-regression")
val defaultAllureResultsDir = rootProject.file("build/allure-results")
val allureSplitterModePattern =
    Regex("\\\"name\\\"\\s*:\\s*\\\"(?:splitterConfigLoadMode|splitter\\.config\\.load\\.mode)\\\"\\s*,\\s*\\\"value\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
val allureContainerChildrenPattern = Regex("\\\"children\\\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL)
val allureUuidPattern =
    Regex("\\\"([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\\\"")

fun countJUnitXmlTestCases(testResultsDir: File): Int {
    if (!testResultsDir.isDirectory) {
        return 0
    }
    return testResultsDir.walkTopDown()
        .filter { it.isFile && it.extension.equals("xml", ignoreCase = true) }
        .sumOf { file ->
            Regex("<testcase\\b").findAll(file.readText(Charsets.UTF_8)).count()
        }
}

fun splitterConfigLoadModeFromAllureResult(resultFile: File): String {
    val content = resultFile.readText(Charsets.UTF_8)
    return allureSplitterModePattern.find(content)
        ?.groupValues
        ?.get(1)
        ?.trim()
        ?.replace('-', '_')
        ?.toLowerCase()
        ?: "unknown"
}

fun countAllureResultsBySplitterMode(resultsDir: File): Map<String, Int> {
    val counts = linkedMapOf<String, Int>()
    resultsDir.listFiles()
        ?.asSequence()
        ?.filter { it.isFile && it.name.endsWith("-result.json") }
        ?.forEach { resultFile ->
            val mode = splitterConfigLoadModeFromAllureResult(resultFile)
            counts[mode] = (counts[mode] ?: 0) + 1
        }
    return counts
}

fun missingAllureChildResultUuids(resultsDir: File): Set<String> {
    val resultUuids = resultsDir.listFiles()
        ?.asSequence()
        ?.filter { it.isFile && it.name.endsWith("-result.json") }
        ?.map { it.name.removeSuffix("-result.json") }
        ?.toSet()
        ?: emptySet()
    val missing = linkedSetOf<String>()
    resultsDir.listFiles()
        ?.asSequence()
        ?.filter { it.isFile && it.name.endsWith("-container.json") }
        ?.forEach { containerFile ->
            val content = containerFile.readText(Charsets.UTF_8)
            allureContainerChildrenPattern.findAll(content).forEach { childrenMatch ->
                allureUuidPattern.findAll(childrenMatch.groupValues[1]).forEach { uuidMatch ->
                    val uuid = uuidMatch.groupValues[1]
                    if (uuid !in resultUuids) {
                        missing.add(uuid)
                    }
                }
            }
        }
    return missing
}

val validateSplitterRestKafkaAllureResults by tasks.registering {
    group = "verification"
    description = "Validate combined splitter REST+Kafka Allure raw results before report/TestOps upload"
    doLast {
        if (!splitterRestKafkaResultsDir.isDirectory) {
            throw GradleException(
                "Splitter REST+Kafka Allure results directory not found: ${splitterRestKafkaResultsDir.absolutePath}. " +
                        "Run splitterRestRegression and splitterKafkaRegression first."
            )
        }

        val expectedByMode = linkedMapOf(
            "rest" to countJUnitXmlTestCases(rootProject.file("build/test-results/splitterRestRegression")),
            "kafka" to countJUnitXmlTestCases(rootProject.file("build/test-results/splitterKafkaRegression"))
        )
        val actualByMode = countAllureResultsBySplitterMode(splitterRestKafkaResultsDir)
        val problems = mutableListOf<String>()

        expectedByMode.forEach { (mode, expected) ->
            val actual = actualByMode[mode] ?: 0
            if (expected == 0) {
                problems.add("JUnit XML for splitter $mode regression is empty or missing")
            } else if (actual < expected) {
                problems.add("Allure $mode result count is $actual, below latest JUnit testcase count $expected")
            }
        }

        val missingChildUuids = missingAllureChildResultUuids(splitterRestKafkaResultsDir)
        if (missingChildUuids.isNotEmpty()) {
            problems.add(
                "Allure containers reference ${missingChildUuids.size} missing test result UUID(s): " +
                        missingChildUuids.take(10).joinToString(", ")
            )
        }

        if (problems.isNotEmpty()) {
            throw GradleException(
                "Splitter REST+Kafka Allure results are incomplete:${System.lineSeparator()}" +
                        problems.joinToString(System.lineSeparator()) { "- $it" }
            )
        }

        println(
            "Validated splitter REST+Kafka Allure results: " +
                    "JUnit=$expectedByMode, Allure=$actualByMode, dir=${splitterRestKafkaResultsDir.absolutePath}"
        )
    }
}

val splitterRestKafkaAllureReport by tasks.registering {
    group = "verification"
    description = "Generate a combined Allure HTML report from REST+Kafka splitter results"
    dependsOn("downloadAllure")
    dependsOn(validateSplitterRestKafkaAllureResults)
    doLast {
        if (!splitterRestKafkaResultsDir.isDirectory) {
            throw GradleException(
                "Splitter REST+Kafka Allure results directory not found: ${splitterRestKafkaResultsDir.absolutePath}. " +
                        "Run splitterRestRegression and splitterKafkaRegression first."
            )
        }
        val allureExecutable = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            rootProject.file("build/allure/commandline/bin/allure.bat")
        } else {
            rootProject.file("build/allure/commandline/bin/allure")
        }
        if (!allureExecutable.isFile) {
            throw GradleException("Allure executable not found after downloadAllure: ${allureExecutable.absolutePath}")
        }
        project.exec {
            executable = allureExecutable.absolutePath
            args(
                "generate",
                splitterRestKafkaResultsDir.absolutePath,
                "--clean",
                "-o",
                splitterRestKafkaReportDir.absolutePath
            )
        }
        println("Splitter REST+Kafka Allure report: ${splitterRestKafkaReportDir.resolve("index.html").absolutePath}")
    }
}

val prepareSplitterRestKafkaAllureResultsForTestOps by tasks.registering {
    group = "verification"
    description = "Copy combined REST+Kafka splitter Allure results to build/allure-results for TestOps upload"
    dependsOn(validateSplitterRestKafkaAllureResults)
    doLast {
        if (!splitterRestKafkaResultsDir.isDirectory) {
            throw GradleException(
                "Splitter REST+Kafka Allure results directory not found: ${splitterRestKafkaResultsDir.absolutePath}. " +
                        "Run splitterRestRegression and splitterKafkaRegression first."
            )
        }
        delete(defaultAllureResultsDir)
        defaultAllureResultsDir.mkdirs()
        project.copy {
            from(splitterRestKafkaResultsDir)
            into(defaultAllureResultsDir)
        }
        val categoriesFile = rootProject.file("allure/categories.json")
        if (categoriesFile.isFile) {
            project.copy {
                from(categoriesFile)
                into(defaultAllureResultsDir)
            }
        }
        val resultCount = defaultAllureResultsDir.listFiles()
            ?.count { it.isFile && it.name.endsWith("-result.json") }
            ?: 0
        if (resultCount == 0) {
            throw GradleException("No *-result.json files copied to ${defaultAllureResultsDir.absolutePath}.")
        }
        println("Prepared $resultCount splitter REST+Kafka Allure result(s) for TestOps: ${defaultAllureResultsDir.absolutePath}")
    }
}

val splitterRestKafkaTestOpsUpload by tasks.registering {
    group = "verification"
    description = "Prepare combined splitter REST+Kafka results and upload them to Allure TestOps"
    dependsOn(prepareSplitterRestKafkaAllureResultsForTestOps)
    finalizedBy("testOpsUpload")
}

fun isRequestedTask(taskName: String): Boolean =
    gradle.startParameter.taskNames.any { requested ->
        requested == taskName || requested == ":$taskName" || requested.endsWith(":$taskName")
    }

fun isTestOpsUploadRequested(): Boolean =
    configFlag("allureUploadEnabled", "ALLURE_UPLOAD_ENABLED") ||
            isRequestedTask("testOpsUpload") ||
            isRequestedTask("splitterRestKafkaTestOpsUpload") ||
            isRequestedTask("testAndUploadToTestOps") ||
            isRequestedTask("bypassTestsAndUploadToTestOps")

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
    description = "Validate Allure TestOps upload settings before running tests"
    onlyIf {
        isTestOpsUploadRequested()
    }

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
                "ALLURE_TOKEN is not set. Export it before running TestOps upload tasks."
            )

        println("allurectl  : ${resolveAllurectlExecutable()}")
    }
}

val testOpsUpload by tasks.registering {
    group = "verification"
    description = "Upload existing Allure results to Allure TestOps via allurectl"
    dependsOn(validateTestOpsUploadConfig)
    dependsOn("copyAllureCategories")
    onlyIf {
        isTestOpsUploadRequested()
    }

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
        val resultsDir = resolveAllureUploadResultsDirectory()
        val uploadBatch = parsePositiveInt(
            configValue(
                "allureUploadBatch",
                "ALLURE_UPLOAD_BATCH",
                "ALLURE_IMPORT_BATCH",
                defaultValue = "100"
            )!!,
            "allureUploadBatch/ALLURE_UPLOAD_BATCH"
        )
        val launchName = testOpsLaunchName()
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
                        "Run test/bypassTests/splitter regression first, " +
                        "or set allureResultsDir/ALLURE_RESULTS_DIR/allure.results.directory."
            )
        }

        val allResultFiles = resultsDir.listFiles()
            ?.filter { it.isFile }
            .orEmpty()
        val testResultFiles = allResultFiles.filter { it.name.endsWith("-result.json") }
        if (testResultFiles.isEmpty()) {
            throw GradleException("No *-result.json files found in ${resultsDir.absolutePath}.")
        }

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
                "ALLURE_TOKEN is not set. Export it as an environment variable before uploading to TestOps."
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

val testAndUploadToTestOps by tasks.registering {
    group = "verification"
    description = "Run functional tests and upload produced Allure results to TestOps"
    dependsOn(tasks.named("test"))
}

val bypassTestsAndUploadToTestOps by tasks.registering {
    group = "verification"
    description = "Run TestOps registration-only bypass tests and upload produced Allure results"
    dependsOn(bypassTests)
}

tasks.named<Test>("test") {
    dependsOn(validateTestOpsUploadConfig)
    finalizedBy(testOpsUpload)
}

bypassTests.configure {
    dependsOn(validateTestOpsUploadConfig)
    finalizedBy(testOpsUpload)
}

tasks {
    // Для компиляции ставим кодировку UTF-8
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
    // Для тестов внутри проекта используем платформу JUnit
    withType<Test> {
        useJUnitPlatform()
        systemProperty("file.encoding", "UTF-8")
        systemProperty("splitter.config.load.mode", splitterConfigLoadMode)
        systemProperty("splitter.test.profile", splitterTestProfile)
        systemProperty("splitter.config.kafka.status.required", splitterConfigKafkaStatusRequired)
        systemProperty("env", activeTestEnv)
        systemProperty("allure.results.directory", allureResultsDirectory.absolutePath)
        systemProperty("report.outdated.tests.file", file("config/reporting/outdated-tests.properties").absolutePath)
        val reportExclusions = if (includeDisabledTests) {
            reportExclusionsWithDisabledIncludedFile
        } else {
            reportExclusionsFile
        }
        systemProperty("report.exclusions.file", reportExclusions.get().asFile.absolutePath)
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
            "encryption.password",
            "EXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED",
            "exlab2696.running.cache.wait.timeout.ms",
            "exlab2696.running.cache.wait.poll.ms",
            "exlab2930.processor.timeout.seconds",
            "exlab2930.processor.stability.seconds"
        )
        System.getProperties().stringPropertyNames()
            .filter {
                it in forwardedTestSystemProperties ||
                        it.startsWith("SECURE_") ||
                        it.startsWith("kafka_") ||
                        it.startsWith("rest.") ||
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
            resolvedRuntimeSystemProperty(propertyName)?.let { systemProperty(propertyName, it) }
        }
        secureLocalProperties.stringPropertyNames()
            .mapNotNull { name -> optionalLocalProperty(name)?.let { name to it } }
            .forEach { (name, value) -> systemProperty(name, value) }
        finalizedBy("copyAllureCategories")
    }
}
