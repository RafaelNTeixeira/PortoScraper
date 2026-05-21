package com.porto.scraper.scraper;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages a single headless Chrome instance shared across all Idealista targets in one scrape run.
 *
 * -----------------------------------------------------------------
 *  REQUIREMENTS
 * -----------------------------------------------------------------
 *  - Google Chrome must be installed on the machine.
 *  - Selenium Manager (bundled with Selenium 4.6+) downloads the
 *    matching chromedriver automatically, nothing else to install.
 *
 * -----------------------------------------------------------------
 *  LIFECYCLE
 * -----------------------------------------------------------------
 *  ScraperJob calls open() at the start of each run and close() at
 *  the end. The same driver instance is reused for every Idealista
 *  target in that run, saving the ~2s Chrome startup cost per target.
 *
 *  If the browser crashes mid-run, the next call to getDriver() restarts it automatically.
 */
public class BrowserPool implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(BrowserPool.class);

    private WebDriver driver;

    /**
     * Starts headless Chrome. Call once before the scrape run begins.
     */
    public void open() {
        if (driver != null) return; // already open
        log.info("  [Browser] Starting headless Chrome…");

        ChromeOptions opts = new ChromeOptions();
        opts.addArguments("--headless=new");          // new headless mode (Chrome 112+)
        opts.addArguments("--no-sandbox");
        opts.addArguments("--disable-dev-shm-usage"); // prevent crashes in low-memory envs
        opts.addArguments("--disable-gpu");
        opts.addArguments("--window-size=1920,1080"); // realistic viewport
        opts.addArguments("--disable-blink-features=AutomationControlled"); // hide bot signals
        opts.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Safari/537.36");

        // Suppress Selenium's own INFO logs from cluttering the console
        System.setProperty("webdriver.chrome.silentOutput", "true");

        driver = new ChromeDriver(opts);
        log.info("  [Browser] Chrome ready.");
    }

    /**
     * Returns the shared WebDriver. Restarts Chrome if it has crashed.
     */
    public WebDriver getDriver() {
        try {
            if (driver != null) {
                driver.getTitle(); // lightweight liveness check
                return driver;
            }
        } catch (Exception e) {
            log.warn("  [Browser] Chrome appears to have crashed — restarting.");
            silentQuit();
        }
        open();
        return driver;
    }

    /**
     * Quits the browser. Called at the end of each scrape run.
     */
    @Override
    public void close() {
        silentQuit();
    }

    private void silentQuit() {
        if (driver != null) {
            try { driver.quit(); } catch (Exception ignored) {}
            driver = null;
            log.info("  [Browser] Chrome closed.");
        }
    }
}