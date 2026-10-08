@file:Suppress("UNCHECKED_CAST")


@Suppress("UNCHECKED_CAST")
val buildContext = extra["platformBuildContext"] as MutableMap<String, Any?>

val perfeccionistaVersion get() = buildContext["perfeccionistaVersion"] as String?
val platformvatframeworkVersion get() = buildContext["platformvatframeworkVersion"] as String?
val slf4jVersion get() = buildContext["slf4jVersion"] as String?
val awaitilityVersion get() = buildContext["awaitilityVersion"] as String?
val mockitoVersion get() = buildContext["mockitoVersion"] as String?
val useLocalLibs get() = buildContext["useLocalLibs"] as Boolean
val localLibJars get() = buildContext["localLibJars"] as org.gradle.api.file.FileTree
fun optionalLocalProperty(name: String): String? = (buildContext["optionalLocalProperty"] as (String) -> String?)(name)

// Получаем ссылки на репозитории из файла проекта gradle.properties
val nexusPublicRepository: String by rootProject
val nexusInternalRepository: String by rootProject

// Credentials are read only from the ignored secure.local.override.properties.
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
        add("implementation", localLibJars)
    } else {
        // Подключаем зависимость для работы с JUnit5
        add("implementation", "io.perfeccionista.framework:environment-junit5:$perfeccionistaVersion")
        // api - модуль для работы с REST_API
        add("implementation", "ru.sber.qa.platform-v-at-framework:api:$platformvatframeworkVersion")
        // database - модуль для работы с Базами данных
        add("implementation", "ru.sber.qa.platform-v-at-framework:database:$platformvatframeworkVersion")
        // kafka - модуль для работы Kafka
        add("implementation", "ru.sber.qa.platform-v-at-framework:kafka:$platformvatframeworkVersion")
        // session - модуль для работы с сессиями
        add("implementation", "ru.sber.qa.platform-v-at-framework:session:$platformvatframeworkVersion")
        //container - модуль для работы с OpenShift или Kubernetes
        add("implementation", "ru.sber.qa.platform-v-at-framework:containers:$platformvatframeworkVersion")
        // Native workload management owns the client and needs an explicit HTTP provider.
        add("implementation", "io.fabric8:kubernetes-client:6.13.4")
        add("runtimeOnly", "io.fabric8:kubernetes-httpclient-okhttp:6.13.4")

        //allure2 - модуль для работы с Allure2
        add("implementation", "ru.sber.qa.platform-v-at-framework:allure2:$platformvatframeworkVersion")
    }

    // database - клиент postgresql
    add("implementation", "org.postgresql:postgresql:42.7.7")
    // Fabric8/container framework is incompatible with the Jackson 2.19.x selected transitively on the VDI.
    // Enforce one tested Jackson line for compile and runtime diagnostics.
    add("implementation", enforcedPlatform("com.fasterxml.jackson:jackson-bom:2.17.2"))
    add("implementation", "com.fasterxml.jackson.core:jackson-annotations")
    add("implementation", "com.fasterxml.jackson.core:jackson-core")
    add("implementation", "com.fasterxml.jackson.core:jackson-databind")
    add("implementation", "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    add("implementation", "io.qameta.allure:allure-java-commons:2.29.0")
    add("implementation", "io.qameta.allure:allure-rest-assured:2.29.0")
    add("implementation", "org.apache.httpcomponents:httpcore:4.4.16")
    add("implementation", "org.apache.httpcomponents:httpclient:4.5.14")
    add("implementation", "org.apache.httpcomponents:httpmime:4.5.14")
    add("implementation", "org.apache.commons:commons-lang3:3.14.0")
    add("compileOnly", "org.junit.jupiter:junit-jupiter-api:5.10.2")
    add("testImplementation", "org.junit.jupiter:junit-jupiter-api:5.10.2")
    add("testImplementation", "io.qameta.allure:allure-junit5:2.29.0")
    add("testImplementation", "org.junit.jupiter:junit-jupiter-params:5.10.2")
    add("implementation", "org.apache.kafka:kafka-clients:3.7.1")
    add("implementation", "org.eclipse.jgit:org.eclipse.jgit:6.10.0.202406032230-r")
    add("implementation", "org.apache.groovy:groovy:4.0.22")
    add("testRuntimeOnly", "org.junit.jupiter:junit-jupiter-engine:5.10.2")
    add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher:1.10.2")

    // Подключаем логирование для проекта
    add("implementation", "org.slf4j:slf4j-simple:$slf4jVersion")

    add("implementation", "org.awaitility:awaitility:$awaitilityVersion")

    add("implementation", "org.mockito:mockito-junit-jupiter:$mockitoVersion")
    add("implementation", "org.mockito:mockito-inline:$mockitoVersion")

    add("compileOnly", "org.projectlombok:lombok:1.18.30")
    add("annotationProcessor", "org.projectlombok:lombok:1.18.30")

    add("testCompileOnly", "org.projectlombok:lombok:1.18.30")
    add("testAnnotationProcessor", "org.projectlombok:lombok:1.18.30")
    if (!useLocalLibs) {
        add("implementation", "ru.sber.qa.platform-v-at-framework:sbermock:$platformvatframeworkVersion")
    }

}


// Explicit dependency for secure property decryption.
dependencies {
    add("implementation", "org.jasypt:jasypt:1.9.3")
}
