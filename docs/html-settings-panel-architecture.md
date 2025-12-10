# HTML Settings Panel Architecture

This document describes the architecture of the HTML-based settings panel, including the interaction between the IntelliJ IDE, Language Server, and JCEF browser.

## Overview

```mermaid
flowchart TB
    subgraph IDE["IntelliJ IDE"]
        subgraph Settings["Settings Dialog"]
            SPC[SnykProjectSettingsConfigurable]
            HSP[HTMLSettingsPanel]
            SCH[SaveConfigHandler]
        end
        
        subgraph Services["Services"]
            SASS[SnykApplicationSettingsStateService]
            FCS[FolderConfigSettings]
            LSW[LanguageServerWrapper]
            AUTH[AuthenticationService]
        end
        
        subgraph Theme["Theme System"]
            UIM[UIManager]
        end
    end
    
    subgraph JCEF["JCEF Browser"]
        subgraph HTML["HTML Content"]
            LSHTML[LS Config HTML]
            FBHTML[Fallback HTML]
        end
        
        subgraph JS["JavaScript"]
            DT[DirtyTracker]
            SC[Scripts.js]
        end
        
        subgraph Bridge["IDE Bridge Functions"]
            SAVE["__saveIdeConfig__(json)"]
            DIRTY["__onFormDirtyChange__(bool)"]
            LOGIN["__ideLogin__(config)"]
            LOGOUT["__ideLogout__()"]
        end
    end
    
    subgraph LS["Language Server"]
        CHR[ConfigHtmlRenderer]
        CMD[Configuration Command]
    end

    SPC --> |creates| HSP
    HSP --> |getConfigHtml| LSW
    LSW --> |executeCommand| CMD
    CMD --> |renders| CHR
    CHR --> |returns HTML| LSW
    LSW --> |HTML string| HSP
    HSP --> |creates| SCH
    HSP --> |loadHTML + nonce| JCEF
    
    HSP --> |fallback if LS unavailable| FBHTML
    
    UIM --> |colors, fonts| HSP
    HSP --> |injects CSS + nonce| JCEF
    
    %% Bridge function connections
    SAVE --> |calls| SCH
    SCH --> |applyGlobalSettings| SASS
    SCH --> |applyFolderConfigs| FCS
    
    DIRTY --> |calls| SCH
    SCH --> |onModified| HSP
    
    LOGIN --> |calls| SCH
    SCH --> |updateConfiguration| LSW
    SCH --> |authenticate| AUTH
    
    LOGOUT --> |calls| SCH
    SCH --> |logout| LSW
```

## Sequence: Loading Settings Panel

```mermaid
sequenceDiagram
    participant User
    participant IDE as IntelliJ IDE
    participant HSP as HTMLSettingsPanel
    participant SCH as SaveConfigHandler
    participant LSW as LanguageServerWrapper
    participant LS as Language Server
    participant JCEF as JCEF Browser

    User->>IDE: Open Settings > Snyk
    IDE->>HSP: createComponent()
    HSP->>LSW: getConfigHtml()
    LSW->>LS: executeCommand("snyk.workspace.configuration")
    
    alt LS Available
        LS-->>LSW: HTML string with config form
        LSW-->>HSP: HTML content
    else LS Not Available
        LSW-->>HSP: null
        HSP->>HSP: loadFallbackHtml()
    end
    
    HSP->>HSP: generateNonce()
    Note over HSP: SecureRandom → Base64 (16 bytes)
    HSP->>HSP: html.replace("ideNonce", nonce)
    
    HSP->>HSP: getThemeCssVariables()
    Note over HSP: Extract colors & fonts from UIManager
    
    HSP->>SCH: generateSaveConfigHandler(browser, themeCss, nonce)
    HSP->>JCEF: loadHTML(htmlWithNonce)
    
    JCEF->>SCH: onLoadEnd
    SCH->>JCEF: inject CSS with nonce attribute
    SCH->>JCEF: inject bridge functions
    JCEF-->>User: Display settings form
```

## Sequence: Saving Configuration

```mermaid
sequenceDiagram
    participant User
    participant JCEF as JCEF Browser
    participant JS as JavaScript
    participant Bridge as Bridge Functions
    participant SCH as SaveConfigHandler
    participant Settings as Settings Services
    participant LSW as LanguageServerWrapper

    User->>JCEF: Modify form fields
    JCEF->>JS: input/change event
    JS->>JS: DirtyTracker.checkDirty()
    JS->>Bridge: __onFormDirtyChange__(true)
    Bridge->>SCH: onModified callback
    Note over SCH: Enable Apply button

    User->>JCEF: Click Apply
    JCEF->>JS: getAndSaveIdeConfig()
    JS->>JS: collectData() → JSON
    JS->>Bridge: __saveIdeConfig__(jsonString)
    Bridge->>SCH: parseAndSaveConfig()
    
    SCH->>Settings: Update SnykApplicationSettingsStateService
    SCH->>Settings: Update FolderConfigSettings
    SCH->>LSW: updateConfiguration()
    
    JS->>JS: DirtyTracker.reset()
    JS->>Bridge: __onFormDirtyChange__(false)
```

## Sequence: Authentication Flow

```mermaid
sequenceDiagram
    participant User
    participant JCEF as JCEF Browser
    participant Bridge as Bridge Functions
    participant SCH as SaveConfigHandler
    participant LSW as LanguageServerWrapper
    participant Auth as AuthenticationService

    User->>JCEF: Click "Authenticate"
    JCEF->>Bridge: __ideLogin__(configJson)
    Bridge->>SCH: Save config first
    SCH->>LSW: updateConfiguration()
    SCH->>Auth: authenticate()
    Auth->>LSW: OAuth/Token flow
    LSW-->>JCEF: Refresh with auth status

    User->>JCEF: Click "Logout"
    JCEF->>Bridge: __ideLogout__()
    Bridge->>SCH: logout handler
    SCH->>LSW: logout()
```

## Theme Integration

```mermaid
flowchart LR
    subgraph IDE["IntelliJ Theme System"]
        UIM[UIManager]
        PC["Panel.background"]
        LF["Label.foreground"]
        TF["TextField.background"]
        SB["ScrollBar.thumbColor"]
        FNT["Label.font"]
    end
    
    subgraph HSP["HTMLSettingsPanel"]
        GTC[getThemeCssVariables]
    end
    
    subgraph CSS["Injected CSS Variables"]
        VBG["--vscode-editor-background"]
        VFG["--vscode-foreground"]
        VIB["--vscode-input-background"]
        VSB["--vscode-scrollbarSlider-background"]
        VFF["--vscode-font-family"]
        VFS["--vscode-font-size"]
    end
    
    subgraph JCEF["JCEF Browser"]
        STYLE[":root { ... }"]
        HTML["HTML uses var(--vscode-*)"]
    end
    
    UIM --> GTC
    PC --> VBG
    LF --> VFG
    TF --> VIB
    SB --> VSB
    FNT --> VFF
    FNT --> VFS
    
    GTC --> STYLE
    STYLE --> HTML
```

## Bridge Functions Reference

### IDE-Injected Functions (JS → IDE)

| Function | Purpose | Handler |
|----------|---------|---------|
| `__saveIdeConfig__(json)` | Save configuration JSON to IDE settings | `SaveConfigHandler.parseAndSaveConfig()` |
| `__onFormDirtyChange__(isDirty)` | Notify IDE of form dirty state changes | `SaveConfigHandler` → `onModified()` |
| `__ideLogin__(configJson)` | Trigger authentication (saves config first) | `SaveConfigHandler` → `AuthenticationService` |
| `__ideLogout__()` | Trigger logout | `SaveConfigHandler` → `LanguageServerWrapper.logout()` |

### LS-Provided Functions (IDE → JS)

| Function | Purpose | Called By |
|----------|---------|-----------|
| `getAndSaveIdeConfig()` | Collect form data and trigger save | `HTMLSettingsPanel.apply()` |
| `__isFormDirty__()` | Query current dirty state | (available for IDE use) |
| `__resetDirtyState__()` | Reset dirty tracker | (available for IDE use) |

### Fallback HTML Functions

| Function | Purpose |
|----------|---------|
| `saveConfig()` | Collect form data and call `__saveIdeConfig__` |

## Content Security Policy (CSP) & Nonce

The LS HTML includes a Content Security Policy that requires nonces for inline styles:

```html
<meta http-equiv="Content-Security-Policy"
    content="style-src 'self' 'nonce-{{.Nonce}}' https://cdn.jsdelivr.net; ..." />
```

### Nonce Flow

1. **LS renders HTML** with placeholder `nonce="ideNonce"`
2. **HTMLSettingsPanel.generateNonce()** creates secure random nonce (Base64, 16 bytes)
3. **Replace placeholder**: `html.replace("ideNonce", actualNonce)`
4. **Pass to SaveConfigHandler**: `generateSaveConfigHandler(browser, themeCss, nonce)`
5. **Inject CSS with nonce**: `style.setAttribute('nonce', nonce)`

This allows the IDE to inject theme CSS while respecting the LS's security policy.

## CSS Variables Mapping

| CSS Variable | UIManager Key | Fallback |
|--------------|---------------|----------|
| `--vscode-font-family` | `Label.font.family` | `Inter, system-ui` |
| `--vscode-font-size` | `Label.font.size` | `13px` |
| `--vscode-editor-background` | `Panel.background` | `#1e1e1e` |
| `--vscode-foreground` | `Label.foreground` | `#cccccc` |
| `--vscode-input-background` | `TextField.background` | `#3c3c3c` |
| `--vscode-input-foreground` | `TextField.foreground` | `#cccccc` |
| `--vscode-input-border` | `Component.borderColor` | `#454545` |
| `--vscode-button-background` | `Button.default.startBackground` | `#0e639c` |
| `--vscode-focusBorder` | `Component.focusColor` | `#007acc` |
| `--vscode-scrollbarSlider-background` | `ScrollBar.thumbColor` | `#424242` |

## File Structure

```
src/main/kotlin/io/snyk/plugin/
├── ui/
│   ├── settings/
│   │   └── HTMLSettingsPanel.kt      # Main panel, theme injection, nonce generation
│   └── jcef/
│       └── SaveConfigHandler.kt      # Bridge functions, config parsing, CSP-aware CSS injection
├── settings/
│   └── SnykProjectSettingsConfigurable.kt  # Settings entry point (switches old/new dialog)
└── services/
    └── SnykApplicationSettingsStateService.kt  # Global settings storage

src/main/kotlin/snyk/common/lsp/
├── LanguageServerWrapper.kt          # getConfigHtml(), logout(), updateConfiguration()
└── settings/
    └── FolderConfigSettings.kt       # Folder-specific settings storage

src/main/resources/html/
└── settings-fallback.html            # Fallback when LS unavailable (CLI settings only)

snyk-ls (Language Server):
└── infrastructure/configuration/
    ├── config_html.go                # HTML renderer, CSP with nonce placeholder
    └── template/
        ├── config.html               # Main template with CSP meta tag
        ├── styles.css                # Styling (uses --vscode-* CSS variables)
        ├── scripts.js                # Form handling, collectData(), getAndSaveIdeConfig()
        ├── dirty-tracker.js          # Change detection, calls __onFormDirtyChange__
        └── utils.js                  # Utilities (deepClone, debounce, etc.)
```
