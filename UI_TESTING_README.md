# UI Testing Guide for Snyk IntelliJ Plugin

## Overview

This guide explains how to write and run UI tests for the Snyk IntelliJ plugin. We use two approaches:

1. **Component-level tests** - Direct UI component testing using IntelliJ Platform Test Framework
2. **E2E tests** - Full end-to-end testing using Remote-Robot framework (recommended)

## Component-Level UI Tests

These tests directly instantiate UI components and test them in isolation.

### Setup

Base test class: `SnykUITestBase` extends `LightPlatform4TestCase`

### Example Test

```kotlin
class SnykAuthPanelUITest : SnykUITestBase() {
    @Test
    fun `should display authentication panel when not authenticated`() {
        // Given
        settings.token = null
        
        // When
        val authPanel = SnykAuthPanel(project)
        
        // Then
        val button = UIComponentFinder.getComponentByCondition(
            authPanel, 
            JButton::class
        ) { it.text == SnykAuthPanel.TRUST_AND_SCAN_BUTTON_TEXT }
        
        assertNotNull(button)
    }
}
```

## E2E UI Tests with Remote-Robot

Remote-Robot enables true UI automation by controlling a running IDE instance.

### Setup

1. Add dependencies to `build.gradle.kts`:
```kotlin
repositories {
    maven { url = uri("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies") }
}

dependencies {
    testImplementation("com.intellij.remoterobot:remote-robot:0.11.23")
    testImplementation("com.intellij.remoterobot:remote-fixtures:0.11.23")
}
```

2. Install Robot Server Plugin in the test IDE:
   - Download from: https://plugins.jetbrains.com/plugin/13620-robot-server-plugin
   - Or use the marketplace in IDE

### Running E2E Tests

1. **Start IDE with Robot Server**:
   ```bash
   ./gradlew runIde -Drobot-server.port=8082
   ```

2. **Run E2E tests** (in another terminal):
   ```bash
   ./gradlew runUiTests --tests "*E2ETest"
   ```

### Example E2E Test

```kotlin
class SnykAuthE2ETest {
    private lateinit var remoteRobot: RemoteRobot
    
    @Before
    fun setUp() {
        remoteRobot = RemoteRobot("http://127.0.0.1:8082")
    }
    
    @Test
    fun `should open Snyk tool window`() = with(remoteRobot) {
        // Wait for IDE
        waitFor(duration = Duration.ofSeconds(30)) {
            findAll<CommonContainerFixture>(
                byXpath("//div[@class='IdeFrameImpl']")
            ).isNotEmpty()
        }
        
        // Find and click Snyk tool window
        val ideFrame = find<CommonContainerFixture>(
            byXpath("//div[@class='IdeFrameImpl']")
        )
        
        val snykButton = ideFrame.find<JButtonFixture>(
            byXpath("//div[@tooltiptext='Snyk']")
        )
        snykButton.click()
        
        // Verify panel opened
        val authPanel = find<CommonContainerFixture>(
            byXpath("//div[@class='SnykAuthPanel']")
        )
        assertTrue(authPanel.isShowing)
    }
}
```

## Debugging UI Tests

### For Component Tests
- Use standard debugger breakpoints
- Check `UIComponentFinder` for component hierarchy
- Verify mocks are properly configured

### For E2E Tests
1. **View UI Hierarchy**: Open http://localhost:8082 while IDE is running
2. **XPath Helper**: Use browser DevTools to inspect elements
3. **Screenshots**: Add screenshots to failing tests for debugging

## Best Practices

1. **Test Isolation**: Each test should be independent
2. **Explicit Waits**: Use `waitFor` for async operations
3. **Descriptive Names**: Test names should describe the scenario
4. **Clean State**: Reset settings and close dialogs in tearDown
5. **Prefer E2E**: Use E2E tests for user workflows, component tests for logic

## Running Tests in CI

Add to your CI configuration:
```yaml
- name: Run UI Tests
  run: |
    ./gradlew runIde -Drobot-server.port=8082 &
    sleep 30  # Wait for IDE to start
    ./gradlew runUiTests
```

## Troubleshooting

### Component Tests Fail with Platform Errors
- Ensure test extends proper base class
- Check service mocking is correct
- Verify IntelliJ Platform version compatibility

### E2E Tests Can't Connect
- Verify Robot Server plugin is installed
- Check port 8082 is not in use
- Ensure IDE has started completely

### XPath Not Finding Elements
- Use http://localhost:8082 to inspect actual hierarchy
- Check for dynamic class names
- Use more specific attributes (text, accessiblename)

## Covered Test Scenarios

### Component Tests
1. **SnykAuthPanelUITest**
   - Authentication panel display when not authenticated
   - Authenticate button enablement state
   - Button action listener functionality

2. **SnykToolWindowUITest**  
   - Auth panel creation when not authenticated
   - Authenticate button state in tool window
   - Label text verification
   - Button click simulation

### E2E Tests

1. **SnykAuthE2ETest**
   - IDE startup and initialization
   - Snyk tool window opening
   - Authentication panel verification
   - Trust and scan button interaction

2. **SnykWorkflowE2ETest**
   - **Complete workflow test:**
     - IDE startup with project handling
     - Snyk tool window navigation
     - Authentication status checking
     - Scan triggering and monitoring
     - Results verification and tree navigation
   - **Settings navigation test:**
     - Opening IDE settings
     - Navigating to Snyk settings
     - Verifying settings panel elements
     - Token field and scan type checkboxes

3. **SnykOssScanE2ETest**
   - **OSS vulnerability scanning:**
     - Project opening
     - Enabling OSS scanning in settings
     - Triggering OSS-specific scan
     - Waiting for and verifying OSS results
     - Vulnerability details viewing
   - **OSS results filtering:**
     - Accessing filter options
     - Applying severity filters
     - Verifying filtered results

### Test Coverage by Feature

| Feature | Component Tests | E2E Tests |
|---------|----------------|-----------|
| Authentication | ✅ | ✅ |
| Tool Window | ✅ | ✅ |
| OSS Scanning | ❌ | ✅ |
| Code Security | ❌ | ❌ |
| IaC Scanning | ❌ | ❌ |
| Settings Panel | ❌ | ✅ |
| Results Tree | ❌ | ✅ |
| JCEF Panels | ❌ | ❌ |
| Actions/Buttons | Partial | ✅ |

### Scenarios Not Yet Covered

- Code Security scanning workflow
- IaC (Infrastructure as Code) scanning
- Fix suggestions and code actions
- Ignoring issues functionality
- Project trust management
- CLI download and updates
- Error handling scenarios
- Multi-project support
- Integration with IDE features (code navigation, quick fixes)

## Additional Resources

- [Remote-Robot Documentation](https://github.com/JetBrains/intellij-ui-test-robot)
- [IntelliJ Platform Testing](https://plugins.jetbrains.com/docs/intellij/testing-plugins.html)
- [UI Testing Best Practices](https://www.jetbrains.com/help/idea/testing.html)