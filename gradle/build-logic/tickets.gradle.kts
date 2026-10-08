@file:Suppress("UNCHECKED_CAST")

import org.gradle.api.tasks.testing.Test
import java.util.Locale

@Suppress("UNCHECKED_CAST")
val buildContext = extra["platformBuildContext"] as MutableMap<String, Any?>

val sourceSets = extensions.getByType<SourceSetContainer>()
val bypassTests get() = buildContext["bypassTests"] as org.gradle.api.tasks.TaskProvider<Test>
fun configValue(name: String, vararg envNames: String, defaultValue: String? = null): String? =
    (buildContext["configValue"] as (String, Array<out String>, String?) -> String?)(name, envNames, defaultValue)
fun normalizedFileEnvironment(): String = (buildContext["normalizedFileEnvironment"] as () -> String)()

fun ticketValue(name: String, defaultValue: String): String =
    configValue(name, name.replace('.', '_').replace('-', '_').uppercase(Locale.ROOT), defaultValue = defaultValue)!!

fun configureExplab2690MapperCoverage(task: Test) = task.apply {
    group = "tickets"
    description = "EXPLAB-2690: MAPPER coverage with managed stand readiness and ConfigMap state"
    dependsOn(tasks.named("testClasses"))
    testClassesDirs = sourceSets.getByName("test").output.classesDirs
    classpath = sourceSets.getByName("test").runtimeClasspath
    useJUnitPlatform()
    filter.includeTestsMatching("ru.sber.qa.splitter.EXPLAB_2690.*")
    filter.excludeTestsMatching("ru.sber.qa.splitter.EXPLAB_2690.SplitterReactions*")
    filter.isFailOnNoMatchingTests = true
    maxParallelForks = 1
    forkEvery = 0
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
    systemProperty("junit.jupiter.testclass.order.default", "org.junit.jupiter.api.ClassOrderer\$OrderAnnotation")
    systemProperty("explab2690.stand.application-flags.enabled", ticketValue("explab2690.stand.application-flags.enabled", "true"))
    systemProperty("file.encoding", "UTF-8")
    systemProperty("splitter.config.load.mode", "rest")
    systemProperty("splitter.test.profile", "mapper-alternative-contract")
    val ticketEnv = normalizedFileEnvironment()
    val workload = ticketValue("explab2690.stand.workload", "splitter-mapper")
    val deployment = ticketValue("explab2690.stand.deployment", "splitter-mapper-service")
    val service = ticketValue("explab2690.stand.service", "splitter-mapper-service")
    val rulesConfigMap = ticketValue("explab2690.configmap.rules.name", "splitter-mapper-service-lib")
    val serviceConfigMap = ticketValue("explab2690.configmap.service.name", "splitter-mapper-service")
    systemProperty("explab2690.stand.config.enabled", ticketValue("explab2690.stand.config.enabled", "true"))
    systemProperty("explab2690.stand.workload", workload)
    systemProperty("explab2690.stand.service", service)
    systemProperty("explab2690.stand.service-port", ticketValue("explab2690.stand.service-port", "8080"))
    listOf("timeout.seconds" to "120", "poll.millis" to "2000").forEach { (key, fallback) ->
        val property = "stand.$ticketEnv.workloads.$workload.readiness.$key"
        systemProperty(property, ticketValue(property, fallback))
    }
    systemProperty("stand.$ticketEnv.workloads.$workload.deployment", deployment)
    systemProperty("stand.$ticketEnv.workloads.$workload.configmap", rulesConfigMap)
    systemProperty("stand.$ticketEnv.workloads.$workload.service-configmap", serviceConfigMap)
    systemProperty("explab2690.configmap.rules.name", rulesConfigMap)
    systemProperty("explab2690.configmap.rules.key",
        ticketValue("explab2690.configmap.rules.key", "splitter-rules-mapper.yml"))
    systemProperty("explab2690.configmap.rules.resource",
        ticketValue("explab2690.configmap.rules.resource", "splitter/EXPLAB_2690/configmap/mapper-required.yml"))
    systemProperty("explab2690.configmap.service.enabled",
        ticketValue("explab2690.configmap.service.enabled", "false"))
    systemProperty("explab2690.configmap.service.name", serviceConfigMap)
    systemProperty("explab2690.configmap.service.api-config-load-key",
        ticketValue("explab2690.configmap.service.api-config-load-key", "splitter.config.api-config-load"))
    systemProperty("explab2690.configmap.service.api-config-load-value",
        ticketValue("explab2690.configmap.service.api-config-load-value", "true"))
    systemProperty("explab2690.configmap.service.api-config-load-env-key",
        ticketValue("explab2690.configmap.service.api-config-load-env-key", ""))
    systemProperty("explab2690.configmap.service.api-config-load-env-value",
        ticketValue("explab2690.configmap.service.api-config-load-env-value", "true"))
    systemProperty("allure.results.directory", layout.buildDirectory.dir("allure-results").get().asFile.absolutePath)
    reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/explab2690MapperCoverage"))
    reports.html.outputLocation.set(layout.buildDirectory.dir("reports/tests/explab2690MapperCoverage"))
    outputs.upToDateWhen { false }
    doFirst {
        systemProperty("splitter.config.load.mode", "rest")
        systemProperty("splitter.test.profile", "mapper-alternative-contract")
        logger.lifecycle(
            "EXPLAB-2690 managed MAPPER state: workload=${systemProperties["explab2690.stand.workload"]}, " +
                    "service=${systemProperties["explab2690.stand.service"]}, " +
                    "rulesConfigMap=${systemProperties["explab2690.configmap.rules.name"]}, " +
                    "rulesKey=${systemProperties["explab2690.configmap.rules.key"]}, " +
                    "serviceConfigMapEnabled=${systemProperties["explab2690.configmap.service.enabled"]}"
        )
    }
}

if (tasks.findByName("explab2690MapperCoverage") == null) {
    tasks.register<Test>("explab2690MapperCoverage") {
        configureExplab2690MapperCoverage(this)
    }
} else {
    tasks.named<Test>("explab2690MapperCoverage") {
        configureExplab2690MapperCoverage(this)
    }
}

// Keep the standard Test lifecycle and separate service overrides for a combined root-owned run.
listOf("explab2690ReactionsCoverage", "explab2690Coverage", "splitterExtensionRegression").forEach { taskName ->
    val task = if (tasks.findByName(taskName) == null) tasks.register<Test>(taskName) else tasks.named<Test>(taskName)
    task.configure {
        configureExplab2690MapperCoverage(this)
        filter.setExcludePatterns()
        filter.setIncludePatterns(when (taskName) {
            "explab2690ReactionsCoverage" -> "ru.sber.qa.splitter.EXPLAB_2690.SplitterReactions*"
            "splitterExtensionRegression" -> "ru.sber.qa.splitter.extension.*"
            else -> "ru.sber.qa.splitter.EXPLAB_2690.*"
        })
        description = "EXPLAB-2690: ${if (taskName == "explab2690Coverage") "MAPPER + REACTIONS" else "REACTIONS"}, run-scoped stand state"
        if (taskName == "splitterExtensionRegression") {
            group = "regression"
            description = "Splitter: extended document matrices, Kafka reports, timestamps, flags and layers"
        }
        systemProperty("allure.results.directory", layout.buildDirectory.dir(
            if (taskName == "splitterExtensionRegression") "allure-results-splitter-extension" else "allure-results"
        ).get().asFile.absolutePath)
        reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/$taskName"))
        reports.html.outputLocation.set(layout.buildDirectory.dir("reports/tests/$taskName"))
    }
}

listOf("explab2690MapperCoverage", "explab2690ReactionsCoverage", "explab2690Coverage", "splitterExtensionRegression").forEach { taskName ->
    tasks.named<Test>(taskName) {
        val env = normalizedFileEnvironment()
        val prefix = "explab2690.reactions."
        val workload = ticketValue(prefix + "stand.workload", "splitter-reactions")
        val service = ticketValue(prefix + "stand.service", "splitter-reactions-service")
        val rulesMap = ticketValue(prefix + "configmap.rules.name", "$service-lib")
        mapOf("stand.workload" to workload, "stand.service" to service, "stand.service-port" to "8080",
            "configmap.rules.name" to rulesMap, "configmap.rules.key" to "splitter-rules-reactions.yml",
            "configmap.rules.resource" to "splitter/EXPLAB_2690/configmap/reactions-required.yml",
            "configmap.service.enabled" to "false", "configmap.service.name" to service,
            "configmap.service.api-config-load-key" to "", "configmap.service.api-config-load-env-key" to "",
            "configmap.service.api-config-load-value" to "true", "configmap.service.api-config-load-env-value" to "true")
            .forEach { (key, value) -> systemProperty(prefix + key, ticketValue(prefix + key, value)) }
        systemProperty("stand.$env.workloads.$workload.deployment", ticketValue(prefix + "stand.deployment", service))
        systemProperty("stand.$env.workloads.$workload.configmap", rulesMap)
        listOf("timeout.seconds" to "120", "poll.millis" to "2000").forEach { (key, value) ->
            val property = "stand.$env.workloads.$workload.readiness.$key"
            systemProperty(property, ticketValue(property, value))
        }
        listOf("explab2690.", prefix).forEach { servicePrefix ->
            listOf("allow-result-without-main", "all-rule-code-exp-enabled", "empty-objects-response-enabled",
                "return-suppressed", "api-config-load").forEach { key ->
                val property = servicePrefix + "application-env." + key
                systemProperty(property, ticketValue(property, "SPLITTER_" + key.replace('-', '_').uppercase(Locale.ROOT)))
            }
        }
    }
}
tasks.named("explab2690ReactionsCoverage") { mustRunAfter("explab2690MapperCoverage") }
tasks.named("explab2690Coverage") { mustRunAfter("explab2690MapperCoverage", "explab2690ReactionsCoverage") }
tasks.named("splitterExtensionRegression") {
    mustRunAfter("explab2690MapperCoverage", "explab2690ReactionsCoverage", "explab2690Coverage")
}

// Extended contracts are opt-in, including for broad REST/Kafka regression and IDE Gradle test runs.
tasks.withType<Test>().configureEach {
    if (name != "splitterExtensionRegression" && name != "bypassTests")
        filter.excludeTestsMatching("ru.sber.qa.splitter.extension.*")
}

// EXPLAB-2972 uses the standard Java test source set and Platform V AT lifecycle.
mapOf(
    "explab2972Test" to "StatusChange2972RegularFlowTest",
    "explab2972ManagedTest" to "StatusChange2972ManagedFlowTest",
    "explab2972LaunchPlanTest" to "LaunchPlan2972FlowTest"
).forEach { (taskName, testClass) ->
    tasks.register<Test>(taskName) {
        group = "verification"
        description = "EXPLAB-2972 / Platform V AT / $testClass; environment from test.properties"
        dependsOn(tasks.named("testClasses"))
        testClassesDirs = sourceSets.getByName("test").output.classesDirs
        classpath = sourceSets.getByName("test").runtimeClasspath
        useJUnitPlatform()
        filter.includeTestsMatching(if (taskName == "explab2972LaunchPlanTest")
            "ru.sber.qa.experiments.EXPLAB_2972.launch_plan.$testClass"
            else "ru.sber.qa.experiments.explab2972.$testClass")
        filter.isFailOnNoMatchingTests = true
        maxParallelForks = 1
        systemProperty("junit.jupiter.execution.parallel.enabled", "false")
        systemProperty("file.encoding", "UTF-8")
        systemProperty("allure.results.directory", layout.buildDirectory.dir("allure-results").get().asFile.absolutePath)
        reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/$taskName"))
        reports.html.outputLocation.set(layout.buildDirectory.dir("reports/tests/$taskName"))
        outputs.upToDateWhen { false }
    }
}
tasks.withType<Test>().configureEach {
    if (name != "explab2972LaunchPlanTest" && name != "bypassTests")
        filter.excludeTestsMatching("ru.sber.qa.experiments.EXPLAB_2972.launch_plan.*")
    if (name != "explab2972ManagedTest" && name != "bypassTests")
        filter.excludeTestsMatching("ru.sber.qa.experiments.explab2972.StatusChange2972ManagedFlowTest")
}
tasks.named("explab2972ManagedTest") { mustRunAfter("explab2972Test") }
tasks.named("explab2972LaunchPlanTest") { mustRunAfter("explab2972Test", "explab2972ManagedTest") }

val workloadCheckClasses = layout.buildDirectory.dir("classes/java/workloadChecks")
val compileWorkloadChecks = tasks.register<JavaCompile>("compileWorkloadChecks") {
    source(fileTree("src/workloadChecks/java") { include("**/*.java") })
    source("tools/scheduler/local-isolation-checks/java/SchedulerIsolationChecks.java")
    classpath = sourceSets.getByName("test").runtimeClasspath
    destinationDirectory.set(workloadCheckClasses)
    options.encoding = "UTF-8"
    options.release.set(17)
    dependsOn("testClasses")
}
tasks.register<JavaExec>("workloadContractChecks") {
    group = "verification"
    description = "Fabric8 workload lifecycle checks against an isolated loopback API"
    dependsOn(compileWorkloadChecks)
    classpath = files(workloadCheckClasses, sourceSets.getByName("test").runtimeClasspath)
    mainClass.set("infrastructure.kubernetes.WorkloadContractChecks")
    systemProperty("allure.results.directory", layout.buildDirectory.dir("contract-checks/allure-results").get().asFile.absolutePath)
    systemProperty("stand.artifacts.directory", file(".workload-recovery/private/contract-checks").absolutePath)
}
