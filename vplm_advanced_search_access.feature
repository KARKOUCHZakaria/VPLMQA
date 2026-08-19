Feature: VPLM advanced search access

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
    Then I should see "Recherche avancee"
    When I enter "P070" in the search field
    Then I should see "Recherche avancee"
    When I click "Recherche avancee"
    And I wait until the page is loaded
    Then I should see "Recherche avancee"
    And I should see "Nouvelle recherche avancee"
    And I should see "Tout selectionner"

  Scenario: Create a new advanced search and view results
    When I click "Nouvelle recherche avancee"
    And I wait until the page is loaded
    Then I should see "Recherche avancee"
    When I select "Document" from the "Classes d'objets" field
    And I select "Sur les objets recherches" from the "first Ajout d'un critere" field
    And I select "Createur" from the "first Attribut" field
    And I select "Contient" from the "first Operateur" field
    And I enter "vplm" in the first Createur field
    When I click "Ajouter une condition"
    And I select "OU" from the "Condition" field
    And I select "Sur les objets recherches" from the "second Ajout d'un critere" field
    And I select "Reference" from the "second Attribut" field
    And I select "Commence par" from the "second Operateur" field
    And I enter "Z" in the second Reference field
    When I click "Ajouter une condition"
    And I select "Sur les objets recherches" from the "third Ajout d'un critere" field
    And I select "Version" from the "third Attribut" field
    And I select "Contient" from the "third Operateur" field
    And I enter "A" in the third Version field
    When I click "Voir les resultats de la recherche"
    And I wait until the page is loaded
    Then I should see "Document"
    And I should see "vplm"

  Scenario: Logout from VPLM
    When I click the profile menu
    And I click "Deconnexion"
    Then I should see the Login page
