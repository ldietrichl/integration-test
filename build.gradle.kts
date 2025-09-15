import com.google.protobuf.gradle.id

// Версии зависимостей
val platformvatframeworkVersion = project.properties["platformvatframeworkVersion"] as String?

val javaxAnnotationVersion = project.properties["javaxAnnotationVersion"] as String?

val slf4jVersion = project.properties["slf4jVersion"] as String?
val awaitilityVersion = project.properties["awaitilityVersion"] as String?
val mockitoVersion = project.properties["mockitoVersion"] as String?

plugins {
    java
    id("io.qameta.allure") version "2.11.2"

    //для gRPC
    id("com.google.protobuf") version "0.9.4"
}

// Задаем координаты проекта - группу и версию
group = "ru.sber.qa.examples"
version = "0.0.1"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// Получаем ссылки на репозитории из файла проекта gradle.properties
val nexusPublicRepository: String by rootProject
val nexusInternalRepository: String by rootProject

// Получаем значения tokenName и tokenPassword из /Users/<username>/.gradle/gradle.properties
val tokenName = project.properties["tokenName"] as String?
val tokenPassword = project.properties["tokenPassword"] as String?

repositories {
    listOf(nexusPublicRepository, nexusInternalRepository).forEach {
        maven {
            url = uri(it)
            isAllowInsecureProtocol = true
            credentials {
                username = this@Build_gradle.tokenName
                password = this@Build_gradle.tokenPassword
            }
        }
    }
}

dependencies {
    // Подключаем зависимость для работы с JUnit5
    implementation(group = "io.perfeccionista.framework", name = "environment-junit5", version = "0.9.1-Beta")
    // Подключаем зависимость для работы с Cucumber
    implementation(group = "ru.sber.qa.platform-v-at-framework", name = "environment-cucumber7", version = "$platformvatframeworkVersion")
    // todo временная зависимость до след релиза(убрать в новом релизе > 0.0.21)
    implementation(group = "io.cucumber", name = "cucumber-junit", version = "7.20.1")
    // database - модуль для работы с REST_API
    implementation(group = "ru.sber.qa.platform-v-at-framework", name = "api", version = "$platformvatframeworkVersion")
    // pacman - модуль для работы с СУП-параметрами в ЕФС
    implementation(group = "ru.sber.qa.platform-v-at-framework", name = "pacman", version = "$platformvatframeworkVersion")
    // database - модуль для работы с Базами данных
    implementation(group = "ru.sber.qa.platform-v-at-framework", name = "database", version = "$platformvatframeworkVersion")
    // kafka - модуль для работы Kafka
    implementation(group = "ru.sber.qa.platform-v-at-framework", name = "kafka", version = "$platformvatframeworkVersion")
    // session - модуль для работы с сессиями
    implementation(group = "ru.sber.qa.platform-v-at-framework", name = "session", version = "$platformvatframeworkVersion")
    // sbermock - модуль для работы с SberMock API
    implementation(group = "ru.sber.qa.platform-v-at-framework", name = "sbermock", version = "$platformvatframeworkVersion")
    //gRPC - модуль для работы с gRPC
    implementation(group = "ru.sber.qa.platform-v-at-framework", name = "grpc", version = "$platformvatframeworkVersion")
    //container - модуль для работы с OpenShift или Kubernetes
    implementation(group = "ru.sber.qa.platform-v-at-framework", name = "containers", version = "$platformvatframeworkVersion")
    //audit - модуль для работы с uAudit(единый аудит)
    implementation(group = "ru.sber.qa.platform-v-at-framework", name = "audit", version = "$platformvatframeworkVersion")
    //graphql - модуль для работы с GraphQL
    implementation(group = "ru.sber.qa.platform-v-at-framework", name = "graphql", version = "$platformvatframeworkVersion")
    //opensearch - модуль для работы с OpenSearch
    implementation(group = "ru.sber.qa.platform-v-at-framework", name = "opensearch", version = "$platformvatframeworkVersion")
    //journal - модуль для работы с Журналами ЕФС и ППРБ
    implementation(group = "ru.sber.qa.platform-v-at-framework", name = "journal", version = "$platformvatframeworkVersion")
    //metrics - модуль для работы с Хранилищем метрик VictoriaMetrics и Thanos
    implementation(group = "ru.sber.qa.platform-v-at-framework", name = "metrics", version = "$platformvatframeworkVersion")

    //allure2 - модуль для работы с Allure2
    implementation(group = "ru.sber.qa.platform-v-at-framework", name = "allure2", version = "$platformvatframeworkVersion")
    // Подключаем логирование для проекта
    implementation(group = "org.slf4j", name = "slf4j-simple", version = "$slf4jVersion")

    implementation(group = "org.awaitility", name = "awaitility", version = "$awaitilityVersion")

    // Зависимости для демонстрационных тестов проекта, при настройке проекта возможно удалить
    testImplementation(group = "org.mockito", name = "mockito-junit-jupiter", version = "$mockitoVersion")
    testImplementation(group = "org.mockito", name = "mockito-inline", version = "$mockitoVersion")

    // Зависимости для тестконтейнеров
    testImplementation("org.testcontainers:kafka:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")

    //grpc settings
        if (JavaVersion.current().isJava9Compatible) {
        // Workaround for @javax.annotation.Generated
        // see: https://github.com/grpc/grpc-java/issues/3633
        implementation(group = "javax.annotation", name = "javax.annotation-api", version = "$javaxAnnotationVersion")
    }
    //graphql settings
    implementation("com.graphql-java:graphql-java:17.1")
}

// Настраиваем Allure-plugin для локальных отчетов
allure {
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

tasks.register<Copy>("copyAllureCategories") {
    delete("${buildDir}/copy-categories/")
    from("${projectDir}/allure/categories.json")
    into("${buildDir}/allure-results/")
}

tasks {
    // Для компиляции ставим кодировку UTF-8
    withType<JavaCompile> {
        options.encoding= "UTF-8"
    }
    // Для тестов внутри проекта используем платформу JUnit
    withType<Test> {
        useJUnitPlatform()
        testLogging.showStandardStreams = true
        finalizedBy("copyAllureCategories")
//            testLogging {
//                events("PASSED", "SKIPPED", "FAILED", "STANDARD_OUT", "STANDARD_ERROR")
//            }
    }
}

// Настраиваем генерацию Java классов из .proto файла(только для модуля grpc)
sourceSets {
    main {
        proto {
            // In addition to the default 'src/main/proto'
            srcDir ("src/main/resources/proto") // Your existing source directory
            srcDir ("src/test/resources/proto")
         }
//        java {
//
//        }
    }
//    test {
//        proto {
//            // In addition to the default 'src/test/proto'
//        }
//    }
}

protobuf {
    protoc {
        // The artifact spec for the Protobuf Compiler
        artifact = "com.google.protobuf:protoc:4.29.3"
    }

    plugins {
        // Optional: an artifact spec for a protoc plugin, with "grpc" as
        // the identifier, which can be referred to in the "plugins"
        // container of the "generateProtoTasks" closure.
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.69.0"
        }

    }

    generateProtoTasks {
        ofSourceSet("main").forEach {
            it.plugins {
                // Apply the "grpc" plugin whose spec is defined above, without
                // options. Note the braces cannot be omitted, otherwise the
                // plugin will not be added. This is because of the implicit way
                // NamedDomainObjectContainer binds the methods.
                id("grpc") {

                }
            }
        }
    }
}
