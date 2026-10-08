@file:Suppress("UNCHECKED_CAST")

import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test

@Suppress("UNCHECKED_CAST")
val buildContext = extra["platformBuildContext"] as MutableMap<String, Any?>

val sourceSets = extensions.getByType<SourceSetContainer>()
val slf4jVersion get() = buildContext["slf4jVersion"] as String?
val includeDisabledTests get() = buildContext["includeDisabledTests"] as Boolean
val includeManualTests get() = buildContext["includeManualTests"] as Boolean
val includeSplitterDataOperatorTests get() = buildContext["includeSplitterDataOperatorTests"] as Boolean
val splitterConfigLoadMode get() = buildContext["splitterConfigLoadMode"] as String
val splitterTestProfile get() = buildContext["splitterTestProfile"] as String
val activeTestEnv get() = buildContext["activeTestEnv"] as String
val splitterConfigKafkaStatusRequired get() = buildContext["splitterConfigKafkaStatusRequired"] as String
val splitterRuntimeSystemProperties get() = buildContext["splitterRuntimeSystemProperties"] as List<String>
fun configuredRuntimeSystemProperty(name: String): String? = (buildContext["configuredRuntimeSystemProperty"] as (String) -> String?)(name)
fun normalizedFileEnvironment(): String = (buildContext["normalizedFileEnvironment"] as () -> String)()
fun propertyEnvName(name: String): String = (buildContext["propertyEnvName"] as (String) -> String)(name)
val bypassTests get() = buildContext["bypassTests"] as org.gradle.api.tasks.TaskProvider<Test>

// Reporting tools have no dependency on the functional project's runtime or stand libraries.
val bypassToolSourceSet = sourceSets.create("bypassTool") {
    java.srcDir("src/bypassTool/java")
    java.srcDir("src/main/java")
    java.include("ru/sber/qa/tools/**", "infrastructure/scheduler/SchedulerOutcomeClassification.java",
        "infrastructure/scheduler/SchedulerRegressionPolicy.java",
        "infrastructure/scheduler/SchedulerAllurePresentation.java",
        "infrastructure/scheduler/SchedulerConfigMapEvidence.java")
    resources.srcDir("src/main/resources")
    resources.include("scheduler-allure-ru.json")
}
sourceSets.named("test") { java.srcDir("src/reporting/java") }
val generatedBypassSourcesDir = layout.buildDirectory.dir("generated/sources/bypassTests/java")
val bypassTestsSourceSet = sourceSets.create("bypassTests") {
    java.srcDir("src/bypassTests/java")
    java.srcDir("src/reporting/java")
    java.srcDir(generatedBypassSourcesDir.get().asFile)
    resources.setSrcDirs(listOf("src/bypassTests/resources"))
}
dependencies {
    add(bypassToolSourceSet.implementationConfigurationName, "com.fasterxml.jackson.core:jackson-databind:2.17.2")
    add(bypassToolSourceSet.implementationConfigurationName, "io.qameta.allure:allure-model:2.29.0")
    add(bypassTestsSourceSet.implementationConfigurationName, "com.fasterxml.jackson.core:jackson-databind:2.17.2")
    add(bypassTestsSourceSet.implementationConfigurationName, "io.qameta.allure:allure-junit5:2.29.0")
    add(bypassTestsSourceSet.implementationConfigurationName, "org.slf4j:slf4j-api:$slf4jVersion")
    add(bypassTestsSourceSet.runtimeOnlyConfigurationName, "org.slf4j:slf4j-simple:$slf4jVersion")
    add(bypassTestsSourceSet.implementationConfigurationName, "org.junit.jupiter:junit-jupiter-api:5.10.3")
    add(bypassTestsSourceSet.runtimeOnlyConfigurationName, "org.junit.jupiter:junit-jupiter-engine:5.10.3")
    add(bypassTestsSourceSet.runtimeOnlyConfigurationName, "org.junit.platform:junit-platform-launcher:1.10.3")
}
tasks.named<org.gradle.language.jvm.tasks.ProcessResources>(bypassTestsSourceSet.processResourcesTaskName) {
    from("src/test/resources/scheduler/scenarios.json") { into("scheduler") }
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
        .filter { it.startsWith("splitter.kap.") }
        .forEach { systemProperty(it, System.getProperty(it)) }
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

fun Test.applyReportEligibilityExclusions() {
    dependsOn(generateReportEligibility)
    // An explicit IDEA/CLI selection must never report a silent successful zero-test run.
    filter.setFailOnNoMatchingTests(true)
    doFirst {
        if (includeDisabledTests) {
            systemProperty("junit.jupiter.conditions.deactivate", "org.junit.*DisabledCondition")
        }

        val exclusions = if (includeDisabledTests) {
            reportExclusionsWithDisabledIncludedFile.get().asFile
        } else {
            reportExclusionsFile.get().asFile
        }
        systemProperty("report.exclusions.file", exclusions.absolutePath)
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


// Explicit cross-script contract; task actions remain lazy.
buildContext["bypassToolSourceSet"] = bypassToolSourceSet
buildContext["bypassTestsSourceSet"] = bypassTestsSourceSet
buildContext["generatedBypassSourcesDir"] = generatedBypassSourcesDir
buildContext["reportEligibilityOutputDir"] = reportEligibilityOutputDir
buildContext["auditReportingTags"] = auditReportingTags
buildContext["generateReportEligibility"] = generateReportEligibility
buildContext["applyReportEligibilityExclusions"] = { task: Test -> task.applyReportEligibilityExclusions() }
