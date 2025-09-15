rootProject.name = "platform-v-at-gradle-draft"

// Инициализируем репозитории для получения плагинов, которые используются в build.gradle.kts
pluginManagement {
    repositories {
        // Получаем значения tokenName и tokenPassword из /Users/<username>/.gradle/gradle.properties
        val tokenName: String by settings
        val tokenPassword: String by settings
        // Получаем ссылки на репозитории из файла проекта gradle.properties
        val nexusPublicRepository: String by settings
        val nexusInternalRepository: String by settings
        // Инициализируем репозитории для скачивания плагинов
        listOf(nexusPublicRepository, nexusInternalRepository).forEach {
            maven {
                url = uri(it)
                isAllowInsecureProtocol = true
                credentials {
                    username = tokenName
                    password = tokenPassword
                }
            }
        }

    }
}
