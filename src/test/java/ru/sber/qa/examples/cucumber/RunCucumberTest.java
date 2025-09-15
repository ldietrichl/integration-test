package ru.sber.qa.examples.cucumber;

import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
import org.junit.runner.RunWith;

/**
 * Для работы с cucumber необходимо добавить зависимости в pom.xml и запустить тест из runner.
 * При запуске через runner необходимо явно указать пакеты до StepDefenition и Hooks в параеметре glue.
 * Пакет "ru.sber.qa.cucumber" - пакет, содержащий CucumberHooks для работы с Environment.
 */
@RunWith(value = Cucumber.class)
@CucumberOptions(
        plugin = {"pretty"},
        features = "src/test/resources/features",
        // При запуске через runner необходимо явно указать пакеты до StepDefenition и Hooks в параеметре glue.
        // В данном примере 1 параметр - пакет до StepDefenition, второй - до Hooks(обязательно!).
        glue = {"ru.sber.qa.examples.cucumber", "ru.sber.qa.cucumber"})
public class RunCucumberTest {
    // will run all features found on the classpath
    // in the same package as this class
}