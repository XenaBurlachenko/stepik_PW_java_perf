@echo off
REM Run tests script for Windows

echo 🚀 Running Playwright Java Tests

REM Run all tests
call mvn clean test

REM Generate Allure report
call mvn allure:report

echo 📊 Report generated in target/site/allure-maven-plugin
