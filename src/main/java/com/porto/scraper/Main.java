package com.porto.scraper;

import com.porto.scraper.config.SearchTarget;
import com.porto.scraper.config.UrlBuilder;
import com.porto.scraper.config.UrlBuilder.Zone;
import com.porto.scraper.database.ListingRepository;
import com.porto.scraper.notification.DiscordNotifier;
import com.porto.scraper.scheduler.ScraperJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 *                   Porto Real Estate Scraper
 * ╚══════════════════════════════════════════════════════════════╝
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    // ══════════════════════════════════════════════════════════════════════
    //  SCHEDULER CONFIG
    // ══════════════════════════════════════════════════════════════════════

    /** How often to scrape, in minutes. */
    private static final int POLL_INTERVAL_MINUTES = 5;

    // ══════════════════════════════════════════════════════════════════════
    //  DISCORD - paste your webhook URL here or leave null to disable
    // ══════════════════════════════════════════════════════════════════════

    private static final String DISCORD_WEBHOOK_URL =
            null; // e.g. "https://discord.com/api/webhooks/1234567890/xxxx"

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
        log.info("         Polling every {} minutes          ", POLL_INTERVAL_MINUTES);
        log.info("═══════════════════════════════════════════");

        // -- Discord -------------------------------------------------------
        DiscordNotifier discord = null;
        if (DISCORD_WEBHOOK_URL != null) {
            discord = new DiscordNotifier(DISCORD_WEBHOOK_URL);
            log.info("Discord notifications: ENABLED");
        } else {
            log.info("Discord notifications: DISABLED");
        }

        // -- Database ------------------------------------------------------
        // Opened once and shared across all scheduled runs.
        // Closed cleanly in the shutdown hook below.
        final ListingRepository db;
        try {
            db = new ListingRepository();
        } catch (SQLException e) {
            log.error("Failed to open database: {}", e.getMessage());
            return;
        }

        // -- Job -----------------------------------------------------------
        final DiscordNotifier finalDiscord = discord;
        ScraperJob job = new ScraperJob(SEARCH_TARGETS, db, finalDiscord);

        // -- Scheduler -----------------------------------------------------
        // Single-threaded: runs are sequential, never overlap.
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "scraper-thread");
            t.setDaemon(false); // keep JVM alive while this thread runs
            return t;
        });

        // Fire immediately, then every POLL_INTERVAL_MINUTES minutes
        scheduler.scheduleAtFixedRate(
                () -> {
                    job.run();
                    logNextRun();
                },
                0,                       // initial delay: 0 = run right now
                POLL_INTERVAL_MINUTES,
                TimeUnit.MINUTES
        );

        log.info("Scheduler started. First run beginning now…");
        log.info("Press Ctrl+C to stop.");

        // -- Graceful shutdown on Ctrl+C -----------------------------------
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received - stopping scheduler…");
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
                db.close();
                log.info("Shutdown complete.");
            } catch (Exception e) {
                log.error("Error during shutdown: {}", e.getMessage());
            }
        }, "shutdown-hook"));
    }

    // -- Helpers -----------------------------------------------------------

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static void logNextRun() {
        LocalTime next = LocalTime.now().plusMinutes(POLL_INTERVAL_MINUTES);
        log.info("Next run scheduled at {}", next.format(TIME_FMT));
    }
}