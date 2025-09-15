@SetEnvironmentConfiguration(ru.sber.qa.config.ApiEnvironmentConfiguration)
Feature: Book rental

  Background:
    Given a student of name "Ivan" and with student id 123456
    And a book of title "My favourite book"

  Scenario: No rental by default
    When "Ivan" requests his number of rentals
    Then There is 0 in his number of rentals

  Scenario: a book rental
    When "Ivan" rents the book "My favourite book"
    Then There is 1 in his number of rentals
      And The book "My favourite book" is in a rental in the list of rentals
      And The book "My favourite book" is unavailable