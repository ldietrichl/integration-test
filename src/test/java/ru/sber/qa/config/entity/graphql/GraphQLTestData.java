package ru.sber.qa.config.entity.graphql;

/**
 * @deprecated Данный класс является источником синтетических тестовых данных для демонстрационных тестов и
 * неприменим в реальных тестах.
 */
public record GraphQLTestData(String rqUid,String rqTm, String ticker, String classCode, String exchange,
                              AppData appData) {
}