import java.util.Properties
import org.gradle.api.tasks.testing.Test

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

// The wrapper properties are the single source for the approved distribution.
tasks.named<org.gradle.api.tasks.wrapper.Wrapper>("wrapper") {
    val wrapperSettings = Properties().apply {
        rootProject.file("gradle/wrapper/gradle-wrapper.properties").inputStream().use { load(it) }
    }
    // settings.gradle.kts checks this version against the configured distribution.
    gradleVersion = gradle.gradleVersion
    distributionType = org.gradle.api.tasks.wrapper.Wrapper.DistributionType.BIN
    distributionUrl = wrapperSettings.getProperty("distributionUrl")
        ?: throw GradleException("Missing distributionUrl in gradle-wrapper.properties")
    distributionSha256Sum = wrapperSettings.getProperty("distributionSha256Sum")
        ?: throw GradleException("Missing distributionSha256Sum in gradle-wrapper.properties")
}

// Compile handwritten source sets and assemble the project without running scenarios.
// Generated registration-only bypass tests are deliberately outside this task.
tasks.register("compileWithoutTests") {
    group = "build"
    description = "Assemble main code and compile test/reporting sources without executing tests"
    dependsOn("assemble", "testClasses", "bypassToolClasses")
}

gradle.taskGraph.whenReady {
    if (allTasks.any { it.project == project && it.name == "compileWithoutTests" }) {
        val testTasks = allTasks.filterIsInstance<Test>()
        if (testTasks.isNotEmpty()) {
            throw GradleException(
                "compileWithoutTests cannot be combined with test execution: " +
                    testTasks.joinToString { it.path }
            )
        }
    }
}


// Shared values and typed function adapters for applied Kotlin scripts.
// No new plugin repositories, dependency versions or connection profiles.
extra["platformBuildContext"] = linkedMapOf<String, Any?>()
// Reject misspelled tunnel switches instead of silently falling back to a dead local URI.
// This also catches a Cyrillic look-alike in "scheduler": the suffix "heduler" stays ASCII.
val invalidSchedulerTunnelOptions = gradle.startParameter.projectProperties.keys.filter { key ->
    key.contains("heduler", ignoreCase = true) &&
        key.contains("tunnel", ignoreCase = true) && key != "schedulerTunnel"
}
require(invalidSchedulerTunnelOptions.isEmpty()) {
    "Unrecognized scheduler tunnel option(s): " + invalidSchedulerTunnelOptions.joinToString() +
        ". Use -PschedulerTunnel=true with ASCII letters, or set scheduler.dev.tunnel.enabled=true " +
        "in src/test/resources/scheduler.properties and omit the -P option."
}


apply(from = "gradle/build-logic/configuration.gradle.kts")
apply(from = "gradle/build-logic/dependencies.gradle.kts")
apply(from = "gradle/build-logic/eligibility.gradle.kts")
apply(from = "gradle/testops/artifacts.gradle.kts")
apply(from = "gradle/build-logic/bypass.gradle.kts")
apply(from = "gradle/build-logic/diagnostics.gradle.kts")
// Allure types belong to the root plugins classpath, not applied-script classloaders.
run {
    val allureResultsDirectory = (extra["platformBuildContext"] as Map<*, *>)["allureResultsDirectory"] as java.io.File

    // Use reporting only. The automatic adapter declares shared raw results as every Test task's
    // output, allowing Gradle stale-output cleanup to erase unrelated evidence.
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

}
apply(from = "gradle/build-logic/test-conventions.gradle.kts")
apply(from = "gradle/build-logic/regression.gradle.kts")
apply(from = "gradle/testops/tasks.gradle")
apply(from = "gradle/build-logic/tickets.gradle.kts")
apply(from = "gradle/build-logic/ignite.gradle.kts")
apply(from = "gradle/build-logic/task-guards.gradle.kts")
apply(from = "gradle/architecture/task-catalog.gradle")
