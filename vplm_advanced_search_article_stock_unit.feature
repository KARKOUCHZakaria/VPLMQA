Feature: VPLM advanced search article stock unit

  Scenario: Login to VPLM portal
    Given I open the login page
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
    When I click "Recherche avancée"
    And I wait until the page is loaded
    Then I should see "Recherche avancée"
    And I should see "Nouvelle recherche avancée"

  Scenario: Create an article advanced search by stock unit
    When I click "Nouvelle recherche avancée"
    And I wait until the page is loaded
    Then I should see "Nouvelle recherche avancée"
    When I select "Article" from the "Classes d'objets" field
    And I wait until the page is loaded
    And I select "Sur les objets recherchés" from the "first Ajout d'un critère" field
    And I select "Unité Stock" from the "first Attribut" field
    And I select "Contient" from the "first Opérateur" field
    And I select "Gramme [G]" from the "first Unité Stock" field
    Then I should see "Gramme [G]"
    When I click "Voir les resultats de la recherche"
    And I wait until the page is loaded
    Then I should see "Article"
    And I should see "Gramme"

  Scenario: Logout from VPLM
    When I click the profile menu
    And I click "Déconnexion"
    Then I should see the Login page
