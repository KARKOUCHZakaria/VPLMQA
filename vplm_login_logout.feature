Feature: VPLM authentication

  Scenario: Login to VPLM portal
    Given I open Login page
    When I enter "vplm" in the username field
    And I enter "${SECRET.password}" in the password field
    And I select "Base Demo (SS)" from the "Base" field
    And I select "AWS - Principal" from the "Poste" field
    And I click the login button
    Then I should see the Home page

  Scenario: Logout from VPLM portal
    Given I am on the Home page
    When I click the profile menu
    And I click "Déconnexion"
    Then I should see the Login page
