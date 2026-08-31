package ru.sber.qa.splitter.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks tests that are eligible only for explicit splitter stand profiles. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface SplitterTestProfileOnly {
    String[] value();
}
