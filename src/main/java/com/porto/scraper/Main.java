package com.porto.scraper;

import com.porto.scraper.config.SearchTarget;
import com.porto.scraper.config.UrlBuilder;
import com.porto.scraper.config.UrlBuilder.Zone;
import com.porto.scraper.model.Listing;
import com.porto.scraper.scraper.ImovirtualScraper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 *                    Porto Real Estate Scraper
 * ╚══════════════════════════════════════════════════════════════╝
 *
 *  ✔ Scrapes multiple search targets in one run
 *  ✔ Supports BUY and RENT
 *  ✔ Supports Apartments, Houses, and Land
 *  ✔ Supports Porto district zones + Braga
 *  ✔ Supports price / rooms / area filters per target
 *
 * -----------------------------------------------------------------
 *  HOW TO CONFIGURE YOUR SEARCHES
 * -----------------------------------------------------------------
 *  Edit the SEARCH_TARGETS list below.
 *  Each entry is built with the fluent UrlBuilder:
 *
 *    new UrlBuilder()
 *        .buy()           or  .rent()
 *        .houses()        or  .apartments()  or  .land()
 *        .inZone(Zone.X)  - see full zone list in UrlBuilder.java
 *        .maxPrice(N)     - optional
 *        .minRooms(N)     - optional
 *        .build()
 *
 * -----------------------------------------------------------------
 *  BUILD & RUN
 * -----------------------------------------------------------------
 *    mvn clean package
 *    java -jar target/real-estate-scraper-1.0-SNAPSHOT.jar
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    // ══════════════════════════════════════════════════════════════════════
    //  YOUR SEARCH TARGETS - edit this list freely
    // ══════════════════════════════════════════════════════════════════════

    private static final List<SearchTarget> SEARCH_TARGETS = List.of(

            // -- BUYING --------------------------------------------------------

            // Buy a house in Porto city, up to €350k
            new UrlBuilder()
                    .buy().houses()
                    .inZone(Zone.PORTO)
                    .maxPrice(350_000)
                    .build(),

            // Buy an apartment in Matosinhos, up to €280k
            new UrlBuilder()
                    .buy().apartments()
                    .inZone(Zone.MATOSINHOS)
                    .maxPrice(280_000)
                    .build(),

            // Buy a house in Vila Nova de Gaia, up to €320k
            new UrlBuilder()
                    .buy().houses()
                    .inZone(Zone.GAIA)
                    .maxPrice(320_000)
                    .build(),

            // Buy a house in Maia, up to €280k - more space for less money
            new UrlBuilder()
                    .buy().houses()
                    .inZone(Zone.MAIA)
                    .maxPrice(280_000)
                    .build(),

            // -- RENTING -------------------------------------------------------

            // Rent an apartment in Porto, €600-€1200/month
            new UrlBuilder()
                    .rent().apartments()
                    .inZone(Zone.PORTO)
                    .minPrice(600)
                    .maxPrice(1_200)
                    .build(),

            // Rent an apartment in Matosinhos, up to €1100/month
            new UrlBuilder()
                    .rent().apartments()
                    .inZone(Zone.MATOSINHOS)
                    .maxPrice(1_100)
                    .build()

            // -- ADD MORE TARGETS HERE -----------------------------------------
            //
            // new UrlBuilder().buy().houses().inZone(Zone.BRAGA).maxPrice(200_000).build(),
            // new UrlBuilder().rent().houses().inZone(Zone.GONDOMAR).maxPrice(900).build(),
            // new UrlBuilder().buy().land().inZone(Zone.VALONGO).maxPrice(80_000).build(),
    );

    // ══════════════════════════════════════════════════════════════════════

    public static void main(String[] args) {

        log.info("═══════════════════════════════════════════");
        log.info("  Porto Real Estate Scraper                ");
        log.info("  {} search target(s) configured           ", SEARCH_TARGETS.size());
        log.info("═══════════════════════════════════════════");

        int totalFound = 0;

        for (SearchTarget target : SEARCH_TARGETS) {
            System.out.println();
            log.info("┌- Scraping: {}", target.getLabel());

            ImovirtualScraper scraper = new ImovirtualScraper(target.getUrl());
            List<Listing> listings = scraper.scrape();

            if (listings.isEmpty()) {
                log.warn("└- No listings found for this target.");
                continue;
            }

            log.info("└- {} listing(s) found", listings.size());
            totalFound += listings.size();

            System.out.println();
            for (Listing listing : listings) {
                // Phase 2: we'll check listing.getId() against the DB here
                // Phase 3: we'll fire a Discord notification here
                System.out.println("[" + target.getLabel() + "]");
                System.out.println(listing);
                System.out.println();
            }
        }

        log.info("═══════════════════════════════════════════");
        log.info("  Done. {} total listing(s) across {} target(s)", totalFound, SEARCH_TARGETS.size());
        log.info("═══════════════════════════════════════════");
    }
}