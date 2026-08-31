package ru.sber.qa.splitter.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks Kafka config-load tests that require ConfigResultMessage/status topic support. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface KafkaConfigStatusRequiredOnly {
}
