rootProject.name = "ABTM"

// Инициализируем репозитории для получения плагинов, которые используются в build.gradle.kts
pluginManagement {
    repositories {
        val gradleLocalProperties = java.util.Properties()
        val gradleLocalPropertiesFile = file("gradle.local.properties")
        if (gradleLocalPropertiesFile.isFile) {
            gradleLocalPropertiesFile.inputStream().use { gradleLocalProperties.load(it) }
        }

        val secureLocalProperties = java.util.Properties()
        listOf(file("secure.local.properties"), file("secure.local.override.properties"))
            .filter { it.isFile }
            .forEach { secureFile -> secureFile.inputStream().use { secureLocalProperties.load(it) } }

        fun usableSettingProperty(value: String?): String? =
            value?.trim()
                ?.takeUnless { it.isBlank() }
                ?.takeUnless { it.startsWith("<SET_ME_") && it.endsWith(">") }

        fun optionalSettingProperty(name: String): String? =
            usableSettingProperty(providers.gradleProperty(name).orNull)
                ?: usableSettingProperty(gradleLocalProperties.getProperty(name))
                ?: usableSettingProperty(secureLocalProperties.getProperty(name))
                ?: usableSettingProperty(System.getenv(name))

        // Получаем значения tokenName и tokenPassword из /Users/<username>/.gradle/gradle.properties,
        // -P параметров, ignored gradle.local.properties, secure.local.* или legacy-полей проекта.
        val tokenName = optionalSettingProperty("tokenName")
            ?: optionalSettingProperty("nexusUserSigma")
            ?: ""
        val tokenPassword = optionalSettingProperty("tokenPassword")
            ?: optionalSettingProperty("nexusPasswordSigma")
            ?: ""
        // Получаем ссылки на репозитории из файла проекта gradle.properties
        val nexusPublicRepository: String by settings
        val nexusInternalRepository: String by settings
        val nexusInternalReleaseRepository: String by settings
        val nexusInternalDevRepository: String by settings
        // Инициализируем репозитории для скачивания плагинов
        listOf(nexusPublicRepository, nexusInternalRepository, nexusInternalReleaseRepository, nexusInternalDevRepository).forEach {
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
