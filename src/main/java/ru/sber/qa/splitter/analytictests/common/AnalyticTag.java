package ru.sber.qa.splitter.analytictests.common;

import io.qameta.allure.LabelAnnotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allure tag that binds an autotest to the analyst matrix column
 * "Детали проверки Заново сформулировано".
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@LabelAnnotation(name = "tag")
public @interface AnalyticTag {
    String value();
}
