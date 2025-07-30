# E2E Test Status and Issues

## Current Status

The E2E test infrastructure for the Snyk IntelliJ plugin has been successfully implemented, but there are several known issues and limitations.

### ✅ Completed

1. **Test Infrastructure**
   - Created E2E tests using Remote-Robot framework
   - Configured Gradle tasks (`runIdeForUiTests`, `runE2ETests`)
   - Created helper script (`scripts/run-ui-tests.sh`)
   - Added GitHub Actions workflow for E2E tests

2. **E2E Test Coverage**
   - SnykAuthE2ETest - Authentication workflow
   - SnykOssScanE2ETest - OSS scanning with Java-Goof
   - SnykCodeSecurityE2ETest - Code Security with nodejs-goof
   - SnykIacScanE2ETest - IaC scanning with terraform-goof
   - SnykProjectTrustE2ETest - Project trust management
   - SnykWorkflowE2ETest - Complete user workflow

### ⚠️ Known Issues

1. **Component-Level UI Tests**
   - Failing with `InstanceNotRegisteredException: com.intellij.platform.settings.SettingsController`
   - This is a compatibility issue with IntelliJ Platform 2024.2
   - Workaround: Temporarily disabled component tests, focusing on E2E tests

2. **Robot Server Plugin**
   - The `runIdeForUiTests` task had missing required properties (fixed by adding `splitMode`)
   - Robot server plugin needs manual download and configuration
   - IDE startup with robot-server can be slow and sometimes fails

3. **Local Execution**
   - E2E tests require a running IDE instance with robot-server plugin
   - The test script works but requires patience for IDE startup
   - Gradle daemon compatibility issues may occur

### 🚀 Running E2E Tests

#### Prerequisites
- Java 17+ 
- Gradle 8.x
- Xvfb (for headless environments)

#### Local Execution

**Option 1: Using the helper script**
```bash
./scripts/run-ui-tests.sh
```

**Option 2: Manual steps**
```bash
# Terminal 1: Start IDE with robot-server
./gradlew runIdeForUiTests

# Wait for "Robot Server started on port 8082" message

# Terminal 2: Run E2E tests
./gradlew runE2ETests
```

#### CI/CD Execution

E2E tests run nightly via GitHub Actions:
- Workflow: `.github/workflows/e2e-tests.yml`
- Trigger: Schedule (2 AM UTC) or manual dispatch
- Matrix build for each E2E test

### 📋 Troubleshooting

1. **"InstanceNotRegisteredException" Error**
   - This affects component tests only
   - E2E tests should work as they run against a real IDE

2. **"splitMode property not set" Error**
   - Fixed by adding `splitMode = RunIdeBase.SplitMode.NONE` to runIdeForUiTests task

3. **Robot Server Not Starting**
   - Check if port 8082 is available
   - Ensure robot-server plugin is downloaded to `build/robot-server-plugin.zip`
   - Try increasing timeout in the script

4. **Tests Can't Connect to Robot Server**
   - Verify IDE is running with `ps aux | grep idea`
   - Check robot server: `curl http://localhost:8082`
   - May need to wait longer for IDE startup

### 🔮 Future Improvements

1. **Fix Component Tests**
   - Wait for IntelliJ Platform fix for SettingsController issue
   - Consider migrating to HeavyPlatformTestCase if needed

2. **Improve E2E Stability**
   - Add retry logic for flaky tests
   - Optimize IDE startup time
   - Better error reporting

3. **Expand Coverage**
   - Add tests for JCEF panels
   - Test more edge cases
   - Performance testing

## References

- [IntelliJ Platform Testing Guide](https://plugins.jetbrains.com/docs/intellij/testing-plugins.html)
- [Remote-Robot Documentation](https://github.com/JetBrains/intellij-ui-test-robot)
- [PR #722](https://github.com/snyk/snyk-intellij-plugin/pull/722)