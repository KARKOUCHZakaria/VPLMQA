Feature: VPLM advanced search project progress and manager

  Scenario: Login to VPLM portal
    Given I open "https://192.168.19.128/apps/plm/portal/login"
    When I enter "vplm" in the username field
    And I enter "${SECRET.password}" in the password field
    And I select "Base Demo (SS)" from the "Base" field
    And I select "AWS - Principal" from the "Poste" field
    And I click the login button
    Then I should see the Home page

  Scenario: Open advanced search from the global search bar
    Given I am on the Home page
    When I click the search icon
    Then I should see "Recherche avancée"
    When I enter "Projet" in the search field
    Then I should see "Recherche avancée"
    When I click "Recherche avancée"
    And I wait until the page is loaded
    Then I should see "Recherche avancée"
    And I should see "Nouvelle recherche avancée"

  Scenario: Create a project advanced search by progress and project manager
    When I click "Nouvelle recherche avancée"
    And I wait until the page is loaded
    Then I should see "Nouvelle recherche avancée"
    When I select "Projet" from the "Classes d'objets" field
    And I wait until the page is loaded
    And I select "Sur les objets recherchés" from the "first Ajout d'un critère" field
    And I select "Avancement (%)" from the "first Attribut" field
    And I select "Égal à" from the "first Opérateur" field
    And I enter "50" in the first Avancement field
    Then the "first Avancement" field should contain "50"
    When I click "Ajouter une condition"
    And I select "Sur les objets recherchés" from the "second Ajout d'un critère" field
    And I select "Chef de projet" from the "second Attribut" field
    And I select "Contient" from the "second Opérateur" field
    And I open the pointer selector for the "second Chef de projet" field
    And I search the pointer selector with "BRILLOL JEAN MARC"
    And I select "BRILLOL JEAN MARC" from the pointer selector table
    And I validate the pointer selector
    Then the "second Chef de projet" field should contain "BRILLOL JEAN MARC"
    When I click "Voir les resultats de la recherche"
    And I wait until the page is loaded
    Then I should see "Projet"

  Scenario: Logout from VPLM
    When I click the profile menu
    And I click "Déconnexion"
    Then I should see the Login page
