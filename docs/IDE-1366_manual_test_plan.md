# Manual Test Plan for IDE-1366: Get Project Organization from Language Server

## Overview of Changes

**New Feature**: Organization selection in project settings (Settings → Tools → Snyk)
- **UI Enhancement**: Auto-detect organization checkbox
- **Language Server Integration**: Organization settings are synchronized with the Snyk Language Server via FolderConfig
- **Fallback Logic**: Global organization setting serves as fallback when folder-specific values are empty

## Key Concepts

### Organization Hierarchy (Fallback Order)
1. **Folder-specific values** (highest priority)
   - `autoDeterminedOrg` when auto-detect is enabled (`orgSetByUser = false`)
   - `preferredOrg` when manual mode is enabled (`orgSetByUser = true`)
2. **Global organization setting** (fallback)
   - From `SnykApplicationSettingsStateService.organization`
3. **Empty string** (final fallback)

### FolderConfig Fields
- `autoDeterminedOrg`: Organization automatically determined by Language Server from LDX-Sync
- `preferredOrg`: Organization manually set by user
- `orgSetByUser`: Boolean flag indicating if user has manually set organization (`true` = manual, `false` = auto-detect)

### UI Behavior
- **Auto-detect checkbox checked** (`orgSetByUser = false`):
  - `preferredOrgTextField` shows `autoDeterminedOrg` (read-only display)
  - `organizationTextField` (global) is disabled
  - Language Server uses `autoDeterminedOrg` for scans
  
- **Auto-detect checkbox unchecked** (`orgSetByUser = true`):
  - `preferredOrgTextField` is empty and editable for user input
  - `organizationTextField` (global) is enabled
  - Language Server uses `preferredOrg` (or global org as fallback) for scans

---

## Test Case 1: Organization Selection in Project Settings

### Objective
Verify organization can be set per-project through the Snyk settings dialog.

### Prerequisites
- IntelliJ IDEA with Snyk plugin installed
- Authenticated Snyk account with multiple organizations
- A test project open in IntelliJ
- Language Server initialized and folder configs received

### Steps

#### 1.1 Access Project Settings
1. Open **File → Settings** (Windows/Linux) or **IntelliJ IDEA → Preferences** (macOS)
2. Navigate to **Tools → Snyk**
3. Verify **"Project settings"** section is visible (only shown when project is available)
4. Verify **"Auto-select organization"** checkbox is present
5. Verify **"Preferred organization"** text field is present

#### 1.2 Test Organization Field Visibility
1. Verify **"Preferred organization"** field is visible in Project settings section
2. Verify help icon/link is displayed next to the field
3. Click help icon and verify documentation link opens correctly

#### 1.3 Test Manual Organization Setting
1. Uncheck **"Auto-select organization"** checkbox
2. Verify **"Preferred organization"** text field becomes editable
3. Enter a valid organization ID in the **"Preferred organization"** field
4. Verify **"Organization"** field in General settings becomes enabled
5. Click **"Apply"** or **"OK"**
6. Verify the setting is saved

#### 1.4 Test Organization Persistence
1. Close and reopen Settings dialog
2. Navigate to **Tools → Snyk**
3. Verify the organization setting is retained
4. Verify checkbox state matches the saved `orgSetByUser` value
5. Verify text field shows correct organization value

#### 1.5 Verify Language Server Communication
1. Open IntelliJ Event Log (Help → Show Log in Explorer → idea.log)
2. Monitor for `WorkspaceDidChangeConfiguration` calls
3. Verify folder config is sent to Language Server with correct `preferredOrg` and `orgSetByUser` values

### Expected Results
- Organization field is visible and functional in Project settings section
- Settings are saved to FolderConfig
- Organization is used in subsequent scans
- Language Server receives updated configuration via `WorkspaceDidChangeConfiguration`

---

## Test Case 2: Auto-Detect Organization Checkbox

### Objective
Verify auto-detect organization functionality and UI behavior.

### Prerequisites
- Same as Test Case 1
- Language Server has received folder configs with `autoDeterminedOrg` populated

### Steps

#### 2.1 Test Checkbox Visibility and Initial State
1. Open **Settings → Tools → Snyk**
2. Verify **"Auto-select organization"** checkbox is present in Project settings section
3. Verify help text/link is displayed next to checkbox
4. Click help link and verify documentation opens correctly
5. Verify checkbox initial state matches `orgSetByUser` value from Language Server:
   - **Checked** if `orgSetByUser = false` (auto-detect enabled)
   - **Unchecked** if `orgSetByUser = true` (manual mode)

#### 2.2 Test Checkbox Behavior - Enabling Auto-Detect
1. If checkbox is unchecked, check **"Auto-select organization"**
2. Verify **"Preferred organization"** field shows `autoDeterminedOrg` value (read-only display)
3. Verify **"Preferred organization"** field becomes read-only (if implemented) or shows auto-determined value
4. Verify **"Organization"** field in General settings becomes disabled
5. Verify checkbox tooltip explains auto-detect behavior

#### 2.3 Test Checkbox Behavior - Disabling Auto-Detect
1. Uncheck **"Auto-select organization"** checkbox
2. Verify **"Preferred organization"** field becomes editable
3. Verify **"Preferred organization"** field is cleared (empty)
4. Verify **"Organization"** field in General settings becomes enabled
5. Verify user can now enter manual organization value

#### 2.4 Test Auto-Detect Functionality
1. Ensure checkbox is checked (auto-detect enabled)
2. Click **"Apply"**
3. Run a Snyk scan (manually or wait for auto-scan)
4. Verify scan uses `autoDeterminedOrg` from Language Server
5. Verify scan results are associated with the correct organization

#### 2.5 Test Manual Override
1. Uncheck **"Auto-select organization"**
2. Enter a specific organization ID in **"Preferred organization"** field
3. Click **"Apply"**
4. Run a Snyk scan
5. Verify scan uses the manually entered organization
6. Verify `orgSetByUser = true` in folder config sent to Language Server

### Expected Results
- Checkbox controls organization field state correctly
- Auto-detect selects appropriate organization from Language Server
- Manual organization setting works when auto-detect is disabled
- Language Server receives correct `orgSetByUser` flag

---

## Test Case 3: Settings Dialog UI Integration

### Objective
Verify complete settings dialog integration and field interactions.

### Prerequisites
- IntelliJ IDEA with Snyk plugin
- Test project open
- Language Server initialized

### Steps

#### 3.1 Test Property Page Loading
1. Open **Settings → Tools → Snyk**
2. Verify all sections load correctly:
   - General settings
   - Issue view options
   - Products and Severities selection
   - Project settings (if project available)
   - User experience
   - Executable settings
3. Verify no errors in Event Log

#### 3.2 Test Field Population on Dialog Open
1. With auto-detect **enabled** (`orgSetByUser = false`):
   - Verify **"Preferred organization"** shows `autoDeterminedOrg` value
   - Verify checkbox is checked
   - Verify **"Organization"** (global) field is disabled

2. With auto-detect **disabled** (`orgSetByUser = true`):
   - Verify **"Preferred organization"** shows `preferredOrg` value (or empty if not set)
   - Verify checkbox is unchecked
   - Verify **"Organization"** (global) field is enabled

3. With **no folder config** (Language Server not initialized):
   - Verify Project settings section may be disabled or not visible
   - Verify fields show appropriate default/empty values

#### 3.3 Test Field Validation
1. Enter invalid organization ID format in **"Preferred organization"**
2. Verify validation works (if implemented)
3. Test with empty fields
4. Test with very long organization strings
5. Verify **"Apply"** button behavior with invalid input

#### 3.4 Test Save/Cancel Operations
1. Make changes to organization settings:
   - Toggle auto-detect checkbox
   - Enter organization value
2. Click **"Apply"**
   - Verify changes are saved
   - Verify Language Server receives update
   - Verify dialog remains open
3. Make additional changes
4. Click **"Cancel"**
   - Verify changes are discarded
   - Verify original values are restored
5. Click **"OK"**
   - Verify changes are saved
   - Verify dialog closes

#### 3.5 Test Field Interactions
1. Verify **"Preferred organization"** field behavior:
   - When user types in field while auto-detect is enabled, verify checkbox auto-unchecks (if implemented)
   - When checkbox is toggled, verify field content updates appropriately
2. Verify **"Organization"** (global) field:
   - Enabled/disabled state matches auto-detect checkbox
   - Value persists independently of project settings

### Expected Results
- Settings dialog loads without errors
- Fields populate correctly based on folder config state
- Validation works correctly (if implemented)
- Save/cancel operations function properly
- Field interactions work as expected

---

## Test Case 4: Language Server Configuration Updates

### Objective
Verify configuration changes trigger Language Server updates and are properly synchronized.

### Prerequisites
- IntelliJ IDEA with Snyk plugin
- Language Server running and initialized
- Multiple test projects (optional, for multi-project testing)

### Steps

#### 4.1 Test Configuration Propagation
1. Open **Settings → Tools → Snyk**
2. Change organization settings:
   - Toggle auto-detect checkbox
   - Enter/change preferred organization
3. Click **"Apply"**
4. Monitor Language Server communication:
   - Check Event Log for `WorkspaceDidChangeConfiguration` calls
   - Verify folder config contains updated values:
     - `preferredOrg` matches text field value
     - `orgSetByUser` matches checkbox state
     - `autoDeterminedOrg` is preserved (never modified by IDE)

#### 4.2 Test FolderConfig Update Logic
1. With auto-detect **enabled** (checkbox checked):
   - Click **"Apply"**
   - Verify folder config sent to LS has:
     - `orgSetByUser = false`
     - `preferredOrg = ""` (empty string)
     - `autoDeterminedOrg` unchanged (from LS)

2. With auto-detect **disabled** (checkbox unchecked):
   - Enter organization in **"Preferred organization"** field
   - Click **"Apply"**
   - Verify folder config sent to LS has:
     - `orgSetByUser = true`
     - `preferredOrg` = value from text field
     - `autoDeterminedOrg` unchanged (from LS)

#### 4.3 Test Multiple Projects (if applicable)
1. Open multiple projects in IntelliJ
2. Configure different organizations for different projects:
   - Project A: Auto-detect enabled
   - Project B: Manual organization "org-123"
   - Project C: Manual organization "org-456"
3. Switch between projects
4. Verify each project maintains its own organization setting
5. Run scans on each project
6. Verify each project uses the correct organization

#### 4.4 Test Language Server Response
1. Make organization setting changes
2. Click **"Apply"**
3. Wait for Language Server to process configuration
4. Verify Language Server sends updated folder configs back via `$/snyk.folderConfig` notification
5. Verify IDE updates UI to reflect any changes from Language Server
6. Verify `autoDeterminedOrg` is updated by Language Server (if LDX-Sync provides new value)

#### 4.5 Test Fallback Behavior
1. Clear preferred organization (set to empty)
2. Disable auto-detect
3. Verify global organization setting is used as fallback
4. Verify Language Server receives global organization in `organization` field of `LanguageServerSettings`
5. Run scan and verify correct organization is used

### Expected Results
- Configuration changes are propagated to Language Server via `WorkspaceDidChangeConfiguration`
- Folder config updates contain correct `preferredOrg`, `orgSetByUser`, and `autoDeterminedOrg` values
- Each project maintains its own organization setting
- Language Server responses are properly handled
- Fallback to global organization works correctly

---

## Test Case 5: Edge Cases and Error Handling

### Objective
Verify edge cases and error scenarios are handled gracefully.

### Prerequisites
- IntelliJ IDEA with Snyk plugin
- Test project
- Various test scenarios (see steps)

### Steps

#### 5.1 Test Empty/Null Values
1. Test with `autoDeterminedOrg` empty/null:
   - Verify UI handles gracefully
   - Verify fallback to global organization works
   - Verify no crashes or errors

2. Test with `preferredOrg` empty when `orgSetByUser = true`:
   - Verify fallback behavior
   - Verify Language Server receives correct values

#### 5.2 Test Language Server Not Initialized
1. Close project
2. Open Settings (application-level)
3. Verify Project settings section is not visible or disabled
4. Verify no errors when accessing settings without project

#### 5.3 Test Rapid Toggle
1. Rapidly toggle auto-detect checkbox multiple times
2. Enter/clear organization value multiple times
3. Click Apply/Cancel rapidly
4. Verify no race conditions or UI glitches
5. Verify final state is correct

#### 5.4 Test Special Characters
1. Enter organization with special characters (if valid)
2. Enter very long organization strings
3. Enter organization with unicode characters
4. Verify handling is correct

#### 5.5 Test Concurrent Modifications
1. Open Settings dialog
2. In another window/process, modify organization via API/config file (if possible)
3. Verify IDE handles concurrent modifications correctly
4. Verify Language Server receives consistent state

#### 5.6 Test Migration Scenarios
1. Test with old project that has global organization but no folder config:
   - Verify migration logic works
   - Verify `OrgMigratedFromGlobalConfig` flag is set
   - Verify organization is properly migrated to folder config

### Expected Results
- Edge cases are handled gracefully
- No crashes or unhandled exceptions
- Error messages are clear and helpful
- Fallback behavior works in all scenarios

---

## Test Case 6: Integration with Scans

### Objective
Verify organization settings are correctly used during Snyk scans.

### Prerequisites
- IntelliJ IDEA with Snyk plugin
- Authenticated Snyk account
- Test project with vulnerabilities
- Multiple organizations available

### Steps

#### 6.1 Test Scan with Auto-Detect Enabled
1. Enable auto-detect organization
2. Click **"Apply"**
3. Trigger a Snyk scan (manual or automatic)
4. Verify scan uses `autoDeterminedOrg` from Language Server
5. Verify scan results show correct organization
6. Verify issues are associated with correct organization

#### 6.2 Test Scan with Manual Organization
1. Disable auto-detect
2. Enter specific organization ID
3. Click **"Apply"**
4. Trigger a Snyk scan
5. Verify scan uses manually entered organization
6. Verify scan results show correct organization

#### 6.3 Test Organization Change During Active Scan
1. Start a scan
2. While scan is running, change organization settings
3. Click **"Apply"**
4. Verify scan behavior (may continue with old org or restart)
5. Verify new scans use updated organization

#### 6.4 Test Scan with Invalid Organization
1. Enter invalid/non-existent organization ID
2. Click **"Apply"**
3. Trigger a Snyk scan
4. Verify appropriate error handling
5. Verify error message is clear and actionable

#### 6.5 Test Scan Results Display
1. Run scans with different organizations
2. Verify scan results are correctly filtered/displayed
3. Verify organization information is shown in UI (if applicable)
4. Verify issues are correctly associated with organization

### Expected Results
- Scans use correct organization based on settings
- Organization changes are reflected in subsequent scans
- Error handling for invalid organizations works correctly
- Scan results correctly reflect organization settings

---

## Error Monitoring

Throughout all tests, monitor:

### IntelliJ Event Log
- **Help → Show Log in Explorer → idea.log**
- Look for exceptions, errors, or warnings related to:
  - Organization settings
  - Folder config updates
  - Language Server communication

### Snyk Plugin Logs
- Check plugin-specific log files (if available)
- Monitor for:
  - `NullPointerException`
  - `IllegalArgumentException`
  - JSON parsing errors
  - Configuration loading errors
  - Language Server communication errors

### Language Server Logs
- Check Language Server output/logs
- Verify folder config updates are received
- Verify organization resolution works correctly

### Common Issues to Watch For
- Settings not persisting
- UI state not matching actual configuration
- Language Server not receiving updates
- Organization fallback not working
- Race conditions in UI updates
- Memory leaks from event listeners

---

## Regression Testing

Verify existing functionality still works:

### Basic Functionality
- ✅ Basic Snyk scanning (OSS, Code, IaC)
- ✅ Authentication flow (OAuth2 and Token)
- ✅ Issue display and filtering
- ✅ Preferences management (other settings)
- ✅ Language server communication (general)

### Settings Persistence
- ✅ Application-level settings persist across IDE restarts
- ✅ Project-level settings persist across project close/open
- ✅ Settings survive IDE updates (if applicable)

### UI Components
- ✅ Settings dialog opens/closes correctly
- ✅ All other settings sections work as before
- ✅ Tool window displays correctly
- ✅ Issue tree/filtering works correctly

---

## Test Data Preparation

### Organizations Setup
1. Create/identify test organizations:
   - Organization A: Default organization
   - Organization B: Secondary organization
   - Organization C: Organization with specific projects

### Projects Setup
1. Create test projects:
   - Project with no `.snyk` file (uses auto-detect)
   - Project with `.snyk` file containing organization
   - Project in multiple organizations
   - Project with invalid organization reference

### Authentication Setup
1. Ensure test account has access to multiple organizations
2. Verify LDX-Sync is working (for auto-detect)
3. Test with both OAuth2 and Token authentication

---

## Success Criteria

All test cases should pass with:
- ✅ No crashes or unhandled exceptions
- ✅ Settings persist correctly
- ✅ Language Server receives correct configuration
- ✅ Scans use correct organization
- ✅ UI reflects actual configuration state
- ✅ Fallback behavior works correctly
- ✅ No regression in existing functionality

---

## Notes

### IntelliJ-Specific Considerations
- Settings are accessed via **File → Settings** (Windows/Linux) or **IntelliJ IDEA → Preferences** (macOS)
- Project settings are only available when a project is open
- Settings dialog uses IntelliJ's standard settings framework
- Folder configs are managed per workspace folder (project root)

### Language Server Integration
- Organization settings are synchronized via `WorkspaceDidChangeConfiguration`
- Language Server sends folder configs via `$/snyk.folderConfig` notification
- `autoDeterminedOrg` is always provided by Language Server (never modified by IDE)
- `preferredOrg` and `orgSetByUser` are set by IDE based on user input

### Migration Notes
- Existing projects with global organization will be migrated to folder configs
- Migration happens automatically when Language Server initializes
- `OrgMigratedFromGlobalConfig` flag tracks migration status

