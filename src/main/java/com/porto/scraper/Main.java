package com.porto.scraper;

import com.porto.scraper.config.SearchTarget;
import com.porto.scraper.config.UrlBuilder;
import com.porto.scraper.config.UrlBuilder.Zone;
import com.porto.scraper.database.ListingRepository;
import com.porto.scraper.model.Listing;
import com.porto.scraper.scraper.ImovirtualScraper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 *                   Porto Real Estate Scraper
 * ╚══════════════════════════════════════════════════════════════╝
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    // ══════════════════════════════════════════════════════════════════════
    //  YOUR SEARCH TARGETS - edit freely
    // ══════════════════════════════════════════════════════════════════════

    private static final List<SearchTarget> SEARCH_TARGETS = List.of(

            // -- BUYING --------------------------------------------------------
            new UrlBuilder().buy().houses().inZone(Zone.PORTO).maxPrice(350_000).build(),
            new UrlBuilder().buy().apartments().inZone(Zone.MATOSINHOS).maxPrice(280_000).build(),
            new UrlBuilder().buy().houses().inZone(Zone.GAIA).maxPrice(320_000).build(),
            new UrlBuilder().buy().houses().inZone(Zone.MAIA).maxPrice(280_000).build(),

            // -- RENTING -------------------------------------------------------
            new UrlBuilder().rent().apartments().inZone(Zone.PORTO).minPrice(600).maxPrice(1_200).build(),
            new UrlBuilder().rent().apartments().inZone(Zone.MATOSINHOS).maxPrice(1_100).build()

            // new UrlBuilder().buy().houses().inZone(Zone.BRAGA).maxPrice(200_000).build(),
    );

    // ══════════════════════════════════════════════════════════════════════

    public static void main(String[] args) {

        log.info("═══════════════════════════════════════════");
        log.info("         Porto Real Estate Scraper         ");
        log.info("═══════════════════════════════════════════");

        // try-with-resources: DB connection closes cleanly even on crash
        try (ListingRepository db = new ListingRepository()) {

            log.info("Database contains {} previously seen listing(s)", db.totalSeen());

            int newCount   = 0;
            int totalCount = 0;

            for (SearchTarget target : SEARCH_TARGETS) {
                System.out.println();
                log.info("┌- Scraping: {}", target.getLabel());

                ImovirtualScraper scraper = new ImovirtualScraper(target.getUrl());
                List<Listing> listings = scraper.scrape();

                if (listings.isEmpty()) {
                    log.warn("└- No listings returned.");
                    continue;
                }

                log.info("└- {} scraped - checking against database…", listings.size());
                totalCount += listings.size();

                for (Listing listing : listings) {

                    if (!db.isNew(listing.getId())) {
                        // Already seen - skip silently
                        // (change to log.debug if you want to see skips)
                        continue;
                    }

                    // -- NEW LISTING ---------------------------------------
                    newCount++;

                    // 1. Show it
                    System.out.println("  🆕 [" + target.getLabel() + "]");
                    System.out.println("  " + listing);
                    System.out.println();

                    // 2. Save it <- after printing, so a crash doesn't hide it
                    db.markSeen(listing, "imovirtual");

                    // Phase 3: Discord notification goes here
                }
            }

            // -- Summary ---------------------------------------------------
            System.out.println();
            log.info("═══════════════════════════════════════════");
            log.info("  {} scraped across {} target(s)", totalCount, SEARCH_TARGETS.size());
            log.info("  {} new  |  {} already seen",
                    newCount, totalCount - newCount);
            log.info("  {} total in database", db.totalSeen());
            log.info("═══════════════════════════════════════════");

            if (newCount == 0) {
                log.info("  No new listings this run - nothing to report.");
            }

        } catch (SQLException e) {
            log.error("Database error: {}", e.getMessage(), e);
        }
    }
}