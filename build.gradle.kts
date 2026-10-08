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
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties
import java.util.UUID

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
// test.properties is authoritative for both IDEA and Gradle runs.
val activeTestEnv = optionalTestRuntimeProperty("env")
    ?: throw GradleException("Set env in src/test/resources/test.properties")
val splitterConfigKafkaStatusRequired =
    configValue("splitter.config.kafka.status.required", "SPLITTER_CONFIG_KAFKA_STATUS_REQUIRED")
        ?: optionalTestRuntimeProperty("splitter.config.kafka.status.required")
        ?: "false"
val allureResultsDirectory = fileFromProjectOrAbsolute(
    usableLocalProperty(System.getProperty("allure.results.directory"))
        ?: optionalConfigProperty("allure.results.directory")
        ?: "build/allure-results"
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
    when (val value = activeTestEnv.trim().lowercase().replace('_', '-')) {
        "eift", "ift-ds", "eift-ds" -> "ift"
        "eift-dm" -> "ift-dm"
        "localhost" -> "local"
        "dev", "ift", "ift-dm", "lt", "local" -> value
        else -> throw GradleException("Unsupported env in src/test/resources/test.properties: $value")
    }

fun resolveAllureUploadResultsDirectory(): File =
    rootProject.file("testops-results/${normalizedFileEnvironment()}/allure-results")

fun resolveRegressionUploadResultsDirectory(): File =
    rootProject.file("regression-results/${normalizedFileEnvironment()}/testops")

plugins {
    java
    id("io.qameta.allure") version "2.11.2" apply false
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
    testImplementation("io.qameta.allure:allure-junit5:2.29.0")
    if (!useLocalLibs) {
        implementation("io.fabric8:kubernetes-client:6.13.4")
        runtimeOnly("io.fabric8:kubernetes-httpclient-okhttp:6.13.4")
    }
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
    compileClasspath += sourceSets.getByName("main").output + configurations.getByName("testCompileClasspath")
    runtimeClasspath += output + compileClasspath + configurations.getByName("testRuntimeClasspath")
}

val generatedBypassSourcesDir = layout.buildDirectory.dir("generated/sources/bypassTests/java")
val bypassTestsSourceSet = sourceSets.create("bypassTests") {
    java.srcDir("src/bypassTests/java")
    java.srcDir(generatedBypassSourcesDir.get().asFile)
    resources.srcDir("src/test/resources")
    compileClasspath += sourceSets.getByName("main").output + configurations.getByName("testCompileClasspath")
    runtimeClasspath += output + compileClasspath + configurations.getByName("testRuntimeClasspath")
}

configurations.named(bypassToolSourceSet.implementationConfigurationName) {
    extendsFrom(configurations.getByName("testImplementation"))
}
configurations.named(bypassToolSourceSet.runtimeOnlyConfigurationName) {
    extendsFrom(configurations.getByName("testRuntimeOnly"))
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
    classpath = bypassToolSourceSet.runtimeClasspath
    mainClass.set("ru.sber.qa.tools.reporting.TestReportEligibilityScanner")
    systemProperty("splitter.config.load.mode", splitterConfigLoadMode)
    systemProperty("splitter.test.profile", splitterTestProfile)
    systemProperty("splitter.config.kafka.status.required", splitterConfigKafkaStatusRequired)
    systemProperty("includeManualTests", includeManualTests.toString())
    systemProperty("includeSplitterDataOperatorTests", includeSplitterDataOperatorTests.toString())
    systemProperty("env", activeTestEnv)
    environment("ENV", normalizedFileEnvironment())
    splitterRuntimeSystemProperties.forEach { propertyName ->
        configuredRuntimeSystemProperty(propertyName)?.let {
            systemProperty(propertyName, it)
            environment(propertyEnvName(propertyName), it)
        }
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
        inputs.property(propertyName, configuredRuntimeSystemProperty(propertyName) ?: "")
    }
    inputs.property("includeManualTests", includeManualTests)
    inputs.property("includeSplitterDataOperatorTests", includeSplitterDataOperatorTests)
    outputs.dir(reportEligibilityOutputDir)
    // Eligibility also depends on current credentials and Kafka availability.
    outputs.upToDateWhen { false }
}

val generateBypassTests by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Generate registration-only tests only for report-eligible functional tests"
    dependsOn(generateReportEligibility)
    classpath = bypassToolSourceSet.runtimeClasspath
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
    // Full regressions have a scanner per stage below; debug tasks use the generic scanner.
    if (includePattern != "ru.sber.qa.splitter.*") applyReportEligibilityExclusions()
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

// Shared raw results must not become outputs of every Test task: Gradle may clean them.
apply<io.qameta.allure.gradle.base.AllureBasePlugin>()
apply<io.qameta.allure.gradle.adapter.AllureAdapterBasePlugin>()
apply<io.qameta.allure.gradle.report.AllureReportPlugin>()
artifacts.add("allureRawResultElements", allureResultsDirectory)
configure<io.qameta.allure.gradle.base.AllureExtension> {
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

        project.copy {
            from(categoriesFile)
            into(allureResultsDirectory)
        }
    }
}

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
                "ALLURE_TOKEN is not set. Export it before running TestOps upload tasks."
            )

        println("allurectl  : ${resolveAllurectlExecutable()}")
    }
}

fun registerTestOpsUploadTask(
    taskName: String,
    taskGroup: String,
    preparationTask: String,
    resultsDirectory: () -> File
) = tasks.register(taskName) {
    group = taskGroup
    description = if (taskGroup == "regression")
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
            configuredRuntimeSystemProperty(propertyName)?.let { systemProperty(propertyName, it) }
        }
        secureLocalProperties.stringPropertyNames()
            .mapNotNull { name -> optionalLocalProperty(name)?.let { name to it } }
            .forEach { (name, value) -> systemProperty(name, value) }
        doFirst {
            systemProperty("env", normalizedFileEnvironment())
            environment("ENV", normalizedFileEnvironment())
        }
        finalizedBy("copyAllureCategories")
    }
}

val testOpsUpload = registerTestOpsUploadTask(
    "testOpsUpload", "testops", "prepareTestOpsResults", ::resolveAllureUploadResultsDirectory
)
val regressionTestOpsUpload = registerTestOpsUploadTask(
    "regressionTestOpsUpload", "regression", "prepareRegressionTestOpsResults", ::resolveRegressionUploadResultsDirectory
)

// One Gradle panel for the rebuilt suites. Each stage has its own discovery, run and evidence.
val regressionEnvironment = normalizedFileEnvironment()
val regressionResultsRoot = rootProject.file("regression-results/$regressionEnvironment")
val regressionProfileFile = rootProject.file("src/test/resources/regression-profiles.properties")
val regressionProfiles = Properties().apply {
    if (regressionProfileFile.isFile) regressionProfileFile.inputStream().use { load(it) }
}
val regressionRunId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")) +
        "-" + UUID.randomUUID().toString().take(8)

fun regressionRuntimeProperties(mode: String, dataOperator: Boolean): Map<String, String> {
    val result = linkedMapOf(
        "env" to regressionEnvironment,
        "splitter.config.load.mode" to mode,
        "splitter.test.profile" to "current",
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
    // Let the Java profile reader retain env-var > secure-file precedence for Ignite.
    val effective = LinkedHashMap<String, Any>(task.systemProperties)
    effective.keys.removeAll { it.matches(Regex("(?:ignite|data-operator\\.fixture|links\\.fixture)\\.(?:dev|ift|ift-dm|lt|local)\\..+")) }
    val cli = gradle.startParameter.projectProperties
    (System.getProperties().stringPropertyNames() + cli.keys).filter { key ->
        key.startsWith("ignite.") || key.startsWith("data-operator.fixture.") || key.startsWith("links.fixture.")
    }.forEach { key -> (cli[key] ?: System.getProperty(key))?.let { effective[key] = it } }
    effective.putAll(properties)
    task.setSystemProperties(effective)
    task.environment("ENV", regressionEnvironment)
    properties.filterKeys { it.startsWith("splitter.") }.forEach { (key, value) ->
        task.environment(propertyEnvName(key), value)
    }
}

fun configureRebuiltRegression(taskName: String, stage: String, mode: String, patterns: List<String>) {
    val dataOperator = stage == "data-operator"
    val includeDisabledInStage = stage == "splitter-rest" || stage == "splitter-kafka"
    val runtime = regressionRuntimeProperties(mode, dataOperator)
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
        inputs.files("src/test/resources/test.properties", regressionProfileFile,
            "src/test/resources/kafka-consumers.properties", "config/reporting/outdated-tests.properties")
        inputs.properties(runtime)
        outputs.dir(discoveryDir)
        // Credential availability can change without a tracked source change.
        outputs.upToDateWhen { false }
        doFirst {
            if (regressionEnvironment !in setOf("dev", "ift")) {
                throw GradleException("Rebuilt regression profiles are configured for dev/ift; selected $regressionEnvironment")
            }
            if (!regressionProfileFile.isFile) throw GradleException("Install ${regressionProfileFile.name}")
            secureLocalProperties.stringPropertyNames().filter { it.startsWith("SECURE_") }.forEach { key ->
                optionalLocalProperty(key)?.let { scannerTask.systemProperty(key, it) }
            }
            System.getProperties().stringPropertyNames().filter { it.startsWith("SECURE_") }.forEach { key ->
                scannerTask.systemProperty(key, System.getProperty(key))
            }
            applyRegressionRuntime(scannerTask, runtime)
        }
    }
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
            val effective = LinkedHashMap(runtime)
            effective["allure.results.directory"] = rawDir.absolutePath
            effective["report.exclusions.file"] = exclusions.get().absolutePath
            if (includeDisabledInStage) effective["junit.jupiter.conditions.deactivate"] = "org.junit.*DisabledCondition"
            else effective["junit.jupiter.conditions.deactivate"] = ""
            effective["junit.jupiter.execution.parallel.enabled"] = "false"
            if (dataOperator) {
                effective["data-operator.fixture.$regressionEnvironment.enabled"] = "true"
                effective["data-operator.fixture.$regressionEnvironment.output.directory"] =
                    rootProject.file("regression-fixtures/$regressionEnvironment/$regressionRunId").absolutePath
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
                appendSplitterRegressionLog(File(runDir, "console.log"),
                    "[${result.resultType}] ${testDescriptor.className}.${testDescriptor.name}\n" +
                        result.exceptions.joinToString("\n") { throwableStackTrace(it) })
            }
            override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                if (suite.parent != null) return
                File(runDir, "summary.json").writeText(JsonOutput.prettyPrint(JsonOutput.toJson(mapOf(
                    "environment" to regressionEnvironment, "stage" to stage, "completed" to true,
                    "total" to result.testCount, "passed" to result.successfulTestCount,
                    "failed" to result.failedTestCount, "skipped" to result.skippedTestCount
                ))), Charsets.UTF_8)
            }
        })
    }
}

tasks.register<Test>("experimentServiceRegression") {
    description = "Run experiment-service and refBook regressions using test.properties"
}
tasks.register<Test>("dataOperatorRegression") {
    description = "Run data-operator REST, EXPLAB-2411/2729/2974 regressions with owned Ignite fixtures"
}
configureRebuiltRegression("experimentServiceRegression", "experiment", "rest", listOf(
    "ru.sber.qa.experiments.*", "ru.sber.qa.controllers.refBookController.*"))
configureRebuiltRegression("splitterRestRegression", "splitter-rest", "rest", listOf("ru.sber.qa.splitter.*"))
configureRebuiltRegression("splitterKafkaRegression", "splitter-kafka", "kafka", listOf("ru.sber.qa.splitter.*"))
configureRebuiltRegression("dataOperatorRegression", "data-operator", "rest", listOf(
    "ru.sber.qa.dataoperator.regression.*", "ru.sber.qa.dataoperator.EXPLAB_2411.*",
    "ru.sber.qa.dataoperator.EXPLAB_2974.DataOperatorLinksFunctionalFlowTest",
    "ru.sber.qa.dataoperator.EXPLAB_2974.DataOperatorLinksValidationFlowTest",
    "ru.sber.qa.splitter.EXPLAB_2729.*"))

extra["regressionResultsRoot"] = regressionResultsRoot
extra["regressionEnvironment"] = regressionEnvironment
extra["testOpsSourceResultsDir"] =
    configValue("testOpsSourceResultsDir", "TESTOPS_SOURCE_RESULTS_DIR")?.let(::fileFromProjectOrAbsolute)
        ?: allureResultsDirectory
apply(from = "gradle/regression-results.gradle")
apply(from = "gradle/testops-results.gradle")
val regressionTaskNames = listOf("experimentServiceRegression", "splitterRestRegression", "splitterKafkaRegression", "dataOperatorRegression")
regressionTaskNames.forEachIndexed { index, taskName ->
    tasks.named(taskName) { mustRunAfter(regressionTaskNames.take(index)) }
}
// REST/Kafka need an operator to switch the server flag between independent Gradle invocations.
gradle.taskGraph.whenReady {
    if (hasTask(splitterRestRegression.get()) && hasTask(splitterKafkaRegression.get())) {
        throw GradleException("Run splitterRestRegression and splitterKafkaRegression separately; switch SPLITTER_CONFIG_API_CONFIG_LOAD on MAPPER and REACTIONS between them.")
    }
}
tasks.named("prepareRegressionTestOpsResults") { mustRunAfter(regressionTaskNames) }
tasks.named("cleanRegressionResults") { mustRunAfter(regressionTaskNames + listOf("prepareRegressionTestOpsResults", "regressionTestOpsUpload")) }
tasks.named("prepareTestOpsResults") { mustRunAfter(tasks.withType<Test>(), tasks.named("copyAllureCategories")) }
tasks.withType<Test>().configureEach { mustRunAfter("cleanTestOpsResults") }
// Keep support/debug/bypass tasks callable without duplicating the operator's Gradle panel.
listOf("auditReportingTags", "generateReportEligibility", "generateBypassTests", "bypassTests",
    "prepareSplitterRegressionLogs", "splitterRestDebug", "splitterKafkaDebug", "validateTestOpsUploadConfig"
).forEach { taskName -> tasks.named(taskName) { group = null } }

// Shared workload lifecycle and ticket entry points.
apply(from = "gradle/architecture/task-catalog.gradle")
