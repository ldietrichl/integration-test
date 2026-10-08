@file:Suppress("UNCHECKED_CAST")

import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test

@Suppress("UNCHECKED_CAST")
val buildContext = extra["platformBuildContext"] as MutableMap<String, Any?>

val sourceSets = extensions.getByType<SourceSetContainer>()


// Architecture support has no stand access during configuration or compilation.
apply(from = "gradle/architecture/recovery-clean.gradle")
apply(from = "gradle/architecture/corporate-ignite.gradle")

val prepareIgniteHelpers = tasks.register<JavaExec>("prepareIgniteHelpers") {
    description = "Compile the selected corporate Ignite helpers without connecting to Ignite"
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("test").runtimeClasspath
    mainClass.set("util.ignite.IgniteRuntimePreparation")
    args("data-operator")
    workingDir(projectDir)
    outputs.upToDateWhen { false }
}
tasks.named("dataOperatorRegression") { dependsOn(prepareIgniteHelpers) }

val prepareIgniteProbe = tasks.register<JavaExec>("prepareIgniteProbe") {
    description = "Compile the selected Ignite probe without opening a connection"
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("test").runtimeClasspath
    mainClass.set("util.ignite.IgniteRuntimePreparation")
    args("probe")
    workingDir(projectDir)
    outputs.upToDateWhen { false }
}
tasks.register<Test>("ignitePreflight") {
    dependsOn(prepareIgniteProbe)
    testClassesDirs = sourceSets.getByName("test").output.classesDirs
    classpath = sourceSets.getByName("test").runtimeClasspath
    useJUnitPlatform()
    filter.includeTestsMatching("ru.sber.qa.infrastructure.ignite.IgniteConnectionTest")
    maxParallelForks = 1
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
}
