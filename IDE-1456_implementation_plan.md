# IDE-1456: HTML-based Config Dialog Implementation Plan

## Goal
Language Server serves a new config dialog through the `workspace.config` command. This LSP command returns an HTML string that should be displayed inside a JCEF panel. The panel should be included in the `SnykSettingsDialog` if the registry key `snyk.useNewConfigDialog` is `true`. On Apply/Save of the settings, values from the HTML need to be extracted and saved locally in the normal Snyk settings.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                  SnykProjectSettingsConfigurable                │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                    SnykSettingsDialog                     │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │   useNewConfigDialog = false (legacy)               │  │  │
│  │  │   → Traditional Swing-based settings panels         │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │   useNewConfigDialog = true (new)                   │  │  │
│  │  │   → HTMLSettingsPanel (JCEF)                        │  │  │
│  │  │      ↓                                              │  │  │
│  │  │   ┌─────────────────────────────────────────────┐   │  │  │
│  │  │   │ Language Server available?                  │   │  │  │
│  │  │   │   YES → getConfigHtml() from LS             │   │  │  │
│  │  │   │   NO  → Use fallback HTML (CLI settings)    │   │  │  │
│  │  │   └─────────────────────────────────────────────┘   │  │  │
│  │  │      ↓                                              │  │  │
│  │  │   HTML displayed in JCEF browser                    │  │  │
│  │  │      ↓                                              │  │  │
│  │  │   window.__ideSaveConfig__ callback                 │  │  │
│  │  │      ↓                                              │  │  │
│  │  │   Parse JSON and save to plugin settings            │  │  │
│  │  │      ↓                                              │  │  │
│  │  │   If CLI downloaded → refresh with LS config        │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## Fallback HTML Scenario

When the Language Server is **not available** (CLI not downloaded yet), the HTMLSettingsPanel must:
1. Display a **fallback HTML** with only essential settings:
   - CLI Path
   - Automatically manage binaries (checkbox)
   - Base URL for CLI download
   - CLI Release Channel
2. Listen for CLI download completion event
3. Once LS is available, **reload** with full config from Language Server

---

## Phase 1: Planning

### Files to Modify
1. **`snyk/common/lsp/commands/Commands.kt`** - Add new command constant for `workspace.config`
2. **`snyk/common/lsp/LanguageServerWrapper.kt`** - Add method to execute `workspace.config` command
3. **`io/snyk/plugin/ui/settings/HTMLSettingsPanel.kt`** - Implement JCEF-based settings panel
4. **`io/snyk/plugin/Utils.kt`** - Add helper function to check registry key
5. **`io/snyk/plugin/settings/SnykProjectSettingsConfigurable.kt`** - Conditionally use HTMLSettingsPanel
6. **`io/snyk/plugin/ui/SnykSettingsDialog.kt`** - Minor changes for compatibility

### Files to Create
1. **`io/snyk/plugin/ui/jcef/SaveConfigHandler.kt`** - JCEF handler for save config callback
2. **`resources/html/settings-fallback.html`** - Fallback HTML for CLI settings when LS not available

### Tests to Create/Modify
1. **`io/snyk/plugin/ui/settings/HTMLSettingsPanelTest.kt`** - Unit tests for HTML panel
2. **`io/snyk/plugin/settings/SnykProjectSettingsConfigurableTest.kt`** - Update for new flow

---

## Phase 2: Implementation (TDD)

### Step 2.1: Add Command Constant
- [ ] Add `COMMAND_WORKSPACE_CONFIGURATION = "snyk.workspace.configuration"` to Commands.kt
- [ ] Write test to verify constant exists

### Step 2.2: Add LanguageServerWrapper Method
- [ ] Write test for `getConfigHtml()` method
- [ ] Implement `getConfigHtml()` method that:
  - Calls `snyk.workspace.configuration` command via LSP
  - Returns HTML string
  - Handles errors gracefully

### Step 2.3: Add Registry Key Helper
- [ ] Write test for `isNewConfigDialogEnabled()` function
- [ ] Implement `isNewConfigDialogEnabled()` in Utils.kt

### Step 2.4: Create SaveConfigHandler
- [ ] Write test for SaveConfigHandler parsing JSON config
- [ ] Implement SaveConfigHandler that:
  - Registers `window.__ideSaveConfig__` JS callback
  - Parses JSON string from HTML form
  - Maps JSON fields to SnykApplicationSettingsStateService
  - Maps JSON fields to FolderConfigSettings
  - Handles authentication callbacks (`__ideLogin__`, `__ideLogout__`)

### Step 2.5: Create Fallback HTML
- [ ] Create `settings-fallback.html` with:
  - CLI Path text field with browse button simulation
  - Automatically manage binaries checkbox
  - Base URL for CLI download text field
  - CLI Release Channel dropdown (stable, rc, preview)
  - **Reuse `styles.css` from `snyk-ls-2/infrastructure/configuration/template/`** for visual consistency
  - Simplified version of `scripts.js` (only `__ideSaveConfig__` callback, no folder configs)
- [ ] Write test to verify fallback HTML loads correctly

### Step 2.6: Implement HTMLSettingsPanel
- [ ] Write test for HTMLSettingsPanel initialization (LS available)
- [ ] Write test for HTMLSettingsPanel initialization (LS not available - fallback)
- [ ] Write test for HTMLSettingsPanel refresh when LS becomes available
- [ ] Implement HTMLSettingsPanel that:
  - Extends JPanel and implements Disposable
  - Checks if LanguageServer is initialized
  - If LS available: fetches HTML from LanguageServerWrapper
  - If LS not available: loads fallback HTML from resources
  - Creates JBCefBrowser with HTML content
  - Registers SaveConfigHandler
  - Subscribes to `SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC`
  - On CLI download finished: refresh with LS config HTML
  - Provides method to check if modified
  - Provides apply() method for saving

### Step 2.7: Integrate with SnykProjectSettingsConfigurable
- [ ] Write test for conditional panel selection
- [ ] Modify `createComponent()` to:
  - Check registry key
  - Return HTMLSettingsPanel if enabled
  - Return legacy SnykSettingsDialog otherwise
- [ ] Update `isModified()` to delegate appropriately
- [ ] Update `apply()` to delegate appropriately

---

## Phase 3: Review

### Step 3.1: Code Review Checklist
- [ ] All tests pass
- [ ] No linting errors
- [ ] Code follows existing patterns
- [ ] Error handling is robust
- [ ] JCEF fallback when not supported
- [ ] Security considerations (nonce for scripts)

### Step 3.2: Manual Testing
- [ ] Test with registry key = false (legacy behavior)
- [ ] Test with registry key = true (new JCEF panel)
- [ ] Test save/apply functionality
- [ ] Test authentication callbacks
- [ ] Test on platform without JCEF support

---

## Progress Tracking

| Step | Status | Notes |
|------|--------|-------|
| 2.1 Add Command Constant | ✅ Done | Added COMMAND_WORKSPACE_CONFIGURATION |
| 2.2 Add LSW Method | ✅ Done | Added getConfigHtml() |
| 2.3 Add Registry Helper | ✅ Done | Added isNewConfigDialogEnabled() |
| 2.4 Create SaveConfigHandler | ✅ Done | Created SaveConfigHandler.kt |
| 2.5 Create Fallback HTML | ✅ Done | Created settings-fallback.html |
| 2.6 Implement HTMLSettingsPanel | ✅ Done | Full JCEF implementation |
| 2.7 Integrate with Configurable | ✅ Done | Conditional panel selection |
| 3.1 Code Review | ✅ Done | Compiles, tests pass |
| 3.2 Manual Testing | ⬜ Pending | Requires manual IDE testing |

---

## Language Server Template Structure

The Language Server uses Go templates located at `snyk-ls-2/infrastructure/configuration/template/`:
- **`config.html`** - Main HTML template with Go placeholders (`{{.Settings.X}}`, `{{.Nonce}}`, etc.)
- **`styles.css`** - CSS styles (embedded via `{{.Styles}}`)
- **`scripts.js`** - JavaScript logic (embedded via `{{.Scripts}}`)

The LS renders this template with actual settings values and returns **pre-rendered HTML** to the IDE.

Key JS functions from `scripts.js`:
- `window.__ideSaveConfig__(jsonString)` - Called on form changes (debounced)
- `window.__ideLogin__()` - Called when "Authenticate" button clicked
- `window.__ideLogout__()` - Called when "Logout" button clicked or endpoint changes

---

## JSON Config Structure (from HTML form)

Based on `scripts.js` `collectData()` function, the JSON structure sent via `window.__ideSaveConfig__` is:

```json
{
  "activateSnykOpenSource": true,
  "activateSnykCode": true,
  "activateSnykIac": true,
  "scanningMode": "auto",
  "organization": "",
  "additionalParams": "--severity-threshold=high",
  "filterSeverity": {
    "critical": true,
    "high": false,
    "medium": true,
    "low": false
  },
  "issueViewOptions": {
    "openIssues": true,
    "ignoredIssues": false
  },
  "enableDeltaFindings": "false",
  "authenticationMethod": "token",
  "endpoint": "https://api.snyk.io",
  "insecure": false,
  "token": "xxx",
  "folderConfigs": [
    {
      "folderPath": "/path/to/project",
      "riskScoreThreshold": 500,
      "autoOrg": false,
      "orgSetByUser": "true",
      "preferredOrg": "my-org-uuid",
      "autoDeterminedOrg": "auto-org-uuid",
      "additionalParameters": "--all-projects"
    }
  ]
}
```

## Mapping to Plugin Settings

| JSON Field | Plugin Setting |
|------------|----------------|
| `activateSnykOpenSource` | `SnykApplicationSettingsStateService.ossScanEnable` |
| `activateSnykCode` | `SnykApplicationSettingsStateService.snykCodeSecurityIssuesScanEnable` |
| `activateSnykIac` | `SnykApplicationSettingsStateService.iacScanEnabled` |
| `scanningMode` | `SnykApplicationSettingsStateService.scanOnSave` (auto=true, manual=false) |
| `organization` | `SnykApplicationSettingsStateService.organization` |
| `filterSeverity.critical` | `SnykApplicationSettingsStateService.criticalSeverityEnabled` |
| `filterSeverity.high` | `SnykApplicationSettingsStateService.highSeverityEnabled` |
| `filterSeverity.medium` | `SnykApplicationSettingsStateService.mediumSeverityEnabled` |
| `filterSeverity.low` | `SnykApplicationSettingsStateService.lowSeverityEnabled` |
| `issueViewOptions.openIssues` | `SnykApplicationSettingsStateService.openIssuesEnabled` |
| `issueViewOptions.ignoredIssues` | `SnykApplicationSettingsStateService.ignoredIssuesEnabled` |
| `enableDeltaFindings` | `SnykApplicationSettingsStateService.issuesToDisplay` |
| `authenticationMethod` | `SnykApplicationSettingsStateService.authenticationType` |
| `endpoint` | `SnykApplicationSettingsStateService.customEndpointUrl` |
| `insecure` | `SnykApplicationSettingsStateService.ignoreUnknownCA` |
| `token` | `SnykApplicationSettingsStateService.token` |
| `folderConfigs[].preferredOrg` | `FolderConfigSettings.setOrganization()` |
| `folderConfigs[].orgSetByUser` | `FolderConfigSettings.setAutoOrganization()` |
| `folderConfigs[].additionalParameters` | `FolderConfig.additionalParameters` |

---

## Fallback HTML Structure

When LS is not available (CLI not downloaded), the fallback HTML:
- Uses same `styles.css` from `snyk-ls-2/infrastructure/configuration/template/` for visual consistency
- Contains only "Executable Settings" section
- Implements simplified `collectData()` and `__ideSaveConfig__` pattern

### Fallback JSON Structure

```json
{
  "cliPath": "/path/to/snyk-cli",
  "manageBinariesAutomatically": true,
  "cliBaseDownloadURL": "https://downloads.snyk.io",
  "cliReleaseChannel": "stable"
}
```

### Fallback Mapping

| JSON Field | Plugin Setting |
|------------|----------------|
| `cliPath` | `SnykApplicationSettingsStateService.cliPath` |
| `manageBinariesAutomatically` | `SnykApplicationSettingsStateService.manageBinariesAutomatically` |
| `cliBaseDownloadURL` | `SnykApplicationSettingsStateService.cliBaseDownloadURL` |
| `cliReleaseChannel` | `SnykApplicationSettingsStateService.cliReleaseChannel` |
