package config.services.core;

import org.aeonbits.owner.ConfigFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public interface CustomTestConfigScope {
    CustomTestConfig TEST_CONFIG = create();

    private static CustomTestConfig create() {
        CustomTestConfig delegate = ConfigFactory.create(CustomTestConfig.class);
        return (CustomTestConfig) Proxy.newProxyInstance(CustomTestConfig.class.getClassLoader(),
                new Class<?>[]{CustomTestConfig.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("env") && method.getParameterCount() == 0) {
                        return TestEnvironment.current();
                    }
                    try {
                        return method.invoke(delegate, arguments);
                    } catch (InvocationTargetException error) {
                        throw error.getCause();
                    }
                });
    }
}
