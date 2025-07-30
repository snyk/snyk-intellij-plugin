#!/bin/bash

# Script to run UI tests for Snyk IntelliJ Plugin
# This script handles the complexities of starting IDE with robot-server and running tests

set -e

echo "=== Snyk IntelliJ Plugin UI Test Runner ==="
echo

# Configuration
ROBOT_PORT="${ROBOT_PORT:-8082}"

start_ide_with_robot() {
    echo "Starting IDE with Robot Server on port $ROBOT_PORT..."
    
    # Build the plugin first
    echo "Building plugin..."
    ./gradlew buildPlugin
    
    # Run IDE with robot-server using the configured task
    echo "Starting IDE with robot-server..."
    ./gradlew runIdeForUiTests &
    
    IDE_PID=$!
    echo "IDE started with PID: $IDE_PID"
    
    # Wait for robot server to be ready
    echo "Waiting for Robot Server to be ready..."
    local max_attempts=60
    local attempt=0
    
    while [ $attempt -lt $max_attempts ]; do
        if curl -s "http://localhost:$ROBOT_PORT" > /dev/null 2>&1; then
            echo "✅ Robot Server is ready at http://localhost:$ROBOT_PORT"
            return 0
        fi
        
        attempt=$((attempt + 1))
        echo "Waiting for Robot Server... (attempt $attempt/$max_attempts)"
        sleep 2
    done
    
    echo "❌ Robot Server failed to start within timeout"
    return 1
}

run_ui_tests() {
    echo
    echo "Running E2E tests..."
    
    # Run E2E tests using the dedicated task
    ./gradlew runE2ETests --info
    
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