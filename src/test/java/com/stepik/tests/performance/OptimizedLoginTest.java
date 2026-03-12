package com.stepik.tests.performance;

import java.nio.file.Paths;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestReporter;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import com.microsoft.playwright.options.Cookie;

public class OptimizedLoginTest {
    static private Playwright playwright;
    static private Browser browser;
    private BrowserContext context;
    private Page page;
    static private List<Cookie> authCookies;
    
    private static final int PERFORMANCE_THRESHOLD_MS = 3000;
    private static final double TRACE_PROBABILITY = 0.1;
    private static final Random random = new Random();
    
    private static int totalTests = 0;
    private static int slowTests = 0;
    private static long totalDuration = 0;

    @BeforeAll
    static void setUpClass() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
        
        // Получаем cookies один раз для всех тестов
        try (BrowserContext loginContext = browser.newContext()) {
            Page loginPage = loginContext.newPage();
            authCookies = performLogin(loginPage);
        }
    }

    @BeforeEach
    void setUp() {
        context = browser.newContext();
        context.addCookies(authCookies);
        page = context.newPage();
    }

    @Test
    @DisplayName("Тест производительности авторизации")
    @Tag("performance")
    void testLoginPerformance(TestReporter reporter) {
        long startTime = System.currentTimeMillis();
        
        // Полный цикл авторизации
        page.navigate("https://the-internet.herokuapp.com/login");
        page.fill("#username", "tomsmith");
        page.fill("#password", "SuperSecretPassword!");
        page.click("button[type='submit']");
        
        // Проверка успешного входа
        Assertions.assertTrue(page.isVisible("text='You logged into a secure area!'"), 
            "Авторизация не удалась");
        
        long duration = System.currentTimeMillis() - startTime;
        
        // Добавляем метрику в JUnit отчет
        reporter.publishEntry("Время авторизации", duration + " мс");
        reporter.publishEntry("Лимит", PERFORMANCE_THRESHOLD_MS + " мс");
        reporter.publishEntry("Статус", duration < PERFORMANCE_THRESHOLD_MS ? "Успешно" : "Превышение");
        
        Assertions.assertTrue(duration < PERFORMANCE_THRESHOLD_MS,
            String.format("Авторизация заняла %d мс (превышает лимит %d мс)", 
                duration, PERFORMANCE_THRESHOLD_MS));
        
        // Трассировка для медленных тестов
        if (duration > PERFORMANCE_THRESHOLD_MS) {
            slowTests++;
            saveTrace(String.format("medlennaya-avtorizaciya-%d.zip", System.currentTimeMillis()));
        }
        
        // Трассировка для 10% запусков
        if (shouldTakeTrace()) {
            saveTrace(String.format("tracerovka-avtorizaciya-%d.zip", System.currentTimeMillis()));
        }
        
        System.out.printf("Тест авторизации: %d мс%n", duration);
    }

    @Test
    @DisplayName("Тест производительности доступа к защищенной области")
    @Tag("performance")
    void testSecureAreaPerformance(TestReporter reporter) {
        long startTime = System.currentTimeMillis();
        
        page.navigate("https://the-internet.herokuapp.com/secure");
        
        // Проверяем заголовок
        String pageContent = page.locator("h2").textContent();
        Assertions.assertTrue(pageContent.contains("Secure Area"), 
            "Ожидалось 'Secure Area', но найдено: " + pageContent);
        
        // Проверяем что мы действительно на защищенной странице (нет редиректа на логин)
        String currentUrl = page.url();
        Assertions.assertTrue(currentUrl.contains("/secure"),
            "Должны быть на защищенной странице, но на: " + currentUrl);
        
        long duration = System.currentTimeMillis() - startTime;
        
        totalTests++;
        totalDuration += duration;
        
        // Добавляем метрику в JUnit отчет
        reporter.publishEntry("Время загрузки", duration + " мс");
        reporter.publishEntry("Лимит", PERFORMANCE_THRESHOLD_MS + " мс");
        reporter.publishEntry("Статус", duration < PERFORMANCE_THRESHOLD_MS ? "Успешно" : "Превышение");
        
        Assertions.assertTrue(duration < PERFORMANCE_THRESHOLD_MS, 
            String.format("Тест не пройден: загрузка страницы заняла %d мс (превышает лимит %d мс)", 
                duration, PERFORMANCE_THRESHOLD_MS));
        
        if (duration > PERFORMANCE_THRESHOLD_MS) {
            slowTests++;
            saveTrace(String.format("medlennaya-zagruzka-%d.zip", System.currentTimeMillis()));
            System.err.printf("Обнаружен медленный тест: %d мс%n", duration);
        } else {
            System.out.printf("Тест пройден: %d мс%n", duration);
        }
        
        if (shouldTakeTrace()) {
            saveTrace(String.format("tracerovka-%d.zip", System.currentTimeMillis()));
        }
    }

    @Test
    @DisplayName("Тест защищенной области с проверками")
    @Tag("performance")
    void testSecureAreaWithChecks(TestReporter reporter) {
        long startTime = System.currentTimeMillis();
        
        page.navigate("https://the-internet.herokuapp.com/secure");
        
        // Проверяем основные элементы страницы
        Assertions.assertTrue(page.isVisible("h2"), "Заголовок h2 не найден");
        Assertions.assertTrue(page.isVisible("a.button"), "Кнопка Logout не найдена");
        
        // Проверяем текст на кнопке
        String buttonText = page.locator("a.button").textContent();
        Assertions.assertTrue(buttonText.contains("Logout"), "Текст кнопки не содержит Logout");
        
        long duration = System.currentTimeMillis() - startTime;
        
        reporter.publishEntry("Время выполнения", duration + " мс");
        reporter.publishEntry("Лимит", PERFORMANCE_THRESHOLD_MS + " мс");
        reporter.publishEntry("Статус", duration < PERFORMANCE_THRESHOLD_MS ? "Успешно" : "Превышение");
        
        Assertions.assertTrue(duration < PERFORMANCE_THRESHOLD_MS,
            String.format("Загрузка страницы с проверками заняла %d мс", duration));
        
        System.out.printf("Тест с проверками: %d мс%n", duration);
    }

    @RepeatedTest(value = 3, name = "Повторение {currentRepetition}/{totalRepetitions}")
    @DisplayName("Повторный доступ к защищенной странице")
    @Tag("performance")
    void testSecureAreaRepeated(TestReporter reporter, TestInfo testInfo) {
        long startTime = System.currentTimeMillis();
        
        page.navigate("https://the-internet.herokuapp.com/secure");
        Assertions.assertTrue(page.locator("h2").textContent().contains("Secure Area"));
        
        long duration = System.currentTimeMillis() - startTime;
        
        reporter.publishEntry(testInfo.getDisplayName(), duration + " мс");
        
        Assertions.assertTrue(duration < PERFORMANCE_THRESHOLD_MS,
            String.format("Повторный тест: %d мс (превышает лимит %d мс)", 
                duration, PERFORMANCE_THRESHOLD_MS));
        
        System.out.printf("Повторение: %d мс%n", duration);
    }

    @Test
    @DisplayName("Тест защищенной области с проверкой cookies")
    @Tag("performance")
    void testSecureAreaWithCookieCheck(TestReporter reporter) {
        long startTime = System.currentTimeMillis();
        
        page.navigate("https://the-internet.herokuapp.com/secure");
        
        String currentUrl = page.url();
        Assertions.assertTrue(currentUrl.contains("/secure"),
            "Должны быть на защищенной странице, но на: " + currentUrl);
        
        // Проверяем наличие cookies
        List<Cookie> currentCookies = context.cookies();
        boolean hasAuthCookie = !currentCookies.isEmpty();
        
        Assertions.assertTrue(hasAuthCookie, "Не найдены cookies");
        
        long duration = System.currentTimeMillis() - startTime;
        
        reporter.publishEntry("Время проверки cookies", duration + " мс");
        reporter.publishEntry("Лимит", PERFORMANCE_THRESHOLD_MS + " мс");
        reporter.publishEntry("Статус", duration < PERFORMANCE_THRESHOLD_MS ? "Успешно" : "Превышение");
        
        Assertions.assertTrue(duration < PERFORMANCE_THRESHOLD_MS,
            String.format("Проверка cookies заняла %d мс", duration));
    }

    @AfterEach
    void tearDown() {
        if (page != null) page.close();
        if (context != null) context.close();
    }

    @AfterAll
    static void tearDownClass() {
        if (totalTests > 0) {
            double avgDuration = (double) totalDuration / totalTests;
            System.out.println("\n========== ИТОГИ ПРОИЗВОДИТЕЛЬНОСТИ ==========");
            System.out.printf("Всего тестов: %d%n", totalTests);
            System.out.printf("Среднее время: %.2f мс%n", avgDuration);
            System.out.printf("Медленных тестов (>%d мс): %d%n", PERFORMANCE_THRESHOLD_MS, slowTests);
            System.out.printf("Процент успешных: %.1f%%%n", 
                100.0 * (totalTests - slowTests) / totalTests);
            System.out.println("==============================================\n");
        }
        
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    private static List<Cookie> performLogin(Page page) {
        page.navigate("https://the-internet.herokuapp.com/login");
        page.fill("#username", "tomsmith");
        page.fill("#password", "SuperSecretPassword!");
        page.click("button[type='submit']");
        
        return page.context().cookies();
    }

    private boolean shouldTakeTrace() {
        return random.nextDouble() < TRACE_PROBABILITY;
    }

    private void saveTrace(String fileName) {
        try {
            context.tracing().start(new Tracing.StartOptions()
                .setScreenshots(true)
                .setSnapshots(true)
                .setSources(false));
            
            context.tracing().stop(new Tracing.StopOptions()
                .setPath(Paths.get("traces/" + fileName)));
            
            System.out.printf("Сохранена трассировка: %s%n", fileName);
        } catch (Exception e) {
            System.err.println("Ошибка сохранения трассировки: " + e.getMessage());
        }
    }
}