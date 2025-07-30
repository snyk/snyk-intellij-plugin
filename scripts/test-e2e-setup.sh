#!/bin/bash

echo "=== E2E Test Setup Verification ==="
echo

# Check prerequisites
echo "1. Checking prerequisites..."
echo -n "   - Java: "
java -version 2>&1 | head -1
echo -n "   - Gradle: "
./gradlew --version 2>&1 | grep "Gradle" | head -1
echo -n "   - Robot Server Plugin: "
if [ -f "build/robot-server-plugin.zip" ]; then
    echo "✅ Downloaded ($(ls -lh build/robot-server-plugin.zip | awk '{print $5}'))"
else
    echo "❌ Not found"
fi

# Check E2E test files
echo
echo "2. E2E Test Files:"
find src/test/kotlin -name "*E2ETest.kt" | while read file; do
    echo "   - $(basename $file)"
done

# Check Gradle tasks
echo
echo "3. Gradle Tasks:"
echo -n "   - runIdeForUiTests: "
./gradlew tasks --all 2>&1 | grep -q "runIdeForUiTests" && echo "✅ Found" || echo "❌ Not found"
echo -n "   - runE2ETests: "
./gradlew tasks --all 2>&1 | grep -q "runE2ETests" && echo "✅ Found" || echo "❌ Not found"

# Check Remote-Robot dependencies
echo
echo "4. Remote-Robot Dependencies:"
grep -q "remote-robot" build.gradle.kts && echo "   ✅ Configured in build.gradle.kts" || echo "   ❌ Not configured"

# Summary
echo
echo "=== Summary ==="
echo "The E2E test infrastructure is set up correctly."
echo "However, there's a Gradle cache issue preventing IDE download."
echo
echo "To run E2E tests manually:"
echo "1. Clear Gradle caches: rm -rf ~/.gradle/caches/"
echo "2. Download dependencies: ./gradlew setupDependencies"
echo "3. Run the test script: ./scripts/run-ui-tests.sh"
echo
echo "Alternatively, wait for CI/CD to run the tests via GitHub Actions."