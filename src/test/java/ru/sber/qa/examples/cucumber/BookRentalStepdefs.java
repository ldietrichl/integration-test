package ru.sber.qa.examples.cucumber;


import biblio.Bibliotheque;
import biblio.Etudiant;
import biblio.Livre;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.perfeccionista.framework.Environment;
import io.qameta.allure.Step;
import org.apache.http.HttpStatus;
import ru.sber.qa.services.rest.RestClient;
import ru.sber.qa.services.rest.RestService;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import static io.perfeccionista.framework.datasource.Stash.stash;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ru.sber.qa.matchers.RestMatchers.haveBodyWithText;
import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;
import static ru.sber.qa.matchers.RestMatchers.notHaveEmptyBody;
import static ru.sber.qa.matchers.XmlMatchers.haveXmlValue;
import static ru.sber.qa.matchers.XmlMatchers.haveXmlValueContains;
import static ru.sber.qa.matchers.XmlMatchers.haveXmlValueEqualTo;
import static ru.sber.qa.matchers.conditions.TextConditions.equalToText;

public class BookRentalStepdefs {

    Bibliotheque biblio = new Bibliotheque();
    Etudiant etudiant;
    Livre livre;

    public BookRentalStepdefs() {
    }

    @Step("Проверяем наличие книги в каталоге")
    @Given("a student of name {string} and with student id {int}")
    public void givenAStudent(String nomEtudiant, Integer noEtudiant) {

        System.out.println("given " + Environment.getForCurrentThread().getService(RestService.class).restClient().toString());

        stash().put("my_variable", "my_value");
        etudiant = new Etudiant(biblio);
        etudiant.setNom(nomEtudiant);
        etudiant.setNoEtudiant(noEtudiant);
        biblio.addEtudiant(etudiant);
    }

    @And("a book of title {string}")
    public void andABook(String titreLivre) {
        step("And a book", () -> {

                    System.out.println(stash().get("my_variable"));

                    Livre liv = new Livre(biblio);
                    liv.setTitre(titreLivre);
                    biblio.addLivre(liv);
                }
        );
    }


    @Then("There is {int} in his number of rentals")
    public void thenNbRentals(Integer nbEmprunts) {
        RestClient restClient = Environment.getForCurrentThread().getService(RestService.class).restClient();

        step("Отправляем тестовый запрос", () -> {
                    ValidatableResponseWrapper response = restClient.get(spec -> spec,
                            "http://restpvatftest.sbermock.sigma.sbrf.ru/xmlResponse");

                    response.should(
                                    haveStatusCode(HttpStatus.SC_OK),
                                    notHaveEmptyBody(),
                                    haveBodyWithText("Rick Grimes")
                            )
                            .toValidatableXml()
                            .should(
                                    haveXmlValueEqualTo("students.student[0].name", "Rick Grimes"),
                                    haveXmlValueContains("students.student", "Dixon"),
                                    haveXmlValue("students.student[2].name", equalToText("Maggie"))
                            );
                }
        );

        assertEquals(nbEmprunts.intValue(), etudiant.getNombreDEmprunt());
    }


    @When("{string} requests his number of rentals")
    public void whenRequestsRentals(String nomEtudiant) {
        System.out.println(Environment.getForCurrentThread().getService(RestService.class).restClient().toString());
        etudiant = biblio.getEtudiantByName(nomEtudiant);
    }

    @When("{string} rents the book {string}")
    public void whenRenting(String nomEtudiant, String titreLivre) {
        etudiant = biblio.getEtudiantByName(nomEtudiant);
        livre = biblio.getLivreByTitle(titreLivre);
        etudiant.emprunte(livre);
    }

    @And("The book {string} is in a rental in the list of rentals")
    public void andNarrowedBook(String titreLivre) {
        assertTrue(etudiant.getEmprunt().stream().
                anyMatch(emp -> emp.getLivreEmprunte().getTitre().equals(titreLivre)));
    }

    @And("The book {string} is unavailable")
    public void andUnvailableBook(String titreLivre) {
        assertEquals(true, biblio.getLivreByTitle(titreLivre).getEmprunte());
    }

}
