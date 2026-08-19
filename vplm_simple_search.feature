Feature: VPLM simple search

Scenario: Login to VPLM portal
Given I open "https://172.18.194.62/apps/plm/portal/login"
When I enter "vplm" in the username field
And I enter "${SECRET.password}" in the password field
And I select "Base Demo (SS)" from the "Base" field
And I select "AWS - Principal" from the "Poste" field
And I click the login button
Then I should see the Home page

Scenario: Search from home page
Given I am on the Home page
When I click the search icon
And I enter "P070" in the search field
And I press the Enter key
Then I should see "P070" in the search results

Scenario: Logout from VPLM
Given I am on the Home page
When I click the profile menu
And I click "Deconnexion"
Then I should see the Login page
