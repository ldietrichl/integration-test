package ru.sber.qa.allure;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a stable test as critical regression.
 *
 * <p>The Allure listener adds both {@code critical-regress} and {@code regress} tags and the
 * corresponding TestOps custom fields.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface CriticalRegression {
}
