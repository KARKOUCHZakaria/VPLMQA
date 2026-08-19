Feature: VPLM create article 
  Validate article creation from the Home page using the add icon.

  Scenario: Login to VPLM portal
    Given I open Login page
    When I enter "vplm" in the username field
    And I enter "${SECRET.password}" in the password field
    And I select "Base Demo (SS)" from the "Base" field
    And I select "AWS - Principal" from the "Poste" field
    And I click the login button
    Then I should see the Home page

  Scenario: Create a designed article
    Given I am on the Home page
    When I click the add icon
    Then I should see "Choisissez la classe"
    When I click "Article conçu"
    And I click "Confirmer"
    Then I should see "Création d'objet"
    And I should see "Article conçu"
    When I enter a valid article reference in the reference field
    And I enter a matching article designation in the designation field
    And I click "Enregistrer"
    Then I wait for the save to complete
    And I click the refresh icon
    And I should see the created article reference
    And I should see the created article designation

  Scenario: Logout from VPLM portal
    Given I am on the Home page
    When I click the profile menu
    And I click "Déconnexion"
    Then I should see the Login page
