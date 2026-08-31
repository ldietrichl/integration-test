import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import java.io.File
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

fun resolveSplitterConfigLoadMode(): String {
    val restTaskRequested = isGradleTaskRequested("splitterRestRegression")
    val kafkaTaskRequested = isGradleTaskRequested("splitterKafkaRegression")
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
val splitterConfigKafkaStatusRequired =
    configValue("splitter.config.kafka.status.required", "SPLITTER_CONFIG_KAFKA_STATUS_REQUIRED", defaultValue = "true")!!
val allureResultsDirectory = fileFromProjectOrAbsolute(
    usableLocalProperty(System.getProperty("allure.results.directory"))
        ?: optionalConfigProperty("allure.results.directory")
        ?: "build/allure-results"
)

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
    args(
        file("src/test/java").absolutePath,
        file("config/reporting/outdated-tests.properties").absolutePath,
        reportEligibilityOutputDir.get().asFile.absolutePath
    )
    inputs.dir("src/test/java")
    inputs.file("config/reporting/outdated-tests.properties")
    inputs.property("splitter.config.load.mode", splitterConfigLoadMode)
    inputs.property("splitter.test.profile", splitterTestProfile)
    inputs.property("splitter.config.kafka.status.required", splitterConfigKafkaStatusRequired)
    inputs.property("includeManualTests", includeManualTests)
    inputs.property("includeSplitterDataOperatorTests", includeSplitterDataOperatorTests)
    outputs.dir(reportEligibilityOutputDir)
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

fun Test.configureSplitterRegressionTask(mode: String) {
    group = "verification"
    dependsOn(tasks.named("testClasses"))
    testClassesDirs = sourceSets.getByName("test").output.classesDirs
    classpath = sourceSets.getByName("test").runtimeClasspath
    applyReportEligibilityExclusions()
    systemProperty("splitter.config.load.mode", mode)
    filter.includeTestsMatching("ru.sber.qa.splitter.*")
    doFirst("printSplitterConfigLoadModeNotice") {
        logger.lifecycle(splitterConfigLoadModeNotice(mode))
    }
}

val splitterRestRegression by tasks.registering(Test::class) {
    description = "Run report-eligible splitter tests with REST config load flow"
    configureSplitterRegressionTask("rest")
}

val splitterKafkaRegression by tasks.registering(Test::class) {
    description = "Run report-eligible splitter tests with Kafka config load flow"
    configureSplitterRegressionTask("kafka")
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

fun isRequestedTask(taskName: String): Boolean =
    gradle.startParameter.taskNames.any { requested ->
        requested == taskName || requested == ":$taskName" || requested.endsWith(":$taskName")
    }

fun isTestOpsUploadRequested(): Boolean =
    configFlag("allureUploadEnabled", "ALLURE_UPLOAD_ENABLED") ||
            isRequestedTask("testOpsUpload") ||
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
        secureLocalProperties.stringPropertyNames()
            .mapNotNull { name -> optionalLocalProperty(name)?.let { name to it } }
            .forEach { (name, value) -> systemProperty(name, value) }
        finalizedBy("copyAllureCategories")
    }
}
