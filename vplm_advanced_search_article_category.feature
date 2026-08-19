Feature: VPLM advanced search article category

  Scenario: Login to VPLM portal
    Given I open "https://172.18.197.36/apps/plm/portal/login"
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
    When I enter "Article" in the search field
    Then I should see "Recherche avancée"
    When I click "Recherche avancée"
    And I wait until the page is loaded
    Then I should see "Recherche avancée"
    And I should see "Nouvelle recherche avancée"

  Scenario: Create an article advanced search by category
    When I click "Nouvelle recherche avancée"
    And I wait until the page is loaded
    Then I should see "Nouvelle recherche avancée"
    When I select "Article" from the "Classes d'objets" field
    And I wait until the page is loaded
    And I select "Sur les objets recherchés" from the "first Ajout d'un critère" field
    And I select "Catégorie" from the "first Attribut" field
    And I select "Contient" from the "first Opérateur" field
    When I open the value selector for the first Catégorie field
    And I filter the value selector with "bijouterie argent"
    And I select "Bijouterie Argent [BIJOUXARG]" from the value selector
    And I validate the selected value
    Then I should see "L100 Bijouterie Argent [BIJOUXARG]"
    When I click "Voir les resultats de la recherche"
    And I wait until the page is loaded
    Then I should see "Article"
    And I should see "Bijouterie Argent"

  Scenario: Logout from VPLM
    When I click the profile menu
    And I click "Déconnexion"
    Then I should see the Login page
