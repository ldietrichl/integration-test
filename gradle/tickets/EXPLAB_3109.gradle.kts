@file:Suppress("UNCHECKED_CAST")

import org.gradle.api.tasks.testing.Test

val buildContext = extra["platformBuildContext"] as MutableMap<String, Any?>

val links3109Class = "ru.sber.qa.experiments.EXPLAB_3109.ExperimentLinksCore3109FlowTest"
val links3109Api = listOf(1, 9, 18, 60, 61).map { "$links3109Class.test" + it.toString().padStart(2, '0') }
val links3109Kafka = listOf(22, 23, 26, 27, 28, 29, 30).map { "$links3109Class.test" + it.toString().padStart(2, '0') }
val links3109Extended = listOf(28, 29, 30).map { "$links3109Class.test" + it.toString().padStart(2, '0') }
val links3109Selections = linkedMapOf(
    "explab3109Core" to (listOf(links3109Class) to "12 проверок: два API и события Kafka ExpLab"),
    "explab3109Api" to (links3109Api to "5 проверок POST запуска и GET очереди без Kafka"),
    "explab3109Kafka" to (links3109Kafka to "7 проверок настоящих событий Kafka ExpLab"),
    "explab3109Extended" to (links3109Extended to "3 новых сценария: повтор, удаление и две разные задачи"),
    "explab3109ExistingData" to (links3109Api to "Совместимое имя набора 5 API-проверок; Kafka запускается отдельно")
)
links3109Selections.forEach { (taskName, selection) ->
    tasks.register<Test>(taskName)
    // Все задачи запускают REST-команду; Kafka здесь — независимый наблюдатель, а не способ загрузки конфигурации.
    (buildContext["configureTicketRegression"] as (String, String, String, List<String>, String) -> Unit)(
        taskName, "experiment", "rest", selection.first, "current")
    tasks.named<Test>(taskName) {
        group = "functional"
        description = "EXPLAB-3109: " + selection.second
        systemProperty("run.expected.env", "ift")
        systemProperty("junit.jupiter.execution.parallel.enabled", "false")
        doFirst {
            require((buildContext["normalizedFileEnvironment"] as () -> String)() == "ift") {
                "EXPLAB-3109 требует env=ift в src/test/resources/test.properties"
            }
        }
    }
}

gradle.taskGraph.whenReady {
    if (allTasks.any { it.name in links3109Selections.keys }) {
        require(allTasks.count { it.name in links3109Selections.keys } == 1) {
            "Выберите один набор EXPLAB-3109 за запуск"
        }
        require(allTasks.none { it.name in setOf("experimentServiceRegression", "test", "clean", "cleanRegressionResults", "cleanAllureResults") }) {
            "Запускайте набор EXPLAB-3109 отдельно от полного регресса и очистки результатов"
        }
    }
}
