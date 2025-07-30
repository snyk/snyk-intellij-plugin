#!/bin/bash

# Script to run UI tests for Snyk IntelliJ Plugin
# This script handles the complexities of starting IDE with robot-server and running tests

set -e

echo "=== Snyk IntelliJ Plugin UI Test Runner ==="
echo

# Configuration
ROBOT_PORT="${ROBOT_PORT:-8082}"
ROBOT_SERVER_VERSION="0.11.23"
ROBOT_SERVER_URL="https://plugins.jetbrains.com/plugin/download?rel=true&updateId=465614"
ROBOT_SERVER_PATH="build/robot-server-plugin.zip"

# Functions
download_robot_server() {
    if [ ! -f "$ROBOT_SERVER_PATH" ]; then
        echo "Downloading Robot Server Plugin..."
        mkdir -p "$(dirname "$ROBOT_SERVER_PATH")"
        curl -L "$ROBOT_SERVER_URL" -o "$ROBOT_SERVER_PATH"
        echo "Robot Server Plugin downloaded."
    else
        echo "Robot Server Plugin already downloaded."
    fi
}

start_ide_with_robot() {
    echo "Starting IDE with Robot Server on port $ROBOT_PORT..."
    
    # Build the plugin first
    ./gradlew buildPlugin
    
    # Run IDE with system properties and robot server plugin
    ./gradlew runIde \
        -Drobot-server.port="$ROBOT_PORT" \
        -Dide.mac.message.dialogs.as.sheets=false \
        -Djb.privacy.policy.text="<!--999.999-->" \
        -Djb.consents.confirmation.enabled=false \
        -Didea.trust.all.projects=true \
        -Dide.show.tips.on.startup.default.value=false \
        -PrunIdeWithPlugins="$ROBOT_SERVER_PATH" &
    
    IDE_PID=$!
    echo "IDE started with PID: $IDE_PID"
    
    # Wait for IDE to be ready
    echo "Waiting for IDE to start..."
    sleep 30
    
    # Check if robot server is accessible
    if curl -s "http://localhost:$ROBOT_PORT" > /dev/null; then
        echo "Robot Server is ready at http://localhost:$ROBOT_PORT"
    else
        echo "Warning: Robot Server may not be ready yet"
    fi
}

run_ui_tests() {
    echo
    echo "Running UI tests..."
    
    # Run only E2E tests
    ./gradlew test --tests "*E2ETest" --info
    
    TEST_RESULT=$?
    return $TEST_RESULT
}

cleanup() {
    echo
    echo "Cleaning up..."
    if [ ! -z "$IDE_PID" ]; then
        echo "Stopping IDE (PID: $IDE_PID)..."
        kill $IDE_PID 2>/dev/null || true
        wait $IDE_PID 2>/dev/null || true
    fi
}

# Main execution
main() {
    # Set up cleanup on exit
    trap cleanup EXIT
    
    # Download robot server if needed
    download_robot_server
    
    # Start IDE with robot server
    start_ide_with_robot
    
    # Run UI tests
    run_ui_tests
    TEST_RESULT=$?
    
    # Cleanup happens automatically via trap
    
    echo
    if [ $TEST_RESULT -eq 0 ]; then
        echo "✅ UI tests passed!"
    else
        echo "❌ UI tests failed!"
        exit $TEST_RESULT
    fi
}

# Handle script arguments
case "${1:-}" in
    --help|-h)
        echo "Usage: $0 [--port PORT]"
        echo "  --port PORT    Robot server port (default: 8082)"
        echo "  --help         Show this help"
        exit 0
        ;;
    --port)
        ROBOT_PORT="${2:-8082}"
        shift 2
        ;;
esac

# Run main function
main "$@"