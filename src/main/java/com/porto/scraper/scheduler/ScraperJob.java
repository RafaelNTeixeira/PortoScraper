package com.porto.scraper.scheduler;

import com.porto.scraper.config.SearchTarget;
import com.porto.scraper.config.UrlBuilder;
import com.porto.scraper.database.ListingRepository;
import com.porto.scraper.model.Listing;
import com.porto.scraper.notification.DiscordNotifier;
import com.porto.scraper.scraper.BrowserPool;
import com.porto.scraper.scraper.IdealistaScraper;
import com.porto.scraper.scraper.ImovirtualScraper;
import com.porto.scraper.scraper.Scraper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ScraperJob implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ScraperJob.class);

    private final List<SearchTarget> targets;
    private final ListingRepository  db;
    private final DiscordNotifier    discord;

    // Whether any Idealista targets are configured - only spin up
    // Chrome if we actually need it
    private final boolean hasIdealistaTargets;

    public ScraperJob(List<SearchTarget> targets, ListingRepository db, DiscordNotifier discord) {
        this.targets = targets;
        this.db      = db;
        this.discord = discord;
        this.hasIdealistaTargets = targets.stream()
                .anyMatch(t -> t.getSource() == UrlBuilder.Source.IDEALISTA);
    }

    @Override
    public void run() {
        try {
            executeRun();
        } catch (Exception e) {
            log.error("Unhandled error in scrape run - will retry next cycle: {}", e.getMessage(), e);
        }
    }

    private void executeRun() throws Exception {
        log.info("------------------------------------------");
        log.info("  Starting scrape run");
        log.info("------------------------------------------");

        int newCount = 0, totalCount = 0;

        // Open Chrome once for the whole run, only if Idealista targets exist.
        // try-with-resources closes (quits) the browser when the run finishes.
        try (BrowserPool browser = hasIdealistaTargets ? openBrowser() : new BrowserPool()) {

            for (SearchTarget target : targets) {
                log.info("┌- {}", target.getLabel());

                // Pick the right scraper
                Scraper scraper = target.getSource() == UrlBuilder.Source.IDEALISTA
                        ? new IdealistaScraper(target.getUrl(), browser.getDriver())
                        : new ImovirtualScraper(target.getUrl());

                List<Listing> listings;
                try {
                    listings = scraper.scrape();
                } catch (Exception e) {
                    log.error("└- Scraper threw for '{}': {}", target.getLabel(), e.getMessage());
                    continue;
                }

                if (listings.isEmpty()) {
                    log.warn("└- No listings returned.");
                    continue;
                }

                log.info("└- {} scraped - checking DB…", listings.size());
                totalCount += listings.size();

                for (Listing listing : listings) {
                    if (!db.isNew(listing.getId())) continue;

                    newCount++;
                    System.out.println("  🆕 " + target.getLabel());
                    System.out.println("  " + listing);
                    System.out.println();

                    db.markSeen(listing, target.getSource().name().toLowerCase());

                    if (discord != null) discord.notify(listing, target.getLabel());
                }
            }
        }

        log.info("------------------------------------------");
        log.info(newCount > 0 ? "  ✅ {} new listing(s) found!" : "  ✔  No new listings.", newCount);
        log.info("  {} scraped | {} total in DB", totalCount, db.totalSeen());
        log.info("------------------------------------------");
    }

    private BrowserPool openBrowser() {
        BrowserPool pool = new BrowserPool();
        pool.open();
        return pool;
    }
}