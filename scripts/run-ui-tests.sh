#!/bin/bash

# Script to run UI tests for Snyk IntelliJ Plugin
# This script handles the complexities of starting IDE with robot-server and running tests

set -e

echo "=== Snyk IntelliJ Plugin UI Test Runner ==="
echo

# Configuration
ROBOT_PORT="${ROBOT_PORT:-8082}"
MODE="${MODE:-auto}"  # auto, ide-only, test-only

start_ide_with_robot() {
    echo "Starting IDE with Robot Server on port $ROBOT_PORT..."
    
    # Build the plugin first
    echo "Building plugin..."
    ./gradlew buildPlugin
    
    # Run IDE with robot-server
    if [ "$MODE" = "ide-only" ]; then
        echo "Starting IDE with robot-server (interactive mode)..."
        echo "The IDE will stay open. Run tests in another terminal with:"
        echo "  ./scripts/run-ui-tests.sh --mode test-only"
        echo
        ./gradlew runIdeForUiTests
    else
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
    fi
}

wait_for_robot_server() {
    echo "Checking for Robot Server on port $ROBOT_PORT..."
    local max_attempts=5
    local attempt=0
    
    while [ $attempt -lt $max_attempts ]; do
        if curl -s "http://localhost:$ROBOT_PORT" > /dev/null 2>&1; then
            echo "✅ Robot Server is available at http://localhost:$ROBOT_PORT"
            return 0
        fi
        
        attempt=$((attempt + 1))
        echo "Robot Server not found... (attempt $attempt/$max_attempts)"
        sleep 2
    done
    
    echo "❌ Robot Server is not running. Please start the IDE first with:"
    echo "  ./scripts/run-ui-tests.sh --mode ide-only"
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
    case "$MODE" in
        ide-only)
            # Just start IDE, no cleanup trap
            start_ide_with_robot
            ;;
        test-only)
            # Just run tests, assume IDE is already running
            if wait_for_robot_server; then
                run_ui_tests
                TEST_RESULT=$?
                echo
                if [ $TEST_RESULT -eq 0 ]; then
                    echo "✅ UI tests passed!"
                else
                    echo "❌ UI tests failed!"
                    exit $TEST_RESULT
                fi
            else
                exit 1
            fi
            ;;
        auto|*)
            # Default: start IDE and run tests, then cleanup
            trap cleanup EXIT
            start_ide_with_robot
            run_ui_tests
            TEST_RESULT=$?
            echo
            if [ $TEST_RESULT -eq 0 ]; then
                echo "✅ UI tests passed!"
            else
                echo "❌ UI tests failed!"
                exit $TEST_RESULT
            fi
            ;;
    esac
}

# Handle script arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        --help|-h)
            echo "Usage: $0 [OPTIONS]"
            echo "Options:"
            echo "  --mode MODE    Execution mode: auto, ide-only, test-only (default: auto)"
            echo "                 auto: Start IDE and run tests (all-in-one)"
            echo "                 ide-only: Start IDE and keep it running (terminal 1)"
            echo "                 test-only: Run tests only (terminal 2)"
            echo "  --port PORT    Robot server port (default: 8082)"
            echo "  --help         Show this help"
            echo
            echo "Examples:"
            echo "  # All-in-one (default):"
            echo "  $0"
            echo
            echo "  # Two-terminal approach:"
            echo "  # Terminal 1:"
            echo "  $0 --mode ide-only"
            echo "  # Terminal 2:"
            echo "  $0 --mode test-only"
            exit 0
            ;;
        --mode)
            MODE="$2"
            shift 2
            ;;
        --port)
            ROBOT_PORT="$2"
            shift 2
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Run main function
main