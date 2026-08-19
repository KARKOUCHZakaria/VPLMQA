Feature: VPLM project creation workflow
  Validate the project creation flow shown in the recording: login, open the projects application, create a project, verify it in the projects grid, then logout.

  Scenario: Login to VPLM portal
    Given I open "https://172.18.194.62/apps/plm/portal/login"
    When I select "Base Demo (SS)" from the "Database" field
    And I select "AWS - Principal" from the "Workstation" field
    And I enter "vplm" in the username field
    And I enter "${SECRET.password}" in the password field
    And I click the login button
    Then I should see "Bonjour VPLM"

  Scenario: Open the projects application
    When I open "https://172.18.194.62/apps/plm/portal/lastused"
    Then I should see "Mes dernières App's utilisées"
    And I should see "Mes projets"
    When I click "Mes projets"
    Then I should see "Mes projets"
    And I should see "Ajouter"
    And I should see "Référence"
    And I should see "Désignation"

  Scenario: Create a project
    When I click "Ajouter"
    Then I should see "Création d'objet"
    And I should see "Projet"
    When I enter a valid project reference in the reference field
    And I enter a matching project designation in the designation field
    And I select "Cycle" from the "Type de projet" field
    And I click "Enregistrer"
    Then I wait for the save to complete
    Then I should see "Mes projets"
    When I click the refresh icon
    And I should see the created project reference
    And I should see the created project designation
    And I should see "Cycle"
    And I should see "ADMINISTRATEUR VPLM"

  Scenario: Verify the created project in the grid
    Given I open "https://172.18.194.62/apps/plm/grid?name=AU_APP_PROJECTS&appLinkID=41425"
    Then I should see "Mes projets"
    When I click the refresh icon
    And I should see the created project reference
    And I should see the created project designation
    And I should see "Cycle"

  Scenario: Logout from the profile menu
    Given I open "https://172.18.194.62/apps/plm/portal/home"
    Then I should see "Bonjour VPLM"
    When I click "vplm"
    Then I should see "Mon compte"
    And I should see "Conditions générales"
    When I click "Déconnexion"
    Then I should see "Visiativ PLM"
    And I should see "Sign in to continue"
