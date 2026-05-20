package com.porto.scraper;

import com.porto.scraper.config.SearchTarget;
import com.porto.scraper.config.UrlBuilder;
import com.porto.scraper.config.UrlBuilder.Zone;
import com.porto.scraper.database.ListingRepository;
import com.porto.scraper.model.Listing;
import com.porto.scraper.notification.DiscordNotifier;
import com.porto.scraper.scraper.ImovirtualScraper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 *                    Porto Real Estate Scraper
 * ╚══════════════════════════════════════════════════════════════╝
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    // ══════════════════════════════════════════════════════════════════════
    //  DISCORD - paste your webhook URL here
    // ══════════════════════════════════════════════════════════════════════

    private static final String DISCORD_WEBHOOK_URL = null; // e.g. "https://discord.com/api/webhooks/1234567890/xxxx"

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

        // Set up the Discord notifier (null = notifications disabled)
        DiscordNotifier discord = null;
        if (DISCORD_WEBHOOK_URL != null) {
            discord = new DiscordNotifier(DISCORD_WEBHOOK_URL);
            log.info("Discord notifications: ENABLED");
        } else {
            log.info("Discord notifications: DISABLED (set DISCORD_WEBHOOK_URL to enable)");
        }

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
                        continue; // already seen
                    }

                    // -- NEW LISTING ---------------------------------------
                    newCount++;

                    // 1. Print to console
                    System.out.println("  🆕 [" + target.getLabel() + "]");
                    System.out.println("  " + listing);
                    System.out.println();

                    // 2. Save to DB (before notifying, so a webhook failure
                    //    doesn't cause duplicate notifications on retry)
                    db.markSeen(listing, "imovirtual");

                    // 3. Send Discord notification
                    if (discord != null) {
                        discord.notify(listing, target.getLabel());
                    }
                }
            }

            // -- Summary ---------------------------------------------------
            System.out.println();
            log.info("═══════════════════════════════════════════");
            log.info("  {} scraped across {} target(s)", totalCount, SEARCH_TARGETS.size());
            log.info("  {} new  |  {} already seen", newCount, totalCount - newCount);
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