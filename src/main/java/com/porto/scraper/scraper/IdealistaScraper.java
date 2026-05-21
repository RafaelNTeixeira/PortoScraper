package com.porto.scraper.scraper;

import com.porto.scraper.model.Listing;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.By;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Scrapes Idealista Portugal using a shared Selenium WebDriver.
 *
 * Selenium drives a real headless Chrome browser, which executes
 * Idealista's Cloudflare JS challenge and renders the full page -
 * the same as a human visiting the site. This is the only reliable
 * way to bypass Cloudflare's bot protection without an API key.
 *
 * The WebDriver is created ONCE per scrape run in ScraperJob and
 * shared across all Idealista targets, then quit when the run ends.
 * This avoids the ~2s Chrome startup cost per target.
 */
public class IdealistaScraper implements Scraper {

    private static final Logger log = LoggerFactory.getLogger(IdealistaScraper.class);

    private static final String IDEALISTA_BASE = "https://www.idealista.pt";

    // How long to wait for listing cards to appear after navigation.
    // Cloudflare's JS challenge can take a few seconds to clear.
    private static final Duration PAGE_LOAD_WAIT = Duration.ofSeconds(15);

    // CSS selectors - update if Idealista redesigns their cards
    private static final String CARD_SELECTOR   = "article.item";
    private static final String LINK_SELECTOR   = "a.item-link";
    private static final String PRICE_SELECTOR  = "span.item-price";
    private static final String DETAIL_SELECTOR = "span.item-detail";

    private final String    searchUrl;
    private final WebDriver driver;

    /**
     * @param searchUrl  The Idealista search URL to scrape
     * @param driver     A shared Selenium WebDriver (created by ScraperJob)
     */
    public IdealistaScraper(String searchUrl, WebDriver driver) {
        this.searchUrl = searchUrl;
        this.driver    = driver;
    }

    @Override
    public List<Listing> scrape() {
        List<Listing> results = new ArrayList<>();
        try {
            // Strip the #fragment - it's browser-side only
            String url = searchUrl.contains("#") ? searchUrl.substring(0, searchUrl.indexOf('#')) : searchUrl;
            log.info("  [Idealista] Navigating to: {}", url);

            driver.get(url);

            // Wait until at least one listing card appears, or timeout.
            // This covers both the Cloudflare challenge delay and normal page load.
            new WebDriverWait(driver, PAGE_LOAD_WAIT)
                    .until(d -> !d.findElements(By.cssSelector(CARD_SELECTOR)).isEmpty()
                            || d.getTitle().toLowerCase().contains("idealista"));

            // Parse the fully-rendered HTML with Jsoup (faster than Selenium selectors)
            Document doc = Jsoup.parse(driver.getPageSource());

            if (doc.title().toLowerCase().contains("just a moment")) {
                log.warn("  [Idealista] Cloudflare JS challenge still showing. Try a longer PAGE_LOAD_WAIT.");
                return results;
            }

            Elements cards = doc.select(CARD_SELECTOR);
            log.info("  [Idealista] Found {} listing card(s)", cards.size());

            if (cards.isEmpty()) {
                log.warn("  [Idealista] No cards found. Page title: {}", doc.title());
            }

            for (Element card : cards) {
                Listing listing = parseCard(card);
                if (listing != null) results.add(listing);
            }

        } catch (org.openqa.selenium.TimeoutException e) {
            // Cards never appeared - likely still blocked or page is empty
            log.warn("  [Idealista] Timed out waiting for listing cards.");
            log.warn("  [Idealista] Page title at timeout: {}", driver.getTitle());
        } catch (Exception e) {
            log.error("  [Idealista] Error: {}", e.getMessage(), e);
        }
        return results;
    }

    // -- Card parsing ------------------------------------------------------

    private Listing parseCard(Element card) {
        try {
            // ID from data-adid, or extracted from the link href
            String id = card.attr("data-adid");
            if (id == null || id.isBlank()) {
                Element linkEl = card.selectFirst(LINK_SELECTOR);
                if (linkEl == null) return null;
                id = extractId(linkEl.attr("href"));
            }
            if (id == null || id.isBlank()) return null;

            // URL - Idealista links look like /imovel/34799836/
            Element linkEl = card.selectFirst(LINK_SELECTOR);
            if (linkEl == null) return null;
            String href = linkEl.attr("href");
            String url  = href.startsWith("http") ? href : IDEALISTA_BASE + href;

            // Title
            String title = linkEl.attr("title");
            if (title == null || title.isBlank()) title = linkEl.text();
            if (title == null || title.isBlank()) title = "No title";

            // Price
            Element priceEl = card.selectFirst(PRICE_SELECTOR);
            String price = priceEl != null ? priceEl.text().trim() : "Price N/A";

            // Location - first item-detail span
            String location = "Location N/A";
            Elements details = card.select(DETAIL_SELECTOR);
            if (!details.isEmpty()) location = details.get(0).text().trim();

            return new Listing(id, title, price, location, url);

        } catch (Exception e) {
            log.warn("  [Idealista] Failed to parse card: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extracts the listing ID from an Idealista href.
     * /imovel/34799836/  →  "34799836"
     */
    private String extractId(String href) {
        if (href == null || href.isBlank()) return null;
        String clean = href.endsWith("/") ? href.substring(0, href.length() - 1) : href;
        int lastSlash = clean.lastIndexOf('/');
        if (lastSlash == -1) return null;
        String segment = clean.substring(lastSlash + 1);
        String digits  = segment.replaceAll(".*?(\\d+)$", "$1");
        return digits.isBlank() ? segment : digits;
    }
}