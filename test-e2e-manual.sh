#!/bin/bash
# Script to manually test E2E with proper sequence

echo "Starting E2E test sequence..."
echo "1. First, let's make sure no IDE is running"
ps aux | grep -i "idea" | grep -v grep && echo "IDE is running" || echo "No IDE running"

echo ""
echo "2. Starting IDE with robot-server..."
./gradlew runIdeForUiTests > /tmp/ide-ui-test.log 2>&1 &
IDE_PID=$!
echo "IDE started with PID: $IDE_PID"

echo ""
echo "3. Waiting for robot server to be ready..."
for i in {1..30}; do
    if curl -s http://localhost:8082 > /dev/null; then
        echo "Robot server is ready!"
        break
    fi
    echo -n "."
    sleep 2
done

echo ""
echo "4. Checking robot server status..."
curl -s http://localhost:8082 > /dev/null && echo "✓ Robot server is running" || echo "✗ Robot server is NOT running"

echo ""
echo "5. Running E2E test..."
./gradlew runE2ETests --tests "SnykAuthE2ETest" 2>&1 | tee /tmp/e2e-test.log

echo ""
echo "6. Checking for debug output..."
grep -E "(Found|Setting|Clicking|Entered)" /tmp/e2e-test.log | tail -20

echo ""
echo "7. Cleaning up..."
kill $IDE_PID 2>/dev/null
echo "Done!"