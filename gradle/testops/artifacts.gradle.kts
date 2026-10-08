@file:Suppress("UNCHECKED_CAST")

import java.nio.file.Files as ArtifactFiles
import java.nio.file.StandardCopyOption as ArtifactStandardCopyOption
import java.nio.file.StandardOpenOption as ArtifactStandardOpenOption
import java.nio.channels.FileChannel as ArtifactFileChannel
import java.security.MessageDigest as ArtifactMessageDigest
import java.math.BigInteger as ArtifactBigInteger
import java.util.SortedMap as ArtifactSortedMap
import java.util.TreeMap as ArtifactTreeMap
import java.time.Instant as ArtifactInstant
import groovy.json.JsonOutput
import java.io.File
import java.util.Locale
import java.util.Properties
import java.util.UUID

@Suppress("UNCHECKED_CAST")
val buildContext = extra["platformBuildContext"] as MutableMap<String, Any?>



class AllureArtifactFiles {
    fun assertExecutionKind(result: Map<*, *>, registration: Boolean, context: String) {
        val labels = (result["labels"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
        val marked = labels.any { it["name"] == "executionMode" && it["value"] == "registration-only" } &&
            labels.any { it["name"] == "registrationOnly" && it["value"] == "true" }
        fun registrationEvidence(node: Any?): Boolean = when (node) {
            is Map<*, *> -> node.any { (key, value) ->
                (key == "historyId" && value is String && value.startsWith("registration-only::")) ||
                (key == "name" && value is String &&
                    (value.contains("Bypass registration: original functional test is not executed") ||
                     value.contains("/ bypass registration mode"))) ||
                (key == "name" && value == "executionMode" && node["value"] == "registration-only") ||
                (key == "name" && value == "registrationOnly" && node["value"] == "true") ||
                registrationEvidence(value)
            }
            is List<*> -> node.any { registrationEvidence(it) }
            else -> false
        }
        if (registration) {
            if (!marked || result["status"] != "passed" ||
                !(result["historyId"] as? String).orEmpty().startsWith("registration-only::"))
                throw GradleException("Only completed, explicitly marked registration results are allowed: " + context)
        } else if (registrationEvidence(result)) {
            throw GradleException("Registration result rejected from functional TestOps bundle: " + context)
        }
    }

    fun contained(boundary: File, candidate: File): File {
        val base = boundary.canonicalFile.toPath()
        val resolved = candidate.canonicalFile.toPath()
        if (resolved == base || !resolved.startsWith(base))
            throw GradleException("Artifact path escapes its directory: $candidate")
        return candidate
    }
    fun unredirected(candidate: File): File {
        val absolute = candidate.absoluteFile.toPath().normalize()
        if (candidate.canonicalFile.toPath() != absolute || ArtifactFiles.isSymbolicLink(absolute))
            throw GradleException("Refusing a redirected artifact path: $candidate")
        return candidate
    }
    fun flatName(value: Any?, context: String): String {
        if (value !is String || value.isEmpty() || value in setOf(".", "..") ||
            value.any { it == '/' || it == '\\' || it == ':' })
            throw GradleException("Invalid flat Allure filename in $context: $value")
        return value
    }
    fun readObject(file: File): Map<*, *> = try {
        groovy.json.JsonSlurper().parse(file, "UTF-8") as? Map<*, *>
            ?: throw GradleException("Expected Allure JSON object: ${file.name}")
    } catch (error: GradleException) { throw error }
      catch (error: Exception) { throw GradleException("Cannot parse Allure JSON: ${file.name}", error) }
    fun sha256(bytes: ByteArray): String = hex(ArtifactMessageDigest.getInstance("SHA-256").digest(bytes))
    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    fun fileHash(file: File): String {
        val digest = ArtifactMessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return hex(digest.digest())
    }
    fun writeJson(file: File, value: Any) = file.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(value)) + "\n", Charsets.UTF_8)
    fun counter(value: Any?, context: String): ArtifactBigInteger {
        if (value !is Number || !Regex("0|[1-9][0-9]*").matches(value.toString()))
            throw GradleException("Invalid non-negative integer in $context")
        return ArtifactBigInteger(value.toString())
    }
    fun children(container: Map<*, *>, context: String): List<*> {
        val children = container["children"] ?: return emptyList<Any>()
        return children as? List<*> ?: throw GradleException("Invalid Allure container children in $context")
    }
}

class SelectedTestOpsArtifacts(
    private val project: org.gradle.api.Project,
    private val environment: String,
    input: File,
    private val registrationOnly: Boolean = false,
    outputParent: File? = null
) {
    private val files = AllureArtifactFiles()
    private val sourceDirectory = input.absoluteFile
    private val bundleParent = (outputParent ?: project.layout.buildDirectory.dir(if (registrationOnly) "bypass-testops-results" else "testops-results").get().asFile).absoluteFile
    private val bundleDirectory = File(bundleParent, environment)
    private val preparedDirectory = File(bundleDirectory, "allure-results")
    private val statuses = setOf("passed", "failed", "broken", "skipped", "unknown")
    private val metadata = setOf("categories.json", "executor.json", "environment.properties", "environment.xml")
    // These task-owned directories are declared by tickets.gradle.kts. Never scan arbitrary build trees.
    private val taskDirectories = setOf("explab2690MapperCoverage", "explab2690ReactionsCoverage",
        "explab2690Coverage", "splitterExtensionRegression", "explab2972Test",
        "explab2972ManagedTest", "explab2972LaunchPlanTest")
    private fun acceptsTaskDirectories() = !registrationOnly && sourceDirectory.toPath().normalize() ==
        project.layout.buildDirectory.dir("allure-results").get().asFile.absoluteFile.toPath().normalize()
    private fun sourceFile(relative: String): File = files.unredirected(files.contained(sourceDirectory, File(sourceDirectory, relative)))
    private fun scope(relative: String) = relative.substringBeforeLast('/', "")
    private fun scoped(scope: String, name: String) = if (scope.isEmpty()) name else "$scope/$name"
    init {
        if (environment !in setOf("dev", "ift", "ift-dm", "lt", "local"))
            throw GradleException("Unsupported TestOps environment: $environment")
    }
    private fun managed(candidate: File): File {
        files.unredirected(bundleParent)
        files.unredirected(candidate)
        return files.contained(bundleParent, candidate)
    }
    private fun deleteManaged(candidate: File) {
        managed(candidate)
        if (candidate.exists()) {
            ArtifactFiles.walk(candidate.toPath()).use { paths -> paths.forEach { files.unredirected(it.toFile()) } }
            project.delete(candidate)
        }
    }
    private fun requireSource() {
        files.unredirected(sourceDirectory)
        val source = sourceDirectory.canonicalFile.toPath()
        val output = bundleParent.canonicalFile.toPath()
        if (source.startsWith(output) || output.startsWith(source))
            throw GradleException("TestOps raw input and prepared output must be separate directories.")
    }
    private fun snapshot(): ArtifactSortedMap<String, Map<String, Any>> {
        requireSource()
        if (!sourceDirectory.isDirectory)
            throw GradleException("Allure source directory does not exist: $sourceDirectory. Run selected tests first.")
        val entries = sourceDirectory.listFiles() ?: throw GradleException("Cannot list Allure source: $sourceDirectory")
        val result = ArtifactTreeMap<String, Map<String, Any>>()
        fun record(file: File, relative: String) {
            files.unredirected(file)
            if (!file.isFile) throw GradleException("Expected flat Allure results: $file")
            files.flatName(file.name, "source")
            result[relative] = linkedMapOf("size" to file.length(), "sha256" to files.fileHash(file))
        }
        entries.forEach { file ->
            files.unredirected(file)
            if (file.isDirectory) {
                if (!acceptsTaskDirectories() || file.name !in taskDirectories)
                    throw GradleException("Unsupported Allure source directory: $file; only declared task result directories are accepted")
                (file.listFiles() ?: throw GradleException("Cannot list Allure task results: $file"))
                    .forEach { child -> record(child, "${file.name}/${child.name}") }
            } else record(file, file.name)
        }
        return result
    }
    private fun junitSnapshot(original: Map<String, *>): ArtifactSortedMap<String, Map<String, Any>> {
        val snapshot = ArtifactTreeMap<String, Map<String, Any>>()
        original.keys.map(::scope).filter { it.isNotEmpty() }.toSet().forEach { task ->
            val xml = files.unredirected(project.layout.buildDirectory.dir("test-results/$task").get().asFile)
            val suites = xml.listFiles()?.filter { it.name.startsWith("TEST-") && it.name.endsWith(".xml") }.orEmpty()
            if (suites.isEmpty()) throw GradleException("Missing JUnit evidence for Allure task directory: $task")
            suites.forEach { f ->
                files.unredirected(f)
                snapshot["$task/${f.name}"] = linkedMapOf("size" to f.length(), "sha256" to files.fileHash(f))
            }
        }
        return snapshot
    }
    private fun validateTaskResults(original: Map<String, *>): List<Map<String, Any>> =
        original.keys.map(::scope).filter { it.isNotEmpty() }.toSet().sorted().map { task ->
            val audit = project.extensions.extraProperties.let { extra ->
                if (!extra.has("splitterRunAudit")) throw GradleException("JUnit audit is not configured for task results")
                (extra["splitterRunAudit"] as groovy.lang.Closure<*>).call(
                    project.layout.buildDirectory.dir("test-results/$task").get().asFile) as Map<*, *>
            }
            if (audit["completed"] != true) throw GradleException("Incomplete JUnit execution for Allure task directory: $task")
            val results = original.keys.filter { scope(it) == task && it.endsWith("-result.json") }
                .map { files.readObject(sourceFile(it)) }
            val actual = linkedMapOf("passed" to 0, "failed" to 0, "skipped" to 0)
            val classes = linkedMapOf<String, Int>()
            val suites = audit["suites"] as List<Map<*, *>>
            results.forEach { j ->
                val status = if (j["status"] in setOf("failed", "broken")) "failed" else j["status"] as? String
                if (status !in actual) throw GradleException("Invalid result status for task $task")
                actual[status!!] = actual.getValue(status) + 1
                val labels = (j["labels"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
                val clazz = labels.firstOrNull { it["name"] == "testClass" }?.get("value") as? String
                    ?: throw GradleException("Missing testClass for task $task")
                classes[clazz] = (classes[clazz] ?: 0) + 1
                val start = (j["start"] as? Number)?.toLong() ?: -1L
                val stop = (j["stop"] as? Number)?.toLong() ?: -1L
                if (suites.none { s -> (s["classes"] as Map<*, *>).containsKey(clazz) &&
                        start >= (s["start"] as Number).toLong() - 1000 && stop <= (s["end"] as Number).toLong() + 1000 })
                    throw GradleException("Stale Allure result outside latest JUnit execution: $task / $clazz")
                if (task.startsWith("explab2690")) {
                    val mode = (j["parameters"] as? List<*>)?.filterIsInstance<Map<*, *>>()
                        ?.firstOrNull { it["name"] == "splitter.config.load.mode" }?.get("value")
                        ?: labels.firstOrNull { it["name"] == "splitterConfigLoadMode" }?.get("value")
                    if (mode != "rest") throw GradleException("Unexpected config-load mode for $task: $mode")
                }
            }
            val expectedClasses = linkedMapOf<String, Int>()
            suites.forEach { s -> (s["classes"] as Map<*, *>).forEach { (c, n) ->
                val key = c as String; expectedClasses[key] = (expectedClasses[key] ?: 0) + (n as Number).toInt()
            } }
            val expectedStatuses = (audit["statuses"] as Map<*, *>).mapValues { (_, v) -> (v as Number).toInt() }
            if (actual != expectedStatuses || classes != expectedClasses || results.size != (audit["total"] as Number).toInt())
                throw GradleException("Task JUnit/Allure mismatch: $task; no upload")
            linkedMapOf("task" to task, "resultCount" to results.size, "statuses" to actual)
        }
    private fun normalized(value: String): String {
        val n = value.trim().lowercase(Locale.ROOT).replace('_', '-')
        return mapOf("eift" to "ift", "ift-ds" to "ift", "eift-ds" to "ift", "eift-dm" to "ift-dm", "localhost" to "local")[n] ?: n
    }
    private fun validateEnvironment(result: Map<*, *>, context: String): Boolean {
        val labels = result["labels"] as? List<*> ?: throw GradleException("Missing Allure environment labels in $context")
        fun labelValues(name: String): Set<String> = labels.filterIsInstance<Map<*, *>>()
            .filter { it["name"] == name }.map { label ->
                val value = label["value"] as? String
                if (value.isNullOrBlank()) throw GradleException("Invalid $name label in $context")
                value.trim()
            }.toSet()
        val actual = labelValues("testEnvironment").map(::normalized).toSet()
        if (actual.isNotEmpty()) {
            if (actual != setOf(environment)) throw GradleException("Allure environment mismatch in $context: $actual; expected $environment")
            return false
        }
        val legacy = labelValues("testStage").map { it.lowercase(Locale.ROOT) }.toSet()
        val expected = when (environment) { "ift-dm" -> "ift"; "local" -> "code"; else -> environment }
        if (legacy.isEmpty()) throw GradleException("Allure result $context has no testEnvironment or legacy testStage label")
        if (legacy != setOf(expected)) throw GradleException("Legacy Allure testStage mismatch in $context: $legacy; expected $expected")
        return true
    }
    private fun attachments(node: Any?, references: MutableSet<String>, context: String, strict: Boolean = true) {
        when (node) {
            is Map<*, *> -> {
                if (strict && node.containsKey("status") && node["status"] !in statuses)
                    throw GradleException("Unsupported Allure status in $context")
                if (strict && node.containsKey("stage") && node["stage"] != "finished")
                    throw GradleException("Unfinished Allure artifact in $context")
                if (node.containsKey("attachments")) {
                    val list = node["attachments"] as? List<*>
                    if (strict && list == null) throw GradleException("Invalid Allure attachments list: $context")
                    list?.forEach { attachment ->
                        if (attachment !is Map<*, *>) {
                            if (strict) throw GradleException("Invalid Allure attachment: $context")
                        } else if (strict || attachment["source"] is String) {
                            references.add(files.flatName(attachment["source"], context))
                        }
                    }
                }
                node.forEach { (key, value) -> if (key != "attachments") attachments(value, references, context, strict) }
            }
            is List<*> -> node.forEach { attachments(it, references, context, strict) }
        }
    }
    private fun recognizedAttachment(name: String) = Regex("[A-Za-z0-9_-]+-attachment(?:\\.[A-Za-z0-9._-]+)?").matches(name)
    private fun withLock(action: () -> Unit) {
        files.unredirected(bundleParent)
        if (!bundleParent.mkdirs() && !bundleParent.isDirectory) throw GradleException("Cannot create TestOps output directory: $bundleParent")
        val lockFile = managed(File(bundleParent, ".$environment.lock"))
        ArtifactFileChannel.open(lockFile.toPath(), ArtifactStandardOpenOption.CREATE, ArtifactStandardOpenOption.WRITE).use { channel ->
            val lock = channel.tryLock() ?: throw GradleException("Another TestOps preparation or cleanup is active for $environment")
            lock.use { action() }
        }
    }
    fun prepare() = withLock {
        managed(bundleDirectory)
        val original = snapshot()
        val junitBefore = junitSnapshot(original)
        val taskEvidence = validateTaskResults(original)
        val scopedResults = original.keys.filter { it.endsWith("-result.json") }.groupBy(::scope)
            .mapValues { (_, names) -> names.map { files.readObject(sourceFile(it))["uuid"] }.toSet() }
        // Resolve references within their original directory before flattening. A root attachment
        // must never mask a missing attachment belonging to another task.
        original.keys.filter { it.endsWith("-result.json") || it.endsWith("-container.json") }.forEach { relative ->
            val refs = linkedSetOf<String>()
            val value = files.readObject(sourceFile(relative))
            attachments(value, refs, relative)
            if (relative.endsWith("-container.json")) files.children(value, relative).forEach { child ->
                if (child !in scopedResults[scope(relative)].orEmpty())
                    throw GradleException("Dangling Allure container child in source scope: $relative -> $child")
            }
            refs.forEach { if (scoped(scope(relative), it) !in original)
                throw GradleException("Missing Allure attachment in source scope: $relative -> $it") }
        }
        val sources = linkedMapOf<String, String>()
        original.forEach { (relative, details) ->
            val name = relative.substringAfterLast('/')
            val previous = sources[name]
            if (previous == null) sources[name] = relative
            else if (name.endsWith("-result.json") || name.endsWith("-container.json") || original[previous] != details)
                throw GradleException("Allure filename collision: $previous and $relative; no overwrite permitted")
        }
        val resultNames = sources.keys.filter { it.endsWith("-result.json") }
        val containerNames = sources.keys.filter { it.endsWith("-container.json") }
        if (resultNames.isEmpty()) throw GradleException("No *-result.json files in $sourceDirectory. Run selected tests first.")
        val suffix = UUID.randomUUID().toString()
        val staging = managed(File(bundleParent, ".$environment-staging-$suffix"))
        val backup = managed(File(bundleParent, ".$environment-backup-$suffix"))
        val stagedRaw = File(staging, "allure-results")
        var previousMoved = false
        var promoted = false
        try {
            if (!stagedRaw.mkdirs()) throw GradleException("Cannot create TestOps staging directory: $stagedRaw")
            sources.forEach { (name, relative) ->
                val details = original.getValue(relative)
                val target = File(stagedRaw, name)
                ArtifactFiles.copy(sourceFile(relative).toPath(), target.toPath())
                if (target.length() != details["size"] || files.fileHash(target) != details["sha256"])
                    throw GradleException("Allure source changed while copying $name")
            }
            val allUuids = hashSetOf<String>()
            val resultUuids = hashSetOf<String>()
            val references = linkedSetOf<String>()
            val statusCounts = ArtifactTreeMap<String, Int>()
            val classes = ArtifactTreeMap<String, Int>()
            val histories = linkedMapOf<String, Int>()
            var earliest = Long.MAX_VALUE
            var latest = 0L
            var legacyCount = 0
            fun registerUuid(value: Map<*, *>, name: String): String {
                val uuid = value["uuid"] as? String
                if (uuid.isNullOrBlank() || !allUuids.add(uuid)) throw GradleException("Missing or duplicate Allure UUID in $name")
                return uuid
            }
            resultNames.forEach { name ->
                val result = files.readObject(File(stagedRaw, name))
                files.assertExecutionKind(result, registrationOnly, name)
                resultUuids.add(registerUuid(result, name))
                if (result["status"] !in statuses || result["stage"] != "finished")
                    throw GradleException("Allure result $name is unfinished or has an invalid status")
                fun timestamp(key: String): Long {
                    val number = files.counter(result[key], "$key timestamp in $name")
                    if (number > ArtifactBigInteger.valueOf(Long.MAX_VALUE)) throw GradleException("Invalid Allure $key timestamp in $name")
                    return number.toLong()
                }
                val start = timestamp("start"); val stop = timestamp("stop")
                if (stop < start) throw GradleException("Allure stop precedes start in $name")
                earliest = minOf(earliest, start); latest = maxOf(latest, stop)
                if (validateEnvironment(result, name)) legacyCount++
                val status = result["status"] as String
                statusCounts[status] = (statusCounts[status] ?: 0) + 1
                val className = (result["labels"] as List<*>).filterIsInstance<Map<*, *>>()
                    .firstOrNull { it["name"] == "testClass" }?.get("value") as? String ?: "(unlabelled)"
                classes[className] = (classes[className] ?: 0) + 1
                val history = result["historyId"] as? String
                if (!history.isNullOrBlank()) histories[history] = (histories[history] ?: 0) + 1
                attachments(result, references, name)
            }
            containerNames.forEach { name ->
                val container = files.readObject(File(stagedRaw, name))
                registerUuid(container, name)
                files.children(container, name).forEach { child ->
                    if (child !is String || child !in resultUuids) throw GradleException("Dangling Allure container child $child in $name")
                }
                attachments(container, references, name)
            }
            references.forEach { if (it !in sources) throw GradleException("Missing Allure attachment: $it") }
            val keep = (resultNames + containerNames + references + sources.keys.filter { it in metadata || recognizedAttachment(it) }).toSet()
            val ignored = sources.keys.filter { it !in keep }
            ignored.forEach { ArtifactFiles.delete(File(stagedRaw, it).toPath()) }
            val environmentFile = File(stagedRaw, "environment.properties")
            if (environmentFile.isFile) {
                val properties = Properties().apply { environmentFile.inputStream().use { load(it) } }
                listOf("env", "environment", "testEnvironment").forEach { key ->
                    val value = properties.getProperty(key)
                    if (value != null && normalized(value) != environment) throw GradleException("Allure environment.properties $key does not match $environment")
                }
            } else environmentFile.writeText("env=$environment\n", Charsets.UTF_8)
            val categories = File(stagedRaw, "categories.json")
            val projectCategories = project.file("allure/categories.json")
            if (!categories.exists() && projectCategories.isFile) ArtifactFiles.copy(projectCategories.toPath(), categories.toPath())
            val duplicateHistories = histories.filterValues { it > 1 }
            if (duplicateHistories.isNotEmpty()) project.logger.warn("TESTOPS ACCUMULATED ATTEMPTS: ${duplicateHistories.size} historyId values repeat; preserving every raw result.")
            if (legacyCount > 0) project.logger.warn("TESTOPS LEGACY ENVIRONMENT: $legacyCount results use testStage only; ift and ift-dm cannot be distinguished.")
            if (ignored.isNotEmpty()) project.logger.warn("TestOps preparation ignored ${ignored.size} non-Allure files; source files were retained.")
            files.writeJson(File(staging, "manifest.json"), linkedMapOf(
                "environment" to environment, "registrationOnly" to registrationOnly, "preparedAt" to ArtifactInstant.now().toString(),
                "sourceDirectory" to sourceDirectory.canonicalPath, "sourceSnapshotSha256" to files.sha256(JsonOutput.toJson(original).toByteArray(Charsets.UTF_8)),
                "sourceFiles" to original, "resultCount" to resultNames.size, "containerCount" to containerNames.size,
                "sourceScopes" to original.keys.map(::scope).toSet().sorted(), "taskEvidence" to taskEvidence,
                "junitSourceFiles" to junitBefore, "flattenedSources" to sources,
                "attachmentCount" to references.size, "statuses" to statusCounts, "classes" to classes,
                "firstResultStart" to earliest, "lastResultStop" to latest, "legacyEnvironmentCount" to legacyCount,
                "environmentEvidence" to when (legacyCount) { 0 -> "testEnvironment"; resultNames.size -> "legacy-testStage"; else -> "testEnvironment-and-legacy-testStage" },
                "duplicateHistoryIds" to duplicateHistories, "ignoredSourceFiles" to ignored,
                "selection" to "all raw results in source; no deduplication or latest-run inference", "destination" to "allure-results"
            ))
            if (snapshot() != original || junitSnapshot(original) != junitBefore)
                throw GradleException("Allure/JUnit source changed during preparation; previous prepared results are retained.")
            if (bundleDirectory.exists()) { ArtifactFiles.move(bundleDirectory.toPath(), backup.toPath()); previousMoved = true }
            ArtifactFiles.move(staging.toPath(), bundleDirectory.toPath()); promoted = true
        } catch (error: Exception) {
            if (promoted && bundleDirectory.exists()) deleteManaged(bundleDirectory)
            if (previousMoved && backup.exists()) ArtifactFiles.move(backup.toPath(), bundleDirectory.toPath(), ArtifactStandardCopyOption.REPLACE_EXISTING)
            throw GradleException("TestOps preparation failed; previous prepared results were retained where possible.", error)
        } finally { deleteManaged(staging) }
        deleteManaged(backup)
        project.logger.lifecycle("Prepared ${resultNames.size} TestOps results for $environment: $preparedDirectory")
        project.logger.lifecycle("Manifest: ${File(bundleDirectory, "manifest.json")}")
        project.logger.lifecycle(if (registrationOnly)
            "REGISTRATION ONLY: no functional coverage. Prior functional raw results are not touched."
        else "Preparation includes every result in the source. Use cleanTestOpsResults before a new selected-test run to avoid accumulation.")
    }
    fun clean() = withLock {
        requireSource(); managed(bundleDirectory)
        val allowed = listOf(project.layout.buildDirectory.dir("allure-results").get().asFile, project.file("allure-results"))
            .map { it.absoluteFile.toPath().normalize() }
        if (sourceDirectory.absoluteFile.toPath().normalize() !in allowed)
            throw GradleException("Refusing automatic cleanup of custom Allure source $sourceDirectory; prepared results were retained.")
        val recognized = linkedSetOf<String>()
        val before = if (sourceDirectory.exists()) snapshot() else ArtifactTreeMap<String, Map<String, Any>>()
        before.keys.forEach { name ->
            val basename = name.substringAfterLast('/')
            if (name.endsWith("-result.json") || name.endsWith("-container.json")) {
                recognized.add(name)
                try {
                    val refs = linkedSetOf<String>()
                    attachments(files.readObject(sourceFile(name)), refs, name, false)
                    recognized.addAll(refs.map { scoped(scope(name), it) })
                }
                catch (error: GradleException) { project.logger.warn("Cleanup could not inspect attachment references in $name; recognized files will still be cleared.") }
            } else if (basename in metadata || recognizedAttachment(basename)) recognized.add(name)
        }
        if (sourceDirectory.exists() && snapshot() != before) throw GradleException("Allure source changed during cleanup inspection. Stop active tests before cleanup.")
        recognized.filter { it in before }.forEach { name -> ArtifactFiles.delete(sourceFile(name).toPath()) }
        deleteManaged(bundleDirectory)
        project.logger.lifecycle("Cleared ${recognized.count { it in before }} recognized raw Allure files and $bundleDirectory")
        project.logger.lifecycle("Regression runs, fixture ownership manifests and unrecognized source files were retained.")
    }
}

class RegressionTestOpsArtifacts(
    private val project: org.gradle.api.Project,
    private val environment: String,
    root: File,
    private val stages: List<String> = listOf("experiment", "splitter-rest", "splitter-kafka", "data-operator", "scheduler-regression"),
    private val bundleName: String = "testops",
    private val requestedRunId: String? = null
) {
    private val files = AllureArtifactFiles()
    private val resultsRoot = root.absoluteFile
    private val buildRoot = project.layout.buildDirectory.get().asFile.absoluteFile
    private val expectedRoot = File(buildRoot, "regression-results/$environment").absoluteFile
    private val prepared = File(resultsRoot, bundleName)
    private val manifest = File(resultsRoot, "$bundleName-manifest.json")
    init {
        if (!Regex("[A-Za-z0-9_-]+").matches(environment) || resultsRoot.toPath().normalize() != expectedRoot.toPath().normalize())
            throw GradleException("Regression results root must be build/regression-results/<environment> inside the project build directory.")
        val supportedStages = setOf("experiment", "splitter-rest", "splitter-kafka", "data-operator", "scheduler-regression")
        if (stages.isEmpty() || stages.distinct().size != stages.size || !supportedStages.containsAll(stages))
            throw GradleException("Expected an explicit nonempty set of supported regression stages.")
        if (bundleName !in setOf("testops", "scheduler-testops"))
            throw GradleException("Unsupported managed regression TestOps bundle name.")
        if (requestedRunId != null && (stages != listOf("scheduler-regression")
                    || !Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,199}").matches(requestedRunId)))
            throw GradleException("schedulerTestOpsRun must be a run-directory ID, not a path; scheduler stage only.")
    }
    private fun requireRoot() {
        files.contained(project.projectDir, resultsRoot); files.contained(buildRoot, resultsRoot)
        val expected = File(project.projectDir.canonicalFile, project.relativePath(expectedRoot)).absoluteFile.toPath().normalize()
        if (resultsRoot.canonicalFile.toPath() != expected || ArtifactFiles.isSymbolicLink(resultsRoot.toPath()))
            throw GradleException("Refusing a redirected regression results directory: $resultsRoot")
    }
    private fun managed(candidate: File): File {
        requireRoot(); files.contained(resultsRoot, candidate)
        if (ArtifactFiles.isSymbolicLink(candidate.toPath())) throw GradleException("Refusing to replace or delete a symbolic link: $candidate")
        return candidate
    }
    private fun attachments(node: Any?, raw: File, context: String) {
        when (node) {
            is Map<*, *> -> {
                if (node.containsKey("attachments")) {
                    val list = node["attachments"] as? List<*> ?: throw GradleException("Invalid attachments list in $context")
                    list.forEach { entry ->
                        val attachment = entry as? Map<*, *> ?: throw GradleException("Invalid attachment in $context")
                        val name = files.flatName(attachment["source"], context)
                        if (!files.contained(raw, File(raw, name)).isFile) throw GradleException("Missing Allure attachment $name referenced by $context")
                    }
                }
                node.forEach { (key, value) -> if (key != "attachments") attachments(value, raw, context) }
            }
            is List<*> -> node.forEach { attachments(it, raw, context) }
        }
    }
    fun prepare() {
        managed(prepared); managed(manifest)
        val copies = linkedMapOf<String, File>()
        val records = mutableListOf<Map<String, Any>>()
        val missing = mutableListOf<String>()
        val latestChecks = mutableListOf<Pair<File, String?>>()
        val sourceChecks = linkedMapOf<File, String>()
        val directoryChecks = linkedMapOf<File, Set<String>>()
        val statusCounts = linkedMapOf<String, Int>()
        val resultUuids = linkedMapOf<String, File>()
        val containerUuids = linkedMapOf<String, File>()
        fun registerUuid(registry: MutableMap<String, File>, value: Any?, artifact: File): String {
            val uuid = value as? String
            if (uuid.isNullOrBlank()) throw GradleException("Missing Allure UUID: $artifact")
            val previous = registry[uuid]
            if (previous != null && (previous.name != artifact.name || !previous.readBytes().contentEquals(artifact.readBytes())))
                throw GradleException("Conflicting Allure UUID $uuid: $previous and $artifact")
            registry[uuid] = artifact
            return uuid
        }
        stages.forEach { stage ->
            val stageDirectory = files.contained(resultsRoot, File(resultsRoot, stage))
            val latestFile = files.contained(stageDirectory, File(stageDirectory, "latest.txt"))
            val relative = if (requestedRunId != null) {
                "runs/$requestedRunId"
            } else {
                if (!latestFile.exists()) { latestChecks.add(latestFile to null); missing.add(stage); return@forEach }
                if (!latestFile.isFile) throw GradleException("Invalid latest-run pointer: $latestFile")
                val content = latestFile.readText(Charsets.UTF_8)
                latestChecks.add(latestFile to content)
                content.trim().replace('\\', '/')
            }
            if (!Regex("runs/[A-Za-z0-9_.-]+").matches(relative) || relative.endsWith("/..") || relative.endsWith("/."))
                throw GradleException("Invalid latest-run path in $latestFile: $relative")
            val run = files.contained(stageDirectory, File(stageDirectory, relative))
            val summaryFile = files.contained(run, File(run, "summary.json"))
            if (!summaryFile.isFile) throw GradleException("Incomplete regression stage $stage: summary.json is missing in $run")
            sourceChecks[summaryFile] = files.fileHash(summaryFile)
            val summary = files.readObject(summaryFile)
            project.logger.lifecycle("TestOps source: env=$environment stage=$stage run=$relative")
            if (summary["completed"] != true || summary["environment"] != environment || summary["stage"] != stage)
                throw GradleException("Incomplete or mismatched regression summary: $summaryFile")
            val counters = listOf("total", "passed", "failed", "skipped").associateWith { files.counter(summary[it], "$it in $summaryFile") }
            if (counters.getValue("passed") + counters.getValue("failed") + counters.getValue("skipped") != counters.getValue("total"))
                throw GradleException("Regression summary counts do not add up: $summaryFile")
            val raw = files.contained(run, File(run, "allure-results"))
            if (!raw.isDirectory) throw GradleException("Missing Allure results for regression stage $stage: $raw")
            val artifacts = raw.listFiles() ?: throw GradleException("Cannot read Allure results: $raw")
            artifacts.forEach {
                files.contained(raw, it)
                if (!it.isFile) throw GradleException("Expected flat Allure results: $it")
                sourceChecks[it] = files.fileHash(it)
            }
            directoryChecks[raw] = artifacts.map { it.name }.toSet()
            val results = artifacts.filter { it.name.endsWith("-result.json") }
            val containers = artifacts.filter { it.name.endsWith("-container.json") }
            if (results.isEmpty()) throw GradleException("Completed regression stage $stage has no Allure test results: $run")
            val countMatches = counters.getValue("total") == ArtifactBigInteger.valueOf(results.size.toLong())
            if (!countMatches) project.logger.warn("REGRESSION COUNT MISMATCH ($stage): Gradle summary=${counters["total"]}, Allure results=${results.size}; preserving all raw results.")
            val localResults = hashSetOf<String>()
            results.forEach { artifact ->
                val result = files.readObject(artifact)
                files.assertExecutionKind(result, false, artifact.name)
                val status = result["status"] as? String ?: "unknown"
                if (status !in setOf("passed", "failed", "broken", "skipped", "unknown"))
                    throw GradleException("Invalid Allure status in ${artifact.name}")
                statusCounts[status] = (statusCounts[status] ?: 0) + 1
                localResults.add(registerUuid(resultUuids, result["uuid"], artifact)); attachments(result, raw, artifact.name)
            }
            containers.forEach { artifact ->
                val container = files.readObject(artifact); registerUuid(containerUuids, container["uuid"], artifact)
                files.children(container, artifact.name).forEach { if (it !is String || it !in localResults) throw GradleException("Dangling Allure container child $it: $artifact") }
                attachments(container, raw, artifact.name)
            }
            artifacts.filter { it.name !in setOf("categories.json", "environment.properties") }.forEach { artifact ->
                val previous = copies[artifact.name]
                if (previous != null && !previous.readBytes().contentEquals(artifact.readBytes())) throw GradleException("Different Allure files have the same name: $previous and $artifact")
                copies[artifact.name] = artifact
            }
            records.add(linkedMapOf("stage" to stage, "run" to relative, "results" to results.size, "containers" to containers.size, "junitAllureCountMatches" to countMatches, "summary" to summary,
                "selection" to if (requestedRunId == null) "latest-started-completed" else "explicit-run-id"))
        }
        if (records.isEmpty()) throw GradleException("No completed regression results for $environment. Run a regression stage first.")
        if (resultUuids.keys.intersect(containerUuids.keys).isNotEmpty()) throw GradleException("An Allure UUID is shared by a test result and a container.")
        if (missing.isNotEmpty()) project.logger.warn("PARTIAL REGRESSION REPORT ($environment): missing stages: ${missing.joinToString(", ")}")
        val suffix = UUID.randomUUID().toString()
        val staging = managed(File(resultsRoot, ".testops-staging-$suffix"))
        val backup = managed(File(resultsRoot, ".testops-backup-$suffix"))
        val manifestStaging = managed(File(resultsRoot, ".testops-manifest-$suffix.json"))
        val manifestBackup = managed(File(resultsRoot, ".testops-manifest-backup-$suffix.json"))
        var promoted = false; var oldDirectoryMoved = false; var oldManifestMoved = false
        try {
            if (!staging.mkdirs() && !staging.isDirectory) throw GradleException("Cannot create prepared results directory: $staging")
            copies.forEach { (name, source) -> ArtifactFiles.copy(source.toPath(), File(staging, name).toPath()) }
            val categories = project.file("allure/categories.json")
            if (categories.isFile) ArtifactFiles.copy(categories.toPath(), File(staging, "categories.json").toPath())
            File(staging, "environment.properties").writeText("env=$environment\n", Charsets.UTF_8)
            files.writeJson(manifestStaging, linkedMapOf("environment" to environment, "preparedAt" to ArtifactInstant.now().toString(), "partial" to missing.isNotEmpty(),
                "missingStages" to missing, "sources" to records, "resultCount" to resultUuids.size,
                "containerCount" to containerUuids.size, "statuses" to statusCounts, "destination" to bundleName,
                "sourceFiles" to sourceChecks.map { (source, hash) ->
                    linkedMapOf("path" to source.relativeTo(resultsRoot).invariantSeparatorsPath, "sha256" to hash)
                }))
            latestChecks.forEach { (pointer, content) ->
                val changed = if (content == null) pointer.exists() else !pointer.isFile || pointer.readText(Charsets.UTF_8) != content
                if (changed) throw GradleException("A regression stage changed during preparation: $pointer")
            }
            directoryChecks.forEach { (raw, expectedNames) ->
                val currentNames = raw.listFiles()?.map { it.name }?.toSet()
                if (currentNames != expectedNames) throw GradleException("Allure files changed during preparation: $raw")
            }
            sourceChecks.forEach { (source, hash) ->
                if (!source.isFile || files.fileHash(source) != hash)
                    throw GradleException("Regression source changed during preparation: $source")
            }
            copies.forEach { (name, source) ->
                if (files.fileHash(File(staging, name)) != sourceChecks.getValue(source))
                    throw GradleException("Prepared Allure copy does not match the source: $name")
            }
            if (prepared.exists()) { ArtifactFiles.move(prepared.toPath(), backup.toPath()); oldDirectoryMoved = true }
            if (manifest.exists()) { ArtifactFiles.move(manifest.toPath(), manifestBackup.toPath()); oldManifestMoved = true }
            ArtifactFiles.move(staging.toPath(), prepared.toPath()); promoted = true
            ArtifactFiles.move(manifestStaging.toPath(), manifest.toPath())
        } catch (error: Exception) {
            if (promoted && prepared.exists()) project.delete(managed(prepared))
            if (oldDirectoryMoved && backup.exists()) ArtifactFiles.move(backup.toPath(), prepared.toPath())
            if (oldManifestMoved && manifestBackup.exists()) ArtifactFiles.move(manifestBackup.toPath(), manifest.toPath(), ArtifactStandardCopyOption.REPLACE_EXISTING)
            throw GradleException("Could not prepare regression TestOps results; previous prepared results were retained where possible.", error)
        } finally { project.delete(managed(staging), managed(manifestStaging)) }
        project.delete(managed(backup), managed(manifestBackup))
        project.logger.lifecycle("Prepared ${resultUuids.size} Allure results for $environment: $prepared")
        project.logger.lifecycle("Preserved Allure statuses: $statusCounts")
        project.logger.lifecycle("Regression preparation manifest: $manifest")
    }
    fun clean() {
        requireRoot(); project.delete(resultsRoot)
        project.logger.lifecycle("Cleared all regression results for $environment: $resultsRoot")
        project.logger.lifecycle("Fixture ownership manifests remain in build/regression-fixtures; the standard clean task removes the entire build directory.")
    }
}



val artifactFileOperations = AllureArtifactFiles()

fun selectedArtifactActions(environment: String, input: File, registration: Boolean): Pair<() -> Unit, () -> Unit> {
    val artifacts = SelectedTestOpsArtifacts(project, environment, input, registration)
    return Pair({ artifacts.prepare() }, { artifacts.clean() })
}
fun regressionArtifactActions(environment: String, root: File): Pair<() -> Unit, () -> Unit> {
    val artifacts = RegressionTestOpsArtifacts(project, environment, root)
    return Pair({ artifacts.prepare() }, { artifacts.clean() })
}

// Explicit cross-script contract; task actions remain lazy.
buildContext["artifactUnredirected"] = { file: File -> artifactFileOperations.unredirected(file) }
buildContext["artifactContained"] = { boundary: File, candidate: File -> artifactFileOperations.contained(boundary, candidate) }
buildContext["artifactWriteJson"] = { file: File, value: Any -> artifactFileOperations.writeJson(file, value) }
buildContext["artifactReadObject"] = { file: File -> artifactFileOperations.readObject(file) }
buildContext["artifactAssertExecutionKind"] = { result: Map<*, *>, registration: Boolean, context: String -> artifactFileOperations.assertExecutionKind(result, registration, context) }
buildContext["artifactFileHash"] = { file: File -> artifactFileOperations.fileHash(file) }
buildContext["artifactCounter"] = { value: Any?, context: String -> artifactFileOperations.counter(value, context) }
buildContext["selectedArtifactActions"] = { environment: String, input: File, registration: Boolean -> selectedArtifactActions(environment, input, registration) }
buildContext["selectedArtifactActionsAt"] = { environment: String, input: File, output: File ->
    artifactFileOperations.contained(project.layout.buildDirectory.get().asFile, output)
    val artifacts = SelectedTestOpsArtifacts(project, environment, input, false, output)
    Pair({ artifacts.prepare() }, { artifacts.clean() })
}
buildContext["regressionArtifactActions"] = { environment: String, root: File -> regressionArtifactActions(environment, root) }
