## Release version steps


**Protocol Version Verification**

- Ensure the Snyk Language Server Protocol version is correct in the plugin. 
  - `requiredLsProtocolVersion`  in  `kotlin/io/snyk/plugin/services/SnykApplicationSettingsStateService.kt`  

**Testing**

- Ensure all tests for the latest commit have passed before proceeding with the release.

**Initiate Release**

- If you want to do a hotfix with a subset of commits from main, create a hotfix branch off the previous release tag.
  - For the hotfix release, cherry pick the commits you want to go into the hotfix release.

- Trigger the release workflow in GitHub Actions.
  - Select the appropriate version type (major, minor, patch).
  - If this is a hotfix not off main, select the hotfix branch.


**Marketplace Availability**

-   Wait for the new release to be approved in the manual review process in IntelliJ Marketplace.


**Installation and Version Verification**

-   Install the plugin or extension in the target IDE. 
-   Confirm that the installed version matches the intended release.


**CLI Configuration and Verification**

- Ensure the Snyk CLI release channel is set to  `stable`  and automatic update is enabled. 


- Execute the CLI binary in the terminal and verify that the version matches the intended release.
  - The correct version can be found in the  `#hammerhead-releases`  channel in Slack or in the github cli repo.
     https://github.com/snyk/cli/releases

**Manual End-to-End Test**

- Manually run a scan using the latest version of the plugin to confirm end-to-end functionality.

