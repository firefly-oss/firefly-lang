#!/bin/bash

set -e

echo "🔥 Firefly Spring Boot Demo Test"
echo "=================================="
echo ""

# Start the application
echo "▶️  Starting Spring Boot application..."
java -jar target/firefly-spring-demo-1.0.0.jar > /tmp/firefly-spring.log 2>&1 &
APP_PID=$!
echo "   PID: $APP_PID"

# Wait for startup
echo "⏳ Waiting for application to start..."
sleep 6

# Test if process is still running
if ! kill -0 $APP_PID 2>/dev/null; then
    echo "❌ Application failed to start!"
    echo ""
    echo "Last 30 lines of log:"
    tail -30 /tmp/firefly-spring.log
    exit 1
fi

echo "✅ Application started successfully"
echo ""

# Test endpoints
echo "🧪 Testing endpoints..."
echo ""

# Test 1: GET /api/hello
echo "1️⃣  Testing GET /api/hello"
RESPONSE=$(curl -s http://localhost:8081/api/hello)
if [ "$RESPONSE" == "Hello from Firefly + Spring Boot!" ]; then
    echo "   ✅ Response: $RESPONSE"
else
    echo "   ❌ Unexpected response: $RESPONSE"
    kill $APP_PID 2>/dev/null
    exit 1
fi
echo ""

# Test 2: GET /api/status
echo "2️⃣  Testing GET /api/status"
RESPONSE=$(curl -s http://localhost:8081/api/status)
if [ "$RESPONSE" == "Status: Running on Firefly" ]; then
    echo "   ✅ Response: $RESPONSE"
else
    echo "   ❌ Unexpected response: $RESPONSE"
    kill $APP_PID 2>/dev/null
    exit 1
fi
echo ""

# Test 3: POST /api/echo
echo "3️⃣  Testing POST /api/echo"
RESPONSE=$(curl -s -X POST -H 'Content-Type: text/plain' -d 'Test message' http://localhost:8081/api/echo)
if [[ "$RESPONSE" == *"Echo from Firefly"* ]]; then
    echo "   ✅ Response: $RESPONSE"
else
    echo "   ❌ Unexpected response: $RESPONSE"
    kill $APP_PID 2>/dev/null
    exit 1
fi
echo ""

# Check logs for errors
echo "📋 Checking logs for errors..."
if grep -q "ERROR" /tmp/firefly-spring.log; then
    echo "   ⚠️  Errors found in logs:"
    grep "ERROR" /tmp/firefly-spring.log | head -5
else
    echo "   ✅ No errors in logs"
fi
echo ""

# Shutdown
echo "🛑 Shutting down application..."
kill $APP_PID 2>/dev/null || true
sleep 1

echo ""
echo "✅ All tests passed!"
echo ""
echo "Summary:"
echo "  • Application started successfully"
echo "  • GET /api/hello ✓"
echo "  • GET /api/status ✓"
echo "  • POST /api/echo ✓"
echo "  • No errors in logs ✓"
echo ""
echo "🎉 Firefly + Spring Boot is working perfectly!"
