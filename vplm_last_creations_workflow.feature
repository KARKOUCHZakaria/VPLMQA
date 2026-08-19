Feature: VPLM last creations workflow
  Validate the main VPLM flow shown in the recording: login, open the last creations app, create a designed article, inspect the list and logout.

  Scenario: Login to VPLM portal
    Given I open "https://172.18.194.62/apps/plm/portal/login"
    When I select "Base Demo (SS)" from the "Database" field
    And I select "AWS - Principal" from the "Workstation" field
    And I enter "vplm" in the username field
    And I enter "${SECRET.password}" in the password field
    And I click the login button
    Then I should see "Bonjour VPLM"
    And I should see "Mes activités récentes"

  Scenario: Open the last used applications page
    When I open "https://172.18.194.62/apps/plm/portal/lastused"
    Then I should see "Mes dernières App's utilisées"
    And I should see "Mes dernières créations"
    And I should see "ModelShape"
    And I should see "Documents"
    And I should see "Configuration"

  Scenario: Open my last creations application
    When I click "Mes dernières créations"
    Then I should see "Mes dernières créations"
    And I should see "Ajouter"
    And I should see "Classe"
    And I should see "Référence"
    And I should see "Désignation"

  Scenario: Create a designed article
    When I click "Ajouter"
    Then I should see "Choisissez la classe"
    When I click "Article conçu"
    And I click "Confirmer"
    Then I should see "Création d'objet"
    And I should see "Article conçu"
    When I enter a valid article reference in the reference field
    And I enter a matching article designation in the designation field
    And I click "Enregistrer"
    Then I should see "Mes dernières créations"
    When I click the refresh icon
    And I should see the created article reference
    And I should see the created article designation

  Scenario: Verify the created item in the last creations grid
    Given I open "https://172.18.194.62/apps/plm/grid?name=AU_APP_MY_LAST_CREATIONS&appLinkID=42954"
    Then I should see "Mes dernières créations"
    When I click the refresh icon
    And I should see the created article reference
    And I should see the created article designation

  Scenario: Logout from the profile menu
    Given I open "https://172.18.194.62/apps/plm/portal/home"
    Then I should see "Bonjour VPLM"
    When I click "vplm"
    Then I should see "Mon compte"
    And I should see "Conditions générales"
    When I click "Déconnexion"
    Then I should see "Visiativ PLM"
    And I should see "Sign in to continue"
