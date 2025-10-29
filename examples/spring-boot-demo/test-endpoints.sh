#!/bin/bash
# Test script for Flylang Spring Boot Demo

echo "Starting Spring Boot application..."
mvn spring-boot:run > /tmp/spring-boot-test.log 2>&1 &
SPRING_PID=$!

echo "Waiting for application to start..."
sleep 8

echo ""
echo "========================================="
echo "Testing GET /hello"
echo "========================================="
curl -s http://localhost:8080/hello
echo ""

echo ""
echo "========================================="
echo "Testing GET /users/{id}?greet={name}"
echo "========================================="
curl -s 'http://localhost:8080/users/123?greet=Alice'
echo ""

echo ""
echo "========================================="
echo "Testing POST /users"
echo "========================================="
curl -s -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{"id":"999","name":"TestUser"}'
echo ""

echo ""
echo "========================================="
echo "Stopping application..."
echo "========================================="
kill $SPRING_PID 2>/dev/null
wait $SPRING_PID 2>/dev/null

echo ""
echo "✅ All tests completed!"
