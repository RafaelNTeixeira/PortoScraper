package com.porto.scraper.scheduler;

import com.porto.scraper.config.SearchTarget;
import com.porto.scraper.database.ListingRepository;
import com.porto.scraper.model.Listing;
import com.porto.scraper.notification.DiscordNotifier;
import com.porto.scraper.scraper.ImovirtualScraper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * One full scrape cycle: iterate all search targets, check against the DB,
 * notify on new listings. Implements Runnable so the scheduler can call it
 * repeatedly without any extra wiring.
 *
 * Errors inside run() are caught and logged, they never propagate up.
 */
public class ScraperJob implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ScraperJob.class);

    private final List<SearchTarget> targets;
    private final ListingRepository  db;
    private final DiscordNotifier    discord; // may be null if notifications are disabled

    public ScraperJob(List<SearchTarget> targets,
                      ListingRepository db,
                      DiscordNotifier discord) {
        this.targets = targets;
        this.db      = db;
        this.discord = discord;
    }

    @Override
    public void run() {
        try {
            executeRun();
        } catch (Exception e) {
            // Catch-all: a crash here must NOT kill the scheduler
            log.error("Unhandled error in scrape run - will retry on next cycle: {}", e.getMessage(), e);
        }
    }

    // -- Core logic --------------------------------------------------------

    private void executeRun() throws Exception {
        log.info("------------------------------------------");
        log.info("  Starting scrape run");
        log.info("------------------------------------------");

        int newCount   = 0;
        int totalCount = 0;

        for (SearchTarget target : targets) {
            log.info("┌- Scraping: {}", target.getLabel());

            List<Listing> listings;
            try {
                listings = new ImovirtualScraper(target.getUrl()).scrape();
            } catch (Exception e) {
                // One failed target should not abort the whole run
                log.error("└- Scraper threw for target '{}': {}", target.getLabel(), e.getMessage());
                continue;
            }

            if (listings.isEmpty()) {
                log.warn("└- No listings returned.");
                continue;
            }

            log.info("└- {} scraped - checking against database…", listings.size());
            totalCount += listings.size();

            for (Listing listing : listings) {

                if (!db.isNew(listing.getId())) {
                    continue;
                }

                // -- NEW LISTING -------------------------------------------
                newCount++;

                System.out.println("  🆕 [" + target.getLabel() + "]");
                System.out.println("  " + listing);
                System.out.println();

                db.markSeen(listing, "imovirtual");

                if (discord != null) {
                    discord.notify(listing, target.getLabel());
                }
            }
        }

        // -- Run summary ---------------------------------------------------
        log.info("------------------------------------------");
        if (newCount > 0) {
            log.info("  ✅ {} new listing(s) found this run!", newCount);
        } else {
            log.info("  ✔  No new listings this run.");
        }
        log.info("  {} scraped across {} target(s) | {} total in DB",
                totalCount, targets.size(), db.totalSeen());
        log.info("------------------------------------------");
    }
}