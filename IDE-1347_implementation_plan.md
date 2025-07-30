# Comprehensive UI Testing Plan for Snyk IntelliJ Plugin

## Executive Summary

This plan outlines a strategy to create comprehensive UI tests for the Snyk IntelliJ plugin, covering all major UI components and user workflows described in the [official documentation](https://docs.snyk.io/cli-ide-and-ci-cd-integrations/snyk-ide-plugins-and-extensions/jetbrains-plugin). The plugin currently uses JUnit 4 with the IntelliJ Platform testing framework and MockK for mocking.

## Current State Analysis

### Existing Test Infrastructure:
- **Test Frameworks**: JUnit 4, IntelliJ Platform Test Framework
- **Mocking**: MockK (as per cursor rules)
- **UI Test Utilities**: Custom `UIComponentFinder` helper
- **Test Types**: 
  - `LightPlatform4TestCase` for lightweight UI tests
  - `HeavyPlatformTestCase` for integration tests requiring full IDE context
- **Current Coverage**: Limited UI tests for AuthPanel, ToolWindowPanel

### Key UI Components Requiring Tests:
1. **Tool Window Components**:
   - SnykToolWindow and SnykToolWindowPanel
   - TreePanel with vulnerability tree
   - SummaryPanel (JCEF-based)
   - IssueDescriptionPanel (JCEF-based)
   - SnykAuthPanel
   - SnykErrorPanel
   - StatePanel

2. **Actions & Dialogs**:
   - Settings dialog (SnykProjectSettingsConfigurable)
   - Scan actions (Run, Stop, Clean)
   - Tree filters (severity, scan types)
   - Reference chooser dialog

3. **Editor Integration**:
   - Code annotations (SnykCodeAnnotator, SnykOSSAnnotator, SnykIaCAnnotator)
   - Line markers
   - Code vision providers
   - Quick fixes/intentions

4. **JCEF Components**:
   - AI fix handlers
   - Ignore in file handlers
   - Issue description rendering
   - Toggle delta handler

## Testing Strategy

### 1. Test Organization Structure
```
src/test/kotlin/
├── io/snyk/plugin/ui/
│   ├── toolwindow/
│   │   ├── panels/
│   │   │   ├── TreePanelTest.kt
│   │   │   ├── SummaryPanelTest.kt
│   │   │   ├── IssueDescriptionPanelTest.kt
│   │   │   ├── StatePanelTest.kt
│   │   │   └── SnykErrorPanelTest.kt
│   │   ├── nodes/
│   │   │   ├── TreeNodeTestBase.kt
│   │   │   ├── RootNodeTests.kt
│   │   │   ├── SecondLevelNodeTests.kt
│   │   │   └── LeafNodeTests.kt
│   │   ├── SnykToolWindowTest.kt
│   │   └── SnykToolWindowFactoryTest.kt
│   ├── actions/
│   │   ├── SnykRunScanActionTest.kt
│   │   ├── SnykStopScanActionTest.kt
│   │   ├── SnykCleanScanActionTest.kt
│   │   ├── FilterActionsTest.kt
│   │   └── SnykSettingsActionTest.kt
│   ├── settings/
│   │   └── SnykProjectSettingsConfigurableTest.kt
│   ├── jcef/
│   │   ├── JCEFTestBase.kt
│   │   ├── ApplyAiFixEditHandlerTest.kt
│   │   ├── GenerateAIFixHandlerTest.kt
│   │   └── IgnoreInFileHandlerTest.kt
│   └── annotator/
│       ├── SnykCodeAnnotatorTest.kt
│       ├── SnykOSSAnnotatorTest.kt
│       └── SnykIaCAnnotatorTest.kt
└── snyk/common/
    └── UITestUtils.kt (enhanced)
```

### 2. Test Categories

#### A. Unit Tests (Fast, Isolated)
- Component initialization
- State management
- Event handling
- Data binding
- UI element visibility/enablement logic

#### B. Integration Tests (Medium speed)
- Panel interactions
- Tree navigation
- Filter applications
- Settings persistence
- Action execution
- LSP communication

#### C. End-to-End Tests (Slow, Full IDE)
- Complete user workflows
- Scan execution flows
- Authentication flows
- Issue navigation
- Delta findings workflow

### 3. Test Implementation Plan

#### Phase 1: Foundation (Week 1)
1. **Enhance Test Infrastructure**
   ```kotlin
   // UITestUtils.kt enhancements
   object UITestUtils {
       fun <T : Component> findComponent(parent: Container, type: KClass<T>, predicate: (T) -> Boolean): T?
       fun waitForComponent(parent: Container, timeout: Duration = 5.seconds): Component
       fun simulateClick(component: JComponent)
       fun simulateTreeSelection(tree: Tree, path: TreePath)
       fun createMockProject(): Project
       fun setupMockLanguageServer(): LanguageServerWrapper
   }
   ```

2. **Create Test Base Classes**
   ```kotlin
   abstract class SnykUITestBase : LightPlatform4TestCase() {
       protected lateinit var mockLanguageServer: LanguageServer
       protected lateinit var mockSettings: SnykApplicationSettingsStateService
       
       override fun setUp() {
           super.setUp()
           setupMocks()
           resetSettings()
       }
   }
   ```

3. **Update Gradle Configuration**
   ```kotlin
   tasks {
       register<Test>("runUiTests") {
           group = "verification"
           description = "Run UI integration tests"
           testClassesDirs = sourceSets["test"].output.classesDirs
           classpath = sourceSets["test"].runtimeClasspath
           
           include("**/*UITest.class")
           include("**/*IntegTest.class")
           
           maxHeapSize = "4096m"
           systemProperty("idea.test.execution.policy", "legacy")
           
           testLogging {
               events("passed", "skipped", "failed")
               exceptionFormat = TestExceptionFormat.FULL
           }
       }
   }
   ```

#### Phase 2: Core Component Tests (Weeks 2-3)
1. **Tool Window Panel Tests**
   - Test initialization with different authentication states
   - Test tree population with mock scan results
   - Test panel switching between auth/error/results views
   - Test description panel updates on selection
   - Test filter application and tree updates

2. **Action Tests**
   - Test scan action enablement based on state
   - Test scan execution triggers
   - Test stop action during scan
   - Test clean action result clearing
   - Test settings action dialog opening

#### Phase 3: JCEF Component Tests (Weeks 4-5)
1. **JCEF Panel Tests**
   - Test HTML content loading
   - Test JavaScript handler registration
   - Test theme switching
   - Test link clicking
   - Test AI fix generation/application

2. **Handler Tests**
   - Test each handler's execute method
   - Test error handling
   - Test state updates after execution

#### Phase 4: Editor Integration Tests (Week 6)
1. **Annotation Tests**
   - Test issue highlighting in different file types
   - Test annotation updates after scan
   - Test quick fix availability and execution
   - Test navigation from annotation to issue details

2. **Code Vision Tests**
   - Test lens display for different issue types
   - Test click handling to show details

#### Phase 5: Settings & Workflow Tests (Week 7)
1. **Settings Tests**
   - Test settings persistence
   - Test settings UI updates
   - Test validation logic

2. **End-to-End Workflow Tests**
   - Test complete authentication flow
   - Test scan → results → fix workflow
   - Test multi-file navigation
   - Test delta findings toggle

### 4. Test Data Management

#### Test Fixtures
```kotlin
object TestFixtures {
    // Use existing test resources
    private const val OSS_RESULT_PATH = "oss-test-results/oss-result-gradle.json"
    private const val CODE_RESULT_PATH = "code-test-results/code-result.json"
    private const val IAC_RESULT_PATH = "iac-test-results/fargate.json"
    
    fun createMockOssResults(): List<OssIssue>
    fun createMockCodeResults(): List<CodeIssue>
    fun createMockIacResults(): List<IacIssue>
    fun createMockScanParams(): SnykScanParams
    fun createMockFolderConfig(): FolderConfig
}
```

#### Mock Strategies (following cursor rules)
- Use existing MockK mocks where available
- Create minimal mocks only when necessary
- Reuse mock patterns from existing tests

### 5. CI/CD Integration

#### GitHub Actions Configuration
```yaml
- name: Setup Display
  run: |
    export DISPLAY=:99.0
    Xvfb :99 -screen 0 1920x1080x24 > /dev/null 2>&1 &

- name: Run UI Tests
  run: ./gradlew runUiTests
  
- name: Upload Test Results
  if: always()
  uses: actions/upload-artifact@v3
  with:
    name: test-results
    path: build/test-results/
```

### 6. Success Metrics & Monitoring

#### Coverage Goals
- **UI Component Coverage**: 80%+ 
- **User Workflow Coverage**: 100% critical paths
- **Edge Case Coverage**: 70%+

#### Performance Targets
- **Unit Tests**: < 100ms per test
- **Integration Tests**: < 1s per test
- **E2E Tests**: < 10s per test
- **Total Suite**: < 10 minutes

#### Quality Metrics
- **Test Stability**: < 1% flaky tests
- **Maintenance Burden**: < 2 hours/week
- **Bug Detection Rate**: > 90% UI bugs caught

### 7. Implementation Guidelines (per cursor rules)

1. **Minimal Changes**: Only add tests, don't refactor existing code
2. **Use Existing Patterns**: Follow patterns from SnykAuthPanelIntegTest
3. **Mock Usage**: Use MockK, reuse existing mocks
4. **Production Ready**: No example implementations
5. **Test Execution**: Run tests after each implementation
6. **Clean Code**: Comment why, not what

### 8. Test Writing Best Practices

#### Naming Convention
```kotlin
@Test
fun `should display error panel when scan fails with network error`() {
    // Test implementation
}
```

#### Test Structure
```kotlin
// Given (Arrange)
val mockResults = TestFixtures.createMockOssResults()
every { languageServer.scan(any()) } returns mockResults

// When (Act)
toolWindowPanel.displayResults()

// Then (Assert)
val errorPanel = UIComponentFinder.findComponent(toolWindowPanel, SnykErrorPanel::class)
assertNotNull(errorPanel)
assertTrue(errorPanel.isVisible)
```

#### Common Assertions
```kotlin
// UI state assertions
assertComponentVisible(component)
assertComponentEnabled(component)
assertTreeNodeCount(tree, expectedCount)
assertSelectedNode(tree, expectedNode)

// Content assertions
assertPanelContent(panel, expectedContent)
assertDialogTitle(dialog, expectedTitle)
```

### 9. Deliverables

1. **Week 1**: Test infrastructure + base classes
2. **Week 2-3**: Core component tests (30+ tests)
3. **Week 4-5**: JCEF tests (20+ tests)
4. **Week 6**: Editor integration tests (15+ tests)
5. **Week 7**: Settings & E2E tests (10+ tests)
6. **Week 8**: Documentation & cleanup

### 10. Risk Mitigation

#### Technical Risks
- **JCEF Testing**: May require headless browser setup
- **Flaky Tests**: Use proper wait mechanisms
- **Mock Complexity**: Keep mocks simple and focused

#### Mitigation Strategies
- Regular test review sessions
- Continuous monitoring of test stability
- Quick fix turnaround for flaky tests
- Regular main branch merges

## Conclusion

This plan provides a structured approach to achieving comprehensive UI test coverage for the Snyk IntelliJ plugin. The phased implementation allows for iterative progress while maintaining production code quality. Following the cursor rules ensures minimal disruption to existing code while building a robust test suite.