#!/bin/bash
# Run tests script for Unix

echo "🚀 Running Playwright Java Tests"

# Run all tests
mvn clean test

# Generate Allure report
mvn allure:report

# Open Allure report
mvn allure:serve
