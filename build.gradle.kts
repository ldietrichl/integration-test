import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import java.util.Properties

val gradleLocalProperties = Properties()
val gradleLocalPropertiesFile = rootProject.file("gradle.local.properties")
if (gradleLocalPropertiesFile.isFile) {
    gradleLocalPropertiesFile.inputStream().use { gradleLocalProperties.load(it) }
}

fun optionalLocalProperty(name: String): String? =
    (project.findProperty(name) as String?) ?: gradleLocalProperties.getProperty(name)

fun firstNotBlank(vararg candidates: String?): String? =
    candidates.firstOrNull { !it.isNullOrBlank() }?.trim()

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
    args(
        file("src/test/java").absolutePath,
        file("config/reporting/outdated-tests.properties").absolutePath,
        reportEligibilityOutputDir.get().asFile.absolutePath
    )
    inputs.dir("src/test/java")
    inputs.file("config/reporting/outdated-tests.properties")
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


// Functional reports must contain only eligible tests. Exclusions are applied before test discovery,
// therefore disabled/no-assertion/outdated cases do not become skipped entries in Allure.
tasks.named<Test>("test") {
    dependsOn(generateReportEligibility)
    filter.setFailOnNoMatchingTests(false)
    doFirst {
        val exclusions = reportExclusionsFile.get().asFile
        if (exclusions.exists()) {
            exclusions.readLines()
                .map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .forEach { filter.excludeTestsMatching(it) }
        }
        val mapperScopeAvailable = firstNotBlank(
            System.getProperty("experiment.mapper.scope.available"),
            System.getenv("EXPERIMENT_MAPPER_SCOPE_AVAILABLE"),
            System.getProperty("exlab2559.mapper.scope.available"),
            System.getenv("EXPLAB_2559_MAPPER_SCOPE_AVAILABLE")
        )
        if (mapperScopeAvailable?.equals("false", ignoreCase = true) == true) {
            filter.excludeTestsMatching("ru.sber.qa.experiments.EXPLAB_2559.*")
            filter.excludeTestsMatching("ru.sber.qa.experiments.v2.CreateChangeGetDeleteExperimentV2Test.*")
        }
        val v2CjToggleTestsEnabled = firstNotBlank(
            System.getProperty("experiment.v2.cj.toggle.tests.enabled"),
            System.getenv("EXPERIMENT_V2_CJ_TOGGLE_TESTS_ENABLED")
        )
        if (v2CjToggleTestsEnabled?.equals("false", ignoreCase = true) == true) {
            filter.excludeTestsMatching("ru.sber.qa.experiments.EXPLAB_2696.RunningV1CacheV2CjEnabled2696FlowTest.*")
        }
    }
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

tasks.register<Copy>("copyAllureCategories") {
    delete("${buildDir}/copy-categories/")
    from("${projectDir}/allure/categories.json")
    into("${buildDir}/allure-results/")
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
        systemProperty("report.outdated.tests.file", file("config/reporting/outdated-tests.properties").absolutePath)
        systemProperty("report.exclusions.file", reportExclusionsFile.get().asFile.absolutePath)
        testLogging {
            showStandardStreams = true
            events("PASSED", "SKIPPED", "FAILED", "STANDARD_OUT", "STANDARD_ERROR")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showCauses = true
            showExceptions = true
            showStackTraces = true
        }
        val forwardedTestSystemProperties = setOf(
            "encryption.password",
            "EXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED",
            "exlab2696.running.cache.wait.timeout.ms",
            "exlab2696.running.cache.wait.poll.ms",
            "experiment.mapper.scope.available",
            "exlab2559.mapper.scope.available",
            "experiment.v2.cj.toggle.tests.enabled",
            "exlab2930.processor.timeout.seconds",
            "exlab2930.processor.stability.seconds"
        )
        System.getProperties().stringPropertyNames()
            .filter { it in forwardedTestSystemProperties }
            .forEach { systemProperty(it, System.getProperty(it)) }
        finalizedBy("copyAllureCategories")
    }
}
