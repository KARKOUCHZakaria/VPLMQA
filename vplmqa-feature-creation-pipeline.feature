Feature: VPLMQA feature creation and pipeline run

  Scenario: Login to VPLMQA
    Given I go to the login page
    When I enter username "admin@vplmqa.com"
    And I enter password "${SECRET.password}"
    And I click the login button
    Then I should see the dashboard page

  Scenario: Create a Wikipedia smoke feature
    Given I go to the Tests page
    When I open the Feature Builder tab
    And I create a feature named "Wikipedia Smoke Test"
    And I add a scenario named "Search Morocco"
    And I add step "Given I open https://www.wikipedia.org/"
    And I add step "When I search for Morocco"
    And I add step "Then I should see Morocco"
    And I save the feature
    Then I should see "Wikipedia Smoke Test"

  Scenario: Run the saved feature from pipeline
    Given I go to the Tests page
    When I open the Pipeline tab
    And I run the feature "Wikipedia Smoke Test" in Chrome
    Then I should see the pipeline progress
    And the feature should finish with a result
