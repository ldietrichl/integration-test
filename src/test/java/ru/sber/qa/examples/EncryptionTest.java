package ru.sber.qa.examples;

import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.sber.qa.config.ApiEnvironmentConfiguration;
import ru.sber.qa.services.configuration.converters.encrypt.PropertyCryptComponent;

import static ru.sber.qa.config.properties.TestConfigScope.TEST_CONFIG;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(ApiEnvironmentConfiguration.class)
public class EncryptionTest {

    /**
     * Пример шифрования паролей.
     */
    @Test
    void getDecryptedValueTest() {
        PropertyCryptComponent cryptComponent = new PropertyCryptComponent();
        // придумываем пароль для шифрования и дальнейшего дешифрования значений в properties
        cryptComponent.setJasyptCryptPassword("dfhfhds423Sg2");

        // зашифровываем значение, которое хотим безопасно хранить в properties
        // после получения в консоли переносим зашифрованное значение в файл .properties
        // обертка ENC() необходима для корректной работы дешифровщика
        String encryptedValue = cryptComponent.encryptString("password");
        System.out.println("Зашифрованный пароль: ENC(" + encryptedValue + ")");
    }

    /**
     * Пример дешифрования паролей.
     */
    @Test
    void getEncryptedValueTest() {
        // !ВАЖНО! Для дешифрования требуется в переменные окружения добавить encryption.password,
        // использованный при шифровании паролей
        // для явного примера указано через System.setProperty(), в своих тестах так не делать!
        // Запускать через -Dencryption.password=...
        // Для детального изучения работы дешифровщика рекомендуется посмотреть документацию
        // https://stash.delta.sbrf.ru/projects/SWATS/repos/platform-v-at-framework/browse/docs/core.md
        System.setProperty("encryption.password", "dfhfhds423Sg2");
        System.out.println("KeystorePass: " + TEST_CONFIG.keystorePass() + "\nTrustorePass: " + TEST_CONFIG.truststorePass());
    }

}
