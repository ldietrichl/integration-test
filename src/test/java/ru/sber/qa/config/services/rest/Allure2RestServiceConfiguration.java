package ru.sber.qa.config.services.rest;

import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.specification.RequestSpecification;
import org.slf4j.event.Level;
import ru.sber.qa.services.rest.DefaultRestServiceConfiguration;
import ru.sber.qa.services.rest.filters.RequestResponseConsoleLoggingFilter;


public class Allure2RestServiceConfiguration extends DefaultRestServiceConfiguration {

    @Override
    public RequestSpecification requestSpecification() {
        return super.requestSpecification()
                .filter(new AllureRestAssured())
                .filter(new RequestResponseConsoleLoggingFilter(Level.WARN));
    }

}
