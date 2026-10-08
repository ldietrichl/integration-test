@file:Suppress("UNCHECKED_CAST")


@Suppress("UNCHECKED_CAST")
val buildContext = extra["platformBuildContext"] as MutableMap<String, Any?>



// Reject old entry points without silently choosing a different phase or running an alias.
val retiredTaskReplacements = linkedMapOf(
    "schedulerReadOnlyRegression" to "schedulerRegression -Pscheduler.regression.phase=read-only",
    "schedulerFixturesRegression" to "schedulerRegression -Pscheduler.regression.phase=fixtures",
    "schedulerJobsRegression" to "schedulerRegression -Pscheduler.regression.phase=jobs",
    "schedulerFullRegression" to "schedulerRegression",
    "schedulerServiceRegression" to "schedulerRegression",
    "schedulerManagedRegression" to "schedulerRegression -Pscheduler.regression.phase=fixtures",
    "schedulerRegressionTestOpsUpload" to "schedulerTestOpsUpload",
    "prepareSchedulerRegressionTestOpsResults" to "prepareSchedulerTestOpsResults",
    "prepareSplitterRegressionLogs" to "splitterRestDebug / splitterKafkaDebug (logs are created automatically)",
    "explab2972StandTest" to "explab2972Test"
).apply {
    listOf("schedulerReadOnlyRegression", "schedulerFixturesRegression",
        "schedulerJobsRegression", "schedulerFullRegression").forEach {
        put(it + "Eligibility", "schedulerRegressionEligibility with scheduler.regression.phase")
    }
}
gradle.startParameter.taskNames.forEach { requested ->
    retiredTaskReplacements[requested.substringAfterLast(':')]?.let { replacement ->
        throw GradleException("Task '$requested' was removed. Use: $replacement")
    }
}
