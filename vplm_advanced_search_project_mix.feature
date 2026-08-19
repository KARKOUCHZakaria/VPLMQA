Feature: VPLM advanced search project mix

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
    When I enter "Projet" in the search field
    Then I should see "Recherche avancee"
    When I click "Recherche avancee"
    And I wait until the page is loaded
    Then I should see "Recherche avancee"
    And I should see "Nouvelle recherche avancee"

  Scenario: Create project oriented advanced search and view results
    When I click "Nouvelle recherche avancee"
    And I wait until the page is loaded
    Then I should see "Nouvelle recherche avancee"
    When I select "Tout" from the "Classes d'objets" field
    And I select "Sur les objets recherches" from the "first Ajout d'un critere" field
    And I select "Designation" from the "first Attribut" field
    And I select "Contient" from the "first Operateur" field
    And I enter "Projet" in the first Designation field
    When I click "Ajouter une condition"
    And I select "Sur les objets recherches" from the "second Ajout d'un critere" field
    And I select "Createur" from the "second Attribut" field
    And I select "Contient" from the "second Operateur" field
    And I enter "vplm" in the second Createur field
    When I click "Ajouter une condition"
    And I select "Sur les objets recherches" from the "third Ajout d'un critere" field
    And I select "Reference" from the "third Attribut" field
    And I select "Commence par" from the "third Operateur" field
    And I enter "AU" in the third Reference field
    When I click "Ajouter une condition"
    And I select "Sur les objets recherches" from the "fourth Ajout d'un critere" field
    And I select "Reference" from the "fourth Attribut" field
    And I select "Contient" from the "fourth Operateur" field
    And I enter "DPR" in the fourth Reference field
    When I check the third condition row
    And I check the fourth condition row
    And I group selected conditions
    And I select "OU" from the "grouped Condition" field
    When I click "Voir les resultats de la recherche"
    And I wait until the page is loaded
    Then I should see "Projet"

  Scenario: Logout from VPLM
    When I click the profile menu
    And I click "Deconnexion"
    Then I should see the Login page
